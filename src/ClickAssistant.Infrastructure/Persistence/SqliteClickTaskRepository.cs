using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using Microsoft.Data.Sqlite;

namespace ClickAssistant.Infrastructure.Persistence;

/// <summary>
/// 基于 SQLite 的点击任务仓储，使用手写参数化 SQL。
/// </summary>
public sealed class SqliteClickTaskRepository : IClickTaskRepository
{
    private readonly SqliteConnectionFactory connectionFactory;

    public SqliteClickTaskRepository(SqliteConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }

    /// <summary>
    /// 获取全部任务，并按更新时间倒序排列。
    /// </summary>
    public async Task<IReadOnlyList<ClickTask>> GetAllAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        var tasks = new List<ClickTask>();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, name, description, enabled, repeat_count, start_delay_ms, created_at, updated_at
            FROM tasks
            ORDER BY datetime(updated_at) DESC;
            """;

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);

        while (await reader.ReadAsync(cancellationToken))
        {
            tasks.Add(MapTask(reader));
        }

        foreach (var task in tasks)
        {
            task.Steps = await GetStepsAsync(connection, task.Id, cancellationToken);
        }

        return tasks;
    }

    /// <summary>
    /// 根据任务标识获取完整任务。
    /// </summary>
    public async Task<ClickTask?> GetByIdAsync(Guid taskId, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, name, description, enabled, repeat_count, start_delay_ms, created_at, updated_at
            FROM tasks
            WHERE id = $id;
            """;
        command.Parameters.AddWithValue("$id", taskId.ToString());

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);

        if (!await reader.ReadAsync(cancellationToken))
        {
            return null;
        }

        var task = MapTask(reader);
        task.Steps = await GetStepsAsync(connection, task.Id, cancellationToken);
        return task;
    }

    /// <summary>
    /// 保存任务和步骤，使用事务保证一致性。
    /// </summary>
    public async Task SaveAsync(ClickTask task, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        // 异步事务 API 返回通用事务对象，这里转回 SQLite 事务以匹配命令对象。
        await using var transaction = (SqliteTransaction)await connection.BeginTransactionAsync(cancellationToken);

        await UpsertTaskAsync(connection, transaction, task, cancellationToken);
        await DeleteStepsAsync(connection, transaction, task.Id, cancellationToken);

        foreach (var step in task.Steps.OrderBy(step => step.Order))
        {
            await InsertStepAsync(connection, transaction, step, cancellationToken);
        }

        await transaction.CommitAsync(cancellationToken);
    }

    /// <summary>
    /// 删除任务，同时级联删除步骤。
    /// </summary>
    public async Task DeleteAsync(Guid taskId, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        // 删除任务和步骤必须在同一个 SQLite 事务内完成，避免出现孤立步骤。
        await using var transaction = (SqliteTransaction)await connection.BeginTransactionAsync(cancellationToken);

        await using (var deleteStepsCommand = connection.CreateCommand())
        {
            deleteStepsCommand.Transaction = transaction;
            deleteStepsCommand.CommandText = "DELETE FROM task_steps WHERE task_id = $taskId;";
            deleteStepsCommand.Parameters.AddWithValue("$taskId", taskId.ToString());
            await deleteStepsCommand.ExecuteNonQueryAsync(cancellationToken);
        }

        await using (var deleteTaskCommand = connection.CreateCommand())
        {
            deleteTaskCommand.Transaction = transaction;
            deleteTaskCommand.CommandText = "DELETE FROM tasks WHERE id = $id;";
            deleteTaskCommand.Parameters.AddWithValue("$id", taskId.ToString());
            await deleteTaskCommand.ExecuteNonQueryAsync(cancellationToken);
        }

        await transaction.CommitAsync(cancellationToken);
    }

    /// <summary>
    /// 插入或更新任务基础信息。
    /// </summary>
    private static async Task UpsertTaskAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        ClickTask task,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO tasks (id, name, description, enabled, repeat_count, start_delay_ms, created_at, updated_at)
            VALUES ($id, $name, $description, $enabled, $repeatCount, $startDelayMs, $createdAt, $updatedAt)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                enabled = excluded.enabled,
                repeat_count = excluded.repeat_count,
                start_delay_ms = excluded.start_delay_ms,
                updated_at = excluded.updated_at;
            """;

        command.Parameters.AddWithValue("$id", task.Id.ToString());
        command.Parameters.AddWithValue("$name", task.Name);
        command.Parameters.AddWithValue("$description", task.Description);
        command.Parameters.AddWithValue("$enabled", task.Enabled ? 1 : 0);
        command.Parameters.AddWithValue("$repeatCount", task.RepeatCount);
        command.Parameters.AddWithValue("$startDelayMs", task.StartDelayMs);
        command.Parameters.AddWithValue("$createdAt", task.CreatedAt.ToString("O"));
        command.Parameters.AddWithValue("$updatedAt", task.UpdatedAt.ToString("O"));

        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    /// <summary>
    /// 删除任务下旧步骤，保存时再整体插入新步骤。
    /// </summary>
    private static async Task DeleteStepsAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        Guid taskId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "DELETE FROM task_steps WHERE task_id = $taskId;";
        command.Parameters.AddWithValue("$taskId", taskId.ToString());
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    /// <summary>
    /// 插入单个输入步骤。
    /// </summary>
    private static async Task InsertStepAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        ClickStep step,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO task_steps (
                id, task_id, name, enabled, action_type, x, y, click_type, mouse_click_count,
                click_interval_ms, press_duration_ms,
                key_name, key_press_count, key_interval_ms, shortcut_keys, text_content,
                auto_focus_before_input,
                end_x, end_y, swipe_duration_ms,
                before_delay_ms, step_order
            )
            VALUES (
                $id, $taskId, $name, $enabled, $actionType, $x, $y, $clickType, $mouseClickCount,
                $clickIntervalMs, $pressDurationMs,
                $keyName, $keyPressCount, $keyIntervalMs, $shortcutKeys, $textContent,
                $autoFocusBeforeInput,
                $endX, $endY, $swipeDurationMs,
                $beforeDelayMs, $order
            );
            """;

        command.Parameters.AddWithValue("$id", step.Id.ToString());
        command.Parameters.AddWithValue("$taskId", step.TaskId.ToString());
        command.Parameters.AddWithValue("$name", step.Name);
        command.Parameters.AddWithValue("$enabled", step.Enabled ? 1 : 0);
        command.Parameters.AddWithValue("$actionType", step.ActionType.ToString());
        command.Parameters.AddWithValue("$x", step.X);
        command.Parameters.AddWithValue("$y", step.Y);
        command.Parameters.AddWithValue("$clickType", step.ClickType.ToString());
        command.Parameters.AddWithValue("$mouseClickCount", step.MouseClickCount);
        command.Parameters.AddWithValue("$clickIntervalMs", step.ClickIntervalMs);
        command.Parameters.AddWithValue("$pressDurationMs", step.PressDurationMs);
        command.Parameters.AddWithValue("$keyName", step.KeyName);
        command.Parameters.AddWithValue("$keyPressCount", step.KeyPressCount);
        command.Parameters.AddWithValue("$keyIntervalMs", step.KeyIntervalMs);
        command.Parameters.AddWithValue("$shortcutKeys", step.ShortcutKeys);
        command.Parameters.AddWithValue("$textContent", step.TextContent);
        command.Parameters.AddWithValue("$autoFocusBeforeInput", step.AutoFocusBeforeInput ? 1 : 0);
        command.Parameters.AddWithValue("$endX", step.EndX);
        command.Parameters.AddWithValue("$endY", step.EndY);
        command.Parameters.AddWithValue("$swipeDurationMs", step.SwipeDurationMs);
        command.Parameters.AddWithValue("$beforeDelayMs", step.BeforeDelayMs);
        command.Parameters.AddWithValue("$order", step.Order);

        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    /// <summary>
    /// 读取任务下全部步骤。
    /// </summary>
    private static async Task<List<ClickStep>> GetStepsAsync(
        SqliteConnection connection,
        Guid taskId,
        CancellationToken cancellationToken)
    {
        var steps = new List<ClickStep>();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT
                id, task_id, name, enabled, action_type, x, y, click_type, mouse_click_count,
                COALESCE(click_interval_ms, 100) AS click_interval_ms,
                COALESCE(press_duration_ms, 0) AS press_duration_ms,
                key_name, key_press_count, key_interval_ms, shortcut_keys, text_content,
                COALESCE(auto_focus_before_input, 0) AS auto_focus_before_input,
                COALESCE(end_x, 0) AS end_x,
                COALESCE(end_y, 0) AS end_y,
                COALESCE(swipe_duration_ms, 300) AS swipe_duration_ms,
                before_delay_ms, step_order
            FROM task_steps
            WHERE task_id = $taskId
            ORDER BY step_order ASC;
            """;
        command.Parameters.AddWithValue("$taskId", taskId.ToString());

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);

        while (await reader.ReadAsync(cancellationToken))
        {
            steps.Add(new ClickStep
            {
                Id = Guid.Parse(reader.GetString(reader.GetOrdinal("id"))),
                TaskId = Guid.Parse(reader.GetString(reader.GetOrdinal("task_id"))),
                Name = reader.GetString(reader.GetOrdinal("name")),
                Enabled = reader.GetInt32(reader.GetOrdinal("enabled")) == 1,
                ActionType = ParseEnumOrDefault(reader.GetString(reader.GetOrdinal("action_type")), InputActionType.MouseClick),
                X = reader.GetInt32(reader.GetOrdinal("x")),
                Y = reader.GetInt32(reader.GetOrdinal("y")),
                ClickType = ParseEnumOrDefault(reader.GetString(reader.GetOrdinal("click_type")), ClickType.LeftSingle),
                MouseClickCount = reader.GetInt32(reader.GetOrdinal("mouse_click_count")),
                ClickIntervalMs = reader.GetInt32(reader.GetOrdinal("click_interval_ms")),
                PressDurationMs = reader.GetInt32(reader.GetOrdinal("press_duration_ms")),
                KeyName = reader.GetString(reader.GetOrdinal("key_name")),
                KeyPressCount = reader.GetInt32(reader.GetOrdinal("key_press_count")),
                KeyIntervalMs = reader.GetInt32(reader.GetOrdinal("key_interval_ms")),
                ShortcutKeys = reader.GetString(reader.GetOrdinal("shortcut_keys")),
                TextContent = reader.GetString(reader.GetOrdinal("text_content")),
                AutoFocusBeforeInput = reader.GetInt32(reader.GetOrdinal("auto_focus_before_input")) == 1,
                EndX = reader.GetInt32(reader.GetOrdinal("end_x")),
                EndY = reader.GetInt32(reader.GetOrdinal("end_y")),
                SwipeDurationMs = reader.GetInt32(reader.GetOrdinal("swipe_duration_ms")),
                BeforeDelayMs = reader.GetInt32(reader.GetOrdinal("before_delay_ms")),
                Order = reader.GetInt32(reader.GetOrdinal("step_order"))
            });
        }

        return steps;
    }

    /// <summary>
    /// 解析枚举字段，旧数据或异常数据无法解析时回退到默认值。
    /// </summary>
    private static TEnum ParseEnumOrDefault<TEnum>(string value, TEnum defaultValue)
        where TEnum : struct
    {
        return Enum.TryParse<TEnum>(value, out var parsedValue)
            ? parsedValue
            : defaultValue;
    }

    /// <summary>
    /// 将 SQLite 数据行转换为领域任务。
    /// </summary>
    private static ClickTask MapTask(SqliteDataReader reader)
    {
        return new ClickTask
        {
            Id = Guid.Parse(reader.GetString(0)),
            Name = reader.GetString(1),
            Description = reader.GetString(2),
            Enabled = reader.GetInt32(3) == 1,
            RepeatCount = reader.GetInt32(4),
            StartDelayMs = reader.GetInt32(5),
            CreatedAt = DateTime.Parse(reader.GetString(6)),
            UpdatedAt = DateTime.Parse(reader.GetString(7))
        };
    }
}
