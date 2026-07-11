using ClickAssistant.Infrastructure.Persistence;
using Microsoft.Data.Sqlite;

namespace ClickAssistant.Tests.Infrastructure;

public sealed class DatabaseMigratorTests
{
    [Fact]
    public async Task InitializeAsync_WhenNewDatabase_ShouldCreateAllTablesAndApplyAllMigrations()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var migrator = new DatabaseMigrator(connectionFactory);
            await migrator.InitializeAsync();

            var currentVersion = await migrator.GetCurrentVersionAsync();
            Assert.NotNull(currentVersion);

            // 验证所有业务表均已创建
            var tableNames = await GetTableNamesAsync(connectionFactory);
            Assert.Contains("tasks", tableNames);
            Assert.Contains("task_steps", tableNames);
            Assert.Contains("execution_logs", tableNames);
            Assert.Contains("app_settings", tableNames);
            Assert.Contains("schema_migrations", tableNames);

            // 验证 v1.1.0 新增列已应用
            var stepColumns = await GetColumnNamesAsync(connectionFactory, "task_steps");
            Assert.Contains("key_name", stepColumns);
            Assert.Contains("text_content", stepColumns);

            // 验证 v1.2.0 新增列已应用
            Assert.Contains("click_interval_ms", stepColumns);
            Assert.Contains("end_x", stepColumns);
            Assert.Contains("swipe_duration_ms", stepColumns);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task InitializeAsync_WhenCalledTwice_ShouldBeIdempotent()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);

            // 首次初始化
            var migrator1 = new DatabaseMigrator(connectionFactory);
            await migrator1.InitializeAsync();
            var version1 = await migrator1.GetCurrentVersionAsync();

            // 二次初始化（幂等）
            var migrator2 = new DatabaseMigrator(connectionFactory);
            await migrator2.InitializeAsync();
            var version2 = await migrator2.GetCurrentVersionAsync();

            Assert.Equal(version1, version2);
            Assert.NotNull(version2);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task InitializeAsync_WhenOldDatabaseMissingColumns_ShouldAutoFillWithDefaults()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);

            // 先创建旧版 schema（仅建表，不应用后续迁移）
            await CreateLegacySchemaAsync(connectionFactory);

            // 再运行迁移器补齐列
            var migrator = new DatabaseMigrator(connectionFactory);
            await migrator.InitializeAsync();

            // 验证 task_steps 表已补全所有列
            var stepColumns = await GetColumnNamesAsync(connectionFactory, "task_steps");
            Assert.Contains("click_interval_ms", stepColumns);
            Assert.Contains("press_duration_ms", stepColumns);
            Assert.Contains("auto_focus_before_input", stepColumns);
            Assert.Contains("end_x", stepColumns);
            Assert.Contains("end_y", stepColumns);
            Assert.Contains("swipe_duration_ms", stepColumns);

            // 验证迁移版本已记录
            var currentVersion = await migrator.GetCurrentVersionAsync();
            Assert.NotNull(currentVersion);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    /// <summary>
    /// 创建仅包含初始 schema（不含 v1.1.0/v1.2.0 列）的旧数据库。
    /// </summary>
    private static async Task CreateLegacySchemaAsync(SqliteConnectionFactory connectionFactory)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync();

        await using var command = connection.CreateCommand();
        command.CommandText = """
            CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                repeat_count INTEGER NOT NULL,
                start_delay_ms INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS task_steps (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                name TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                action_type TEXT NOT NULL DEFAULT 'MouseClick',
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                click_type TEXT NOT NULL,
                mouse_click_count INTEGER NOT NULL DEFAULT 1,
                key_name TEXT NOT NULL DEFAULT '',
                key_press_count INTEGER NOT NULL DEFAULT 1,
                key_interval_ms INTEGER NOT NULL DEFAULT 100,
                shortcut_keys TEXT NOT NULL DEFAULT 'Ctrl+C',
                text_content TEXT NOT NULL DEFAULT '',
                before_delay_ms INTEGER NOT NULL,
                step_order INTEGER NOT NULL,
                FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS execution_logs (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                status TEXT NOT NULL,
                started_at TEXT NOT NULL,
                ended_at TEXT NULL,
                message TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version TEXT PRIMARY KEY,
                applied_at TEXT NOT NULL
            );
            INSERT INTO schema_migrations (version, applied_at)
            VALUES ('1.0.0', '2024-01-01T00:00:00.0000000Z');
            """;
        await command.ExecuteNonQueryAsync();
    }

    private static async Task<List<string>> GetTableNamesAsync(SqliteConnectionFactory connectionFactory)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync();

        var tables = new List<string>();
        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;";

        await using var reader = await command.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            tables.Add(reader.GetString(0));
        }

        return tables;
    }

    private static async Task<List<string>> GetColumnNamesAsync(
        SqliteConnectionFactory connectionFactory,
        string tableName)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync();

        var columns = new List<string>();
        await using var command = connection.CreateCommand();
        command.CommandText = $"PRAGMA table_info({tableName});";

        await using var reader = await command.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            columns.Add(reader.GetString(1));
        }

        return columns;
    }

    private static string CreateTempDatabasePath()
    {
        return Path.Combine(
            Path.GetTempPath(),
            "ClickAssistant.Tests",
            $"{Guid.NewGuid():N}.db");
    }

    private static void DeleteDatabaseFile(string databasePath)
    {
        foreach (var path in new[] { databasePath, $"{databasePath}-wal", $"{databasePath}-shm" })
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
    }
}
