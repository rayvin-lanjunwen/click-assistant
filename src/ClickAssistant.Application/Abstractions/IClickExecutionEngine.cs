using ClickAssistant.Domain.Enums;

namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 输入执行引擎接口，负责任务运行状态控制。
/// </summary>
public interface IClickExecutionEngine
{
    ExecutionStatus Status { get; }

    event EventHandler<ExecutionStatus>? StatusChanged;

    event EventHandler<string>? LogReceived;

    event EventHandler<MouseClickVisualEventArgs>? MouseClickVisualRequested;

    Task StartAsync(Guid taskId, CancellationToken cancellationToken = default);

    Task PauseAsync();

    Task ResumeAsync();

    Task StopAsync();
}
