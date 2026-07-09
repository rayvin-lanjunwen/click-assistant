using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.Entities;

namespace ClickAssistant.Application.Services;

/// <summary>
/// 点击任务应用服务，负责界面用例与仓储之间的编排。
/// </summary>
public sealed class ClickTaskService
{
    private readonly IClickTaskRepository taskRepository;

    public ClickTaskService(IClickTaskRepository taskRepository)
    {
        this.taskRepository = taskRepository;
    }

    /// <summary>
    /// 读取全部任务，供主界面任务列表展示。
    /// </summary>
    public Task<IReadOnlyList<ClickTask>> GetAllAsync(CancellationToken cancellationToken = default)
    {
        return taskRepository.GetAllAsync(cancellationToken);
    }

    /// <summary>
    /// 保存任务前统一刷新时间和步骤归属。
    /// </summary>
    public async Task SaveAsync(ClickTask task, CancellationToken cancellationToken = default)
    {
        task.UpdatedAt = DateTime.Now;

        if (task.CreatedAt == default)
        {
            task.CreatedAt = task.UpdatedAt;
        }

        for (var index = 0; index < task.Steps.Count; index++)
        {
            task.Steps[index].TaskId = task.Id;
            task.Steps[index].Order = index;
        }

        task.ValidateForSave();
        await taskRepository.SaveAsync(task, cancellationToken);
    }

    /// <summary>
    /// 删除指定任务。
    /// </summary>
    public Task DeleteAsync(Guid taskId, CancellationToken cancellationToken = default)
    {
        return taskRepository.DeleteAsync(taskId, cancellationToken);
    }

    /// <summary>
    /// 复制任务并立即保存。
    /// </summary>
    public async Task<ClickTask> DuplicateAsync(ClickTask task, CancellationToken cancellationToken = default)
    {
        var duplicatedTask = task.Duplicate();
        await SaveAsync(duplicatedTask, cancellationToken);
        return duplicatedTask;
    }
}
