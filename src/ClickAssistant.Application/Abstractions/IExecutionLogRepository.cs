using ClickAssistant.Domain.Entities;

namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 执行日志仓储接口，用于记录和读取任务执行结果。
/// </summary>
public interface IExecutionLogRepository
{
    Task AddAsync(ExecutionLog log, CancellationToken cancellationToken = default);

    Task<IReadOnlyList<ExecutionLog>> GetRecentAsync(int limit, CancellationToken cancellationToken = default);
}
