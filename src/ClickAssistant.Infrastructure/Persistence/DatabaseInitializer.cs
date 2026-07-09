using Microsoft.Data.Sqlite;
using SQLitePCL;

namespace ClickAssistant.Infrastructure.Persistence;

/// <summary>
/// 数据库初始化器，负责创建首版 MVP 所需表结构。
/// </summary>
public sealed class DatabaseInitializer
{
    private readonly SqliteConnectionFactory connectionFactory;

    public DatabaseInitializer(SqliteConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }

    /// <summary>
    /// 初始化 SQLite 原生库和数据库表。
    /// </summary>
    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        Batteries_V2.Init();

        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        foreach (var sql in CreateSchemaSql())
        {
            await using var command = connection.CreateCommand();
            command.CommandText = sql;
            await command.ExecuteNonQueryAsync(cancellationToken);
        }

        await EnsureTaskStepColumnsAsync(connection, cancellationToken);
    }

    /// <summary>
    /// 通过分段 SQL 保持表结构清晰，后续可替换为迁移脚本。
    /// </summary>
    private static IReadOnlyList<string> CreateSchemaSql()
    {
        return
        [
            """
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
            """,
            """
            CREATE TABLE IF NOT EXISTS task_steps (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                name TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                action_type TEXT NOT NULL DEFAULT 'MouseClick',
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                click_type TEXT NOT NULL,
                key_name TEXT NOT NULL DEFAULT 'A',
                key_press_count INTEGER NOT NULL DEFAULT 1,
                key_interval_ms INTEGER NOT NULL DEFAULT 100,
                before_delay_ms INTEGER NOT NULL,
                after_delay_ms INTEGER NOT NULL,
                step_order INTEGER NOT NULL,
                FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS execution_logs (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                status TEXT NOT NULL,
                started_at TEXT NOT NULL,
                ended_at TEXT NULL,
                message TEXT NOT NULL
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version TEXT PRIMARY KEY,
                applied_at TEXT NOT NULL
            );
            """
        ];
    }

    /// <summary>
    /// 为已存在的旧数据库补充键盘输入相关列，保证升级后仍可读取旧任务。
    /// </summary>
    private static async Task EnsureTaskStepColumnsAsync(
        SqliteConnection connection,
        CancellationToken cancellationToken)
    {
        var existingColumns = await GetTaskStepColumnsAsync(connection, cancellationToken);
        var requiredColumns = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["action_type"] = "ALTER TABLE task_steps ADD COLUMN action_type TEXT NOT NULL DEFAULT 'MouseClick';",
            ["key_name"] = "ALTER TABLE task_steps ADD COLUMN key_name TEXT NOT NULL DEFAULT 'A';",
            ["key_press_count"] = "ALTER TABLE task_steps ADD COLUMN key_press_count INTEGER NOT NULL DEFAULT 1;",
            ["key_interval_ms"] = "ALTER TABLE task_steps ADD COLUMN key_interval_ms INTEGER NOT NULL DEFAULT 100;"
        };

        foreach (var (columnName, alterSql) in requiredColumns)
        {
            if (existingColumns.Contains(columnName))
            {
                continue;
            }

            await using var command = connection.CreateCommand();
            command.CommandText = alterSql;
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
    }

    /// <summary>
    /// 读取 task_steps 表当前列名，用于判断是否需要执行轻量迁移。
    /// </summary>
    private static async Task<HashSet<string>> GetTaskStepColumnsAsync(
        SqliteConnection connection,
        CancellationToken cancellationToken)
    {
        var columns = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        await using var command = connection.CreateCommand();
        command.CommandText = "PRAGMA table_info(task_steps);";

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);

        while (await reader.ReadAsync(cancellationToken))
        {
            columns.Add(reader.GetString(1));
        }

        return columns;
    }
}
