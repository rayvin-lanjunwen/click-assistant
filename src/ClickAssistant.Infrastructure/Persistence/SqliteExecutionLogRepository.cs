using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;

namespace ClickAssistant.Infrastructure.Persistence;

/// <summary>
/// 基于 SQLite 的执行日志仓储。
/// </summary>
public sealed class SqliteExecutionLogRepository : IExecutionLogRepository
{
    private readonly SqliteConnectionFactory connectionFactory;

    public SqliteExecutionLogRepository(SqliteConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }

    /// <summary>
    /// 添加一条执行日志。
    /// </summary>
    public async Task AddAsync(ExecutionLog log, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO execution_logs (id, task_id, status, started_at, ended_at, message)
            VALUES ($id, $taskId, $status, $startedAt, $endedAt, $message);
            """;
        command.Parameters.AddWithValue("$id", log.Id.ToString());
        command.Parameters.AddWithValue("$taskId", log.TaskId.ToString());
        command.Parameters.AddWithValue("$status", log.Status.ToString());
        command.Parameters.AddWithValue("$startedAt", log.StartedAt.ToString("O"));
        command.Parameters.AddWithValue("$endedAt", log.EndedAt?.ToString("O") ?? string.Empty);
        command.Parameters.AddWithValue("$message", log.Message);

        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    /// <summary>
    /// 获取最近执行日志。
    /// </summary>
    public async Task<IReadOnlyList<ExecutionLog>> GetRecentAsync(int limit, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        var logs = new List<ExecutionLog>();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, task_id, status, started_at, ended_at, message
            FROM execution_logs
            ORDER BY datetime(started_at) DESC
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$limit", limit);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);

        while (await reader.ReadAsync(cancellationToken))
        {
            var endedAtText = reader.GetString(4);
            logs.Add(new ExecutionLog
            {
                Id = Guid.Parse(reader.GetString(0)),
                TaskId = Guid.Parse(reader.GetString(1)),
                Status = Enum.Parse<ExecutionStatus>(reader.GetString(2)),
                StartedAt = DateTime.Parse(reader.GetString(3)),
                EndedAt = string.IsNullOrWhiteSpace(endedAtText) ? null : DateTime.Parse(endedAtText),
                Message = reader.GetString(5)
            });
        }

        return logs;
    }
}
