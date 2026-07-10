using ClickAssistant.Application.Abstractions;
using ClickAssistant.Application.Services;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Tests.Application;

public sealed class ClickExecutionEngineTests
{
    [Fact]
    public async Task StartPauseResumeAndStop_ShouldUpdateStatusAndWriteStoppedLog()
    {
        var task = new ClickTask
        {
            Id = Guid.NewGuid(),
            Name = "执行状态测试任务",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "阻塞鼠标步骤",
                    ActionType = InputActionType.MouseClick,
                    Enabled = true,
                    X = 10,
                    Y = 20,
                    BeforeDelayMs = 0,
                    AfterDelayMs = 0,
                    Order = 0
                }
            ]
        };

        var taskRepository = new InMemoryClickTaskRepository(task);
        var logRepository = new InMemoryExecutionLogRepository();
        var mouseClickService = new BlockingMouseClickService();
        var keyboardInputService = new NoopKeyboardInputService();
        var engine = new ClickExecutionEngine(
            taskRepository,
            logRepository,
            mouseClickService,
            keyboardInputService);
        var statuses = new List<ExecutionStatus>();
        engine.StatusChanged += (_, status) => statuses.Add(status);

        var executionTask = engine.StartAsync(task.Id);
        await mouseClickService.ClickStarted.Task.WaitAsync(TimeSpan.FromSeconds(2));

        await engine.PauseAsync();
        await engine.ResumeAsync();
        await engine.StopAsync();
        await executionTask.WaitAsync(TimeSpan.FromSeconds(2));

        Assert.Contains(ExecutionStatus.Starting, statuses);
        Assert.Contains(ExecutionStatus.Running, statuses);
        Assert.Contains(ExecutionStatus.Paused, statuses);
        Assert.Contains(ExecutionStatus.Stopped, statuses);
        Assert.Equal(ExecutionStatus.Idle, engine.Status);
        var log = Assert.Single(logRepository.Logs);
        Assert.Equal(ExecutionStatus.Stopped, log.Status);
    }

    [Fact]
    public async Task StartAsync_WhenTaskContainsKeyboardActions_ShouldDispatchAllKeyboardActionTypes()
    {
        var task = new ClickTask
        {
            Id = Guid.NewGuid(),
            Name = "键盘动作测试任务",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "单键步骤",
                    ActionType = InputActionType.KeyboardPress,
                    KeyName = "Enter",
                    KeyPressCount = 2,
                    KeyIntervalMs = 0,
                    Order = 0
                },
                new ClickStep
                {
                    Name = "组合键步骤",
                    ActionType = InputActionType.KeyboardShortcut,
                    ShortcutKeys = "Ctrl+C",
                    Order = 1
                },
                new ClickStep
                {
                    Name = "文本步骤",
                    ActionType = InputActionType.TextInput,
                    TextContent = "Hello",
                    KeyIntervalMs = 0,
                    Order = 2
                }
            ]
        };

        var keyboardInputService = new RecordingKeyboardInputService();
        var engine = new ClickExecutionEngine(
            new InMemoryClickTaskRepository(task),
            new InMemoryExecutionLogRepository(),
            new NoopMouseClickService(),
            keyboardInputService);

        await engine.StartAsync(task.Id);

        Assert.Equal(["Key:Enter:2:0", "Shortcut:Ctrl+C", "Text:Hello:0"], keyboardInputService.Actions);
    }

    [Fact]
    public async Task StartAsync_WhenTaskContainsMouseClick_ShouldPublishVisualRequestBeforeClick()
    {
        var task = new ClickTask
        {
            Id = Guid.NewGuid(),
            Name = "鼠标点击提示测试任务",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "鼠标步骤",
                    ActionType = InputActionType.MouseClick,
                    X = 120,
                    Y = 260,
                    ClickType = ClickType.RightSingle,
                    Order = 0
                }
            ]
        };
        var engine = new ClickExecutionEngine(
            new InMemoryClickTaskRepository(task),
            new InMemoryExecutionLogRepository(),
            new NoopMouseClickService(),
            new NoopKeyboardInputService());
        var visualRequests = new List<MouseClickVisualEventArgs>();
        engine.MouseClickVisualRequested += (_, args) => visualRequests.Add(args);

        await engine.StartAsync(task.Id);

        var request = Assert.Single(visualRequests);
        Assert.Equal(120, request.Point.X);
        Assert.Equal(260, request.Point.Y);
        Assert.Equal(ClickType.RightSingle, request.ClickType);
    }

    private sealed class InMemoryClickTaskRepository : IClickTaskRepository
    {
        private readonly Dictionary<Guid, ClickTask> tasks;

        public InMemoryClickTaskRepository(params ClickTask[] tasks)
        {
            this.tasks = tasks.ToDictionary(task => task.Id);
        }

        public Task<IReadOnlyList<ClickTask>> GetAllAsync(CancellationToken cancellationToken = default)
        {
            return Task.FromResult<IReadOnlyList<ClickTask>>([.. tasks.Values]);
        }

        public Task<ClickTask?> GetByIdAsync(Guid taskId, CancellationToken cancellationToken = default)
        {
            tasks.TryGetValue(taskId, out var task);
            return Task.FromResult(task);
        }

        public Task SaveAsync(ClickTask task, CancellationToken cancellationToken = default)
        {
            tasks[task.Id] = task;
            return Task.CompletedTask;
        }

        public Task DeleteAsync(Guid taskId, CancellationToken cancellationToken = default)
        {
            tasks.Remove(taskId);
            return Task.CompletedTask;
        }
    }

    private sealed class InMemoryExecutionLogRepository : IExecutionLogRepository
    {
        public List<ExecutionLog> Logs { get; } = [];

        public Task AddAsync(ExecutionLog log, CancellationToken cancellationToken = default)
        {
            Logs.Add(log);
            return Task.CompletedTask;
        }

        public Task<IReadOnlyList<ExecutionLog>> GetRecentAsync(int limit, CancellationToken cancellationToken = default)
        {
            return Task.FromResult<IReadOnlyList<ExecutionLog>>(Logs.Take(limit).ToList());
        }
    }

    private sealed class BlockingMouseClickService : IMouseClickService
    {
        public TaskCompletionSource ClickStarted { get; } =
            new(TaskCreationOptions.RunContinuationsAsynchronously);

        public async Task ClickAsync(
            ScreenPoint point,
            ClickType clickType,
            CancellationToken cancellationToken = default)
        {
            ClickStarted.TrySetResult();
            await Task.Delay(Timeout.Infinite, cancellationToken);
        }
    }

    private sealed class NoopMouseClickService : IMouseClickService
    {
        public Task ClickAsync(
            ScreenPoint point,
            ClickType clickType,
            CancellationToken cancellationToken = default)
        {
            return Task.CompletedTask;
        }
    }

    private sealed class NoopKeyboardInputService : IKeyboardInputService
    {
        public Task PressKeyAsync(
            string keyName,
            int pressCount,
            int intervalMs,
            CancellationToken cancellationToken = default)
        {
            return Task.CompletedTask;
        }

        public Task PressShortcutAsync(
            string shortcutKeys,
            CancellationToken cancellationToken = default)
        {
            return Task.CompletedTask;
        }

        public Task TypeTextAsync(
            string text,
            int intervalMs,
            CancellationToken cancellationToken = default)
        {
            return Task.CompletedTask;
        }
    }

    private sealed class RecordingKeyboardInputService : IKeyboardInputService
    {
        public List<string> Actions { get; } = [];

        public Task PressKeyAsync(
            string keyName,
            int pressCount,
            int intervalMs,
            CancellationToken cancellationToken = default)
        {
            Actions.Add($"Key:{keyName}:{pressCount}:{intervalMs}");
            return Task.CompletedTask;
        }

        public Task PressShortcutAsync(
            string shortcutKeys,
            CancellationToken cancellationToken = default)
        {
            Actions.Add($"Shortcut:{shortcutKeys}");
            return Task.CompletedTask;
        }

        public Task TypeTextAsync(
            string text,
            int intervalMs,
            CancellationToken cancellationToken = default)
        {
            Actions.Add($"Text:{text}:{intervalMs}");
            return Task.CompletedTask;
        }
    }
}
