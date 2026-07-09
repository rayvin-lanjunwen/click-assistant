using ClickAssistant.Domain.Entities;

namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 点击任务仓储接口，应用层通过它访问任务数据。
/// </summary>
public interface IClickTaskRepository
{
    Task<IReadOnlyList<ClickTask>> GetAllAsync(CancellationToken cancellationToken = default);

    Task<ClickTask?> GetByIdAsync(Guid taskId, CancellationToken cancellationToken = default);

    Task SaveAsync(ClickTask task, CancellationToken cancellationToken = default);

    Task DeleteAsync(Guid taskId, CancellationToken cancellationToken = default);
}
