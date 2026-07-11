using System.Text.RegularExpressions;
using Microsoft.Data.Sqlite;


namespace ClickAssistant.Infrastructure.Persistence;

/// <summary>
/// 版本化数据库迁移器，基于 schema_migrations 表管理升级与回滚。
/// 替代原先基于 PRAGMA table_info 的补列逻辑。
/// </summary>
public sealed class DatabaseMigrator
{
    private readonly SqliteConnectionFactory connectionFactory;

    /// <summary>
    /// 迁移列表按版本号升序排列。每个迁移包含：版本号标识、迁移名称和升级 SQL。
    /// </summary>
    private static readonly IReadOnlyList<Migration> Migrations = new List<Migration>
    {
        // v1.0.0 - 初始建表
        new("1.0.0", "Initial schema", CreateInitialSchemaSql()),

        // v1.1.0 - 新增键盘按键与文本输入相关列
        new("1.1.0", "Add keyboard and text input columns", new[]
        {
            "ALTER TABLE task_steps ADD COLUMN action_type TEXT NOT NULL DEFAULT 'MouseClick';",
            "ALTER TABLE task_steps ADD COLUMN mouse_click_count INTEGER NOT NULL DEFAULT 1;",
            "ALTER TABLE task_steps ADD COLUMN key_name TEXT NOT NULL DEFAULT '';",
            "ALTER TABLE task_steps ADD COLUMN key_press_count INTEGER NOT NULL DEFAULT 1;",
            "ALTER TABLE task_steps ADD COLUMN key_interval_ms INTEGER NOT NULL DEFAULT 100;",
            "ALTER TABLE task_steps ADD COLUMN shortcut_keys TEXT NOT NULL DEFAULT 'Ctrl+C';",
            "ALTER TABLE task_steps ADD COLUMN text_content TEXT NOT NULL DEFAULT '';"
        }),

        // v1.2.0 - 新增点击、滑动和文本输入增强列（取消 AfterDelayMs，新增 ClickInterval 等）
        new("1.2.0", "Add enhanced click, swipe and text input columns", new[]
        {
            "ALTER TABLE task_steps ADD COLUMN click_interval_ms INTEGER NOT NULL DEFAULT 100;",
            "ALTER TABLE task_steps ADD COLUMN press_duration_ms INTEGER NOT NULL DEFAULT 0;",
            "ALTER TABLE task_steps ADD COLUMN auto_focus_before_input INTEGER NOT NULL DEFAULT 0;",
            "ALTER TABLE task_steps ADD COLUMN end_x INTEGER NOT NULL DEFAULT 0;",
            "ALTER TABLE task_steps ADD COLUMN end_y INTEGER NOT NULL DEFAULT 0;",
            "ALTER TABLE task_steps ADD COLUMN swipe_duration_ms INTEGER NOT NULL DEFAULT 300;"
        }),

        // v1.3.0 - 真正落实"取消 AfterDelayMs"：移除 task_steps.after_delay_ms 列
        // v1.0.0 初始建表时定义了 after_delay_ms INTEGER NOT NULL DEFAULT 0；
        // 仓库层 INSERT 早已不写该字段，老用户数据库的 NOT NULL 约束会在新建/复制任务时报错。
        // SQLite ≥ 3.35 原生支持 DROP COLUMN。
        new("1.3.0", "Remove deprecated after_delay_ms column", new[]
        {
            "ALTER TABLE task_steps DROP COLUMN after_delay_ms;"
        })
    };

    public DatabaseMigrator(SqliteConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }

    /// <summary>
    /// 初始化数据库并执行所有未应用的迁移。
    /// </summary>
    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        SQLitePCL.Batteries_V2.Init();

        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await EnsureMigrationsTableAsync(connection, cancellationToken);
        await ApplyMissingMigrationsAsync(connection, cancellationToken);
    }

    /// <summary>
    /// 确保 schema_migrations 表存在。
    /// 兼容旧版仅含 version/applied_at 两列的表结构，自动补充 name 列。
    /// </summary>
    private static async Task EnsureMigrationsTableAsync(
        SqliteConnection connection,
        CancellationToken cancellationToken)
    {
        await using var transaction = (SqliteTransaction)await connection.BeginTransactionAsync(cancellationToken);

        try
        {
            await using var createCommand = connection.CreateCommand();
            createCommand.Transaction = transaction;
            createCommand.CommandText = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version TEXT PRIMARY KEY,
                    applied_at TEXT NOT NULL
                );
                """;
            await createCommand.ExecuteNonQueryAsync(cancellationToken);

            if (!await HasColumnAsync(connection, "schema_migrations", "name", cancellationToken, transaction))
            {
                await using var alterCommand = connection.CreateCommand();
                alterCommand.Transaction = transaction;
                alterCommand.CommandText = """
                    ALTER TABLE schema_migrations ADD COLUMN name TEXT NOT NULL DEFAULT '';
                    """;
                await alterCommand.ExecuteNonQueryAsync(cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    /// <summary>
    /// 已知表名白名单，PRAGMA table_info 仅允许查询这些表，防止拼装 SQL 注入。
    /// </summary>
    private static readonly HashSet<string> AllowedTableNames =
        new(StringComparer.OrdinalIgnoreCase)
        {
            "tasks",
            "task_steps",
            "execution_logs",
            "app_settings",
            "schema_migrations"
        };

    /// <summary>
    /// 检查指定表是否包含某列，表名仅限白名单。
    /// </summary>
    private static async Task<bool> HasColumnAsync(
        SqliteConnection connection,
        string tableName,
        string columnName,
        CancellationToken cancellationToken,
        SqliteTransaction? transaction = null)
    {
        if (!AllowedTableNames.Contains(tableName))
        {
            return false;
        }

        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = $"PRAGMA table_info({tableName});";

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            if (string.Equals(reader.GetString(1), columnName, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }

        return false;
    }

    /// <summary>
    /// 获取已应用的所有迁移版本号。
    /// </summary>
    private static async Task<HashSet<string>> GetAppliedMigrationsAsync(
        SqliteConnection connection,
        CancellationToken cancellationToken)
    {
        var applied = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT version FROM schema_migrations ORDER BY version;";

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            applied.Add(reader.GetString(0));
        }

        return applied;
    }

    /// <summary>
    /// 按版本号升序执行所有未应用的迁移，每个迁移在独立事务中执行。
    /// 若迁移语句为 ALTER TABLE ADD COLUMN 且目标列已存在，或为 DROP COLUMN 且目标列已不存在，则自动跳过该语句。
    /// </summary>
    private static async Task ApplyMissingMigrationsAsync(
        SqliteConnection connection,
        CancellationToken cancellationToken)
    {
        var applied = await GetAppliedMigrationsAsync(connection, cancellationToken);

        foreach (var migration in Migrations)
        {
            if (applied.Contains(migration.Version))
            {
                continue;
            }

            await using var transaction = (SqliteTransaction)await connection.BeginTransactionAsync(cancellationToken);

            try
            {
                foreach (var sql in migration.SqlStatements)
                {
                    if (TryParseAlterTableAddColumn(sql, out var tableName, out var columnName)
                        && await HasColumnAsync(connection, tableName, columnName, cancellationToken, transaction))
                    {
                        // ADD COLUMN：目标列已存在则跳过（兼容已升级过的库）
                        continue;
                    }

                    if (TryParseAlterTableDropColumn(sql, out var dropTable, out var dropColumn)
                        && !await HasColumnAsync(connection, dropTable, dropColumn, cancellationToken, transaction))
                    {
                        // DROP COLUMN：目标列已不存在则跳过（兼容新建库或已清理的库）
                        continue;
                    }

                    await using var command = connection.CreateCommand();
                    command.Transaction = transaction;
                    command.CommandText = sql;
                    await command.ExecuteNonQueryAsync(cancellationToken);
                }

                await using var recordCommand = connection.CreateCommand();
                recordCommand.Transaction = transaction;
                recordCommand.CommandText = """
                    INSERT INTO schema_migrations (version, name, applied_at)
                    VALUES ($version, $name, $appliedAt);
                    """;
                recordCommand.Parameters.AddWithValue("$version", migration.Version);
                recordCommand.Parameters.AddWithValue("$name", migration.Name);
                recordCommand.Parameters.AddWithValue("$appliedAt", DateTime.UtcNow.ToString("O"));
                await recordCommand.ExecuteNonQueryAsync(cancellationToken);

                await transaction.CommitAsync(cancellationToken);
            }
            catch
            {
                await transaction.RollbackAsync(cancellationToken);
                throw;
            }
        }
    }

    /// <summary>
    /// 尝试解析 ALTER TABLE ... ADD COLUMN 语句，返回表名与列名。
    /// </summary>
    private static bool TryParseAlterTableAddColumn(
        string sql,
        out string tableName,
        out string columnName)
    {
        tableName = string.Empty;
        columnName = string.Empty;

        var match = Regex.Match(
            sql,
            @"ALTER\s+TABLE\s+(?<table>\w+)\s+ADD\s+COLUMN\s+(?<column>\w+)",
            RegexOptions.IgnoreCase | RegexOptions.IgnorePatternWhitespace);

        if (!match.Success)
        {
            return false;
        }

        tableName = match.Groups["table"].Value;
        columnName = match.Groups["column"].Value;
        return true;
    }

    /// <summary>
    /// 尝试解析 ALTER TABLE ... DROP COLUMN 语句，返回表名与列名。
    /// </summary>
    private static bool TryParseAlterTableDropColumn(
        string sql,
        out string tableName,
        out string columnName)
    {
        tableName = string.Empty;
        columnName = string.Empty;

        var match = Regex.Match(
            sql,
            @"ALTER\s+TABLE\s+(?<table>\w+)\s+DROP\s+COLUMN\s+(?<column>\w+)",
            RegexOptions.IgnoreCase | RegexOptions.IgnorePatternWhitespace);

        if (!match.Success)
        {
            return false;
        }

        tableName = match.Groups["table"].Value;
        columnName = match.Groups["column"].Value;
        return true;
    }

    /// <summary>
    /// 获取当前数据库已应用的最高迁移版本号。
    /// </summary>
    public async Task<string?> GetCurrentVersionAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT MAX(version) FROM schema_migrations;";
        var result = await command.ExecuteScalarAsync(cancellationToken);

        return result?.ToString();
    }

    private static string[] CreateInitialSchemaSql()
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
            """
        ];
    }

    /// <summary>
    /// 单次迁移记录，包含版本号、名称和 SQL 语句列表。
    /// </summary>
    private sealed class Migration
    {
        public string Version { get; }
        public string Name { get; }
        public string[] SqlStatements { get; }

        public Migration(string version, string name, string[] sqlStatements)
        {
            Version = version;
            Name = name;
            SqlStatements = sqlStatements;
        }
    }
}
