using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;

namespace ClickAssistant.Application.Services;

/// <summary>
/// 输入执行引擎，负责按任务配置执行步骤并控制状态流转。
/// </summary>
public sealed class ClickExecutionEngine : IClickExecutionEngine
{
    private readonly IClickTaskRepository taskRepository;
    private readonly IExecutionLogRepository executionLogRepository;
    private readonly IMouseClickService mouseClickService;
    private readonly IKeyboardInputService keyboardInputService;
    private readonly SemaphoreSlim executionLock = new(1, 1);
    private CancellationTokenSource? stopTokenSource;
    private TaskCompletionSource pauseGate = CreateOpenedGate();

    public ClickExecutionEngine(
        IClickTaskRepository taskRepository,
        IExecutionLogRepository executionLogRepository,
        IMouseClickService mouseClickService,
        IKeyboardInputService keyboardInputService)
    {
        this.taskRepository = taskRepository;
        this.executionLogRepository = executionLogRepository;
        this.mouseClickService = mouseClickService;
        this.keyboardInputService = keyboardInputService;
    }

    public ExecutionStatus Status { get; private set; } = ExecutionStatus.Idle;

    public event EventHandler<ExecutionStatus>? StatusChanged;

    public event EventHandler<string>? LogReceived;

    /// <summary>
    /// 启动任务执行，同一时间只允许一个任务运行。
    /// </summary>
    public async Task StartAsync(Guid taskId, CancellationToken cancellationToken = default)
    {
        await executionLock.WaitAsync(cancellationToken);

        try
        {
            if (Status is ExecutionStatus.Running or ExecutionStatus.Paused or ExecutionStatus.Starting)
            {
                PublishLog("已有任务正在运行，请先停止当前任务。");
                return;
            }

            stopTokenSource = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            pauseGate = CreateOpenedGate();

            await RunTaskAsync(taskId, stopTokenSource.Token);
        }
        finally
        {
            stopTokenSource?.Dispose();
            stopTokenSource = null;
            SetStatus(ExecutionStatus.Idle);
            executionLock.Release();
        }
    }

    /// <summary>
    /// 暂停正在执行的任务。
    /// </summary>
    public Task PauseAsync()
    {
        if (Status != ExecutionStatus.Running)
        {
            return Task.CompletedTask;
        }

        pauseGate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        SetStatus(ExecutionStatus.Paused);
        PublishLog("任务已暂停。");
        return Task.CompletedTask;
    }

    /// <summary>
    /// 继续已暂停的任务。
    /// </summary>
    public Task ResumeAsync()
    {
        if (Status != ExecutionStatus.Paused)
        {
            return Task.CompletedTask;
        }

        pauseGate.TrySetResult();
        SetStatus(ExecutionStatus.Running);
        PublishLog("任务继续执行。");
        return Task.CompletedTask;
    }

    /// <summary>
    /// 停止任务，取消令牌会被执行循环优先检查。
    /// </summary>
    public Task StopAsync()
    {
        stopTokenSource?.Cancel();
        pauseGate.TrySetResult();

        if (Status is ExecutionStatus.Running or ExecutionStatus.Paused or ExecutionStatus.Starting)
        {
            SetStatus(ExecutionStatus.Stopped);
            PublishLog("已请求立即停止。");
        }

        return Task.CompletedTask;
    }

    /// <summary>
    /// 执行完整任务流程，包括倒计时、循环、步骤点击和日志记录。
    /// </summary>
    private async Task RunTaskAsync(Guid taskId, CancellationToken cancellationToken)
    {
        var task = await taskRepository.GetByIdAsync(taskId, cancellationToken)
            ?? throw new InvalidOperationException("任务不存在，无法执行。");

        var startedAt = DateTime.Now;
        var executionLog = new ExecutionLog
        {
            TaskId = task.Id,
            StartedAt = startedAt,
            Status = ExecutionStatus.Running,
            Message = "任务开始执行。"
        };

        try
        {
            task.ValidateForExecution();
            SetStatus(ExecutionStatus.Starting);
            PublishLog($"任务“{task.Name}”准备启动。");

            await DelayWithPauseAsync(task.StartDelayMs, cancellationToken);

            SetStatus(ExecutionStatus.Running);
            var enabledSteps = task.Steps
                .Where(step => step.Enabled)
                .OrderBy(step => step.Order)
                .ToList();

            for (var round = 1; round <= task.RepeatCount; round++)
            {
                foreach (var step in enabledSteps)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    await pauseGate.Task.WaitAsync(cancellationToken);

                    PublishLog($"第 {round}/{task.RepeatCount} 轮：执行步骤“{step.Name}”。");
                    await DelayWithPauseAsync(step.BeforeDelayMs, cancellationToken);
                    await ExecuteStepActionAsync(step, cancellationToken);
                    await DelayWithPauseAsync(step.AfterDelayMs, cancellationToken);
                }
            }

            SetStatus(ExecutionStatus.Completed);
            executionLog.Status = ExecutionStatus.Completed;
            executionLog.Message = "任务执行完成。";
            PublishLog("任务执行完成。");
        }
        catch (OperationCanceledException)
        {
            executionLog.Status = ExecutionStatus.Stopped;
            executionLog.Message = "任务被用户停止。";
            PublishLog("任务已停止。");
        }
        catch (Exception exception)
        {
            SetStatus(ExecutionStatus.Failed);
            executionLog.Status = ExecutionStatus.Failed;
            executionLog.Message = exception.Message;
            PublishLog($"任务执行失败：{exception.Message}");
        }
        finally
        {
            executionLog.EndedAt = DateTime.Now;
            await executionLogRepository.AddAsync(executionLog, CancellationToken.None);
        }
    }

    /// <summary>
    /// 根据步骤动作类型分发到鼠标或键盘输入服务。
    /// </summary>
    private async Task ExecuteStepActionAsync(ClickStep step, CancellationToken cancellationToken)
    {
        switch (step.ActionType)
        {
            case InputActionType.MouseClick:
                await mouseClickService.ClickAsync(step.ToPoint(), step.ClickType, cancellationToken);
                break;
            case InputActionType.KeyboardPress:
                await keyboardInputService.PressKeyAsync(
                    step.KeyName,
                    step.KeyPressCount,
                    step.KeyIntervalMs,
                    cancellationToken);
                break;
            case InputActionType.KeyboardShortcut:
                await keyboardInputService.PressShortcutAsync(step.ShortcutKeys, cancellationToken);
                break;
            case InputActionType.TextInput:
                await keyboardInputService.TypeTextAsync(
                    step.TextContent,
                    step.KeyIntervalMs,
                    cancellationToken);
                break;
            default:
                throw new ArgumentOutOfRangeException(nameof(step), step.ActionType, "未知输入动作类型。");
        }
    }

    /// <summary>
    /// 支持暂停和停止的延迟方法，避免普通 Task.Delay 阻塞状态响应。
    /// </summary>
    private async Task DelayWithPauseAsync(int milliseconds, CancellationToken cancellationToken)
    {
        var remaining = milliseconds;

        while (remaining > 0)
        {
            cancellationToken.ThrowIfCancellationRequested();
            await pauseGate.Task.WaitAsync(cancellationToken);

            var delay = Math.Min(remaining, 100);
            await Task.Delay(delay, cancellationToken);
            remaining -= delay;
        }
    }

    /// <summary>
    /// 设置状态并通知界面。
    /// </summary>
    private void SetStatus(ExecutionStatus status)
    {
        Status = status;
        StatusChanged?.Invoke(this, status);
    }

    /// <summary>
    /// 发布执行日志消息。
    /// </summary>
    private void PublishLog(string message)
    {
        LogReceived?.Invoke(this, $"{DateTime.Now:HH:mm:ss} {message}");
    }

    /// <summary>
    /// 创建默认打开的暂停门，保证未暂停时可直接通过。
    /// </summary>
    private static TaskCompletionSource CreateOpenedGate()
    {
        var source = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        source.SetResult();
        return source;
    }
}
