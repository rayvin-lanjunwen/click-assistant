using System.Collections.ObjectModel;
using System.Windows;
using ClickAssistant.App.Commands;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Application.Services;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;

namespace ClickAssistant.App.ViewModels;

/// <summary>
/// 主窗口 ViewModel，负责任务列表、步骤编辑、执行控制和日志展示。
/// </summary>
public sealed class MainWindowViewModel : ObservableObject
{
    private readonly ClickTaskService taskService;
    private readonly IClickExecutionEngine executionEngine;
    private readonly ICursorPositionService cursorPositionService;
    private readonly IExecutionLogRepository executionLogRepository;
    private ClickTask? selectedTask;
    private ClickStep? selectedStep;
    private ObservableCollection<ClickStep> currentSteps = [];
    private string statusText = "空闲";
    private string hotkeyStatus = "全局停止快捷键未注册";
    private string stopHotkeyInput = "Ctrl + Alt + S";
    private string executionSafetyText = "启动前请确认坐标、按键、重复次数和开始延迟。";
    private bool isTaskEditorEnabled = true;
    private bool isBusy;

    public MainWindowViewModel(
        ClickTaskService taskService,
        IClickExecutionEngine executionEngine,
        ICursorPositionService cursorPositionService,
        IExecutionLogRepository executionLogRepository)
    {
        this.taskService = taskService;
        this.executionEngine = executionEngine;
        this.cursorPositionService = cursorPositionService;
        this.executionLogRepository = executionLogRepository;

        CreateTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(CreateTaskAsync), CanEdit);
        DuplicateTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(DuplicateTaskAsync), CanUseSelectedTask);
        DeleteTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(DeleteTaskAsync), CanUseSelectedTask);
        SaveTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => SaveSelectedTaskAsync(true)), CanUseSelectedTask);
        AddStepCommand = new RelayCommand(() => AddStep(InputActionType.MouseClick), CanUseSelectedTask);
        AddKeyboardStepCommand = new RelayCommand(() => AddStep(InputActionType.KeyboardPress), CanUseSelectedTask);
        RemoveStepCommand = new RelayCommand(RemoveStep, () => CanEdit() && SelectedStep is not null);
        CaptureCoordinateCommand = new RelayCommand(CaptureCoordinate, () => CanEdit() && SelectedStep is not null);
        StartCommand = new AsyncRelayCommand(() => RunSafelyAsync(StartAsync), CanStart);
        PauseCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => executionEngine.PauseAsync()), CanPause);
        ResumeCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => executionEngine.ResumeAsync()), CanResume);
        StopCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => executionEngine.StopAsync()), CanStop);
        SaveStopHotkeyCommand = new AsyncRelayCommand(() => RunSafelyAsync(SaveStopHotkeyAsync), CanEdit);

        executionEngine.StatusChanged += HandleExecutionStatusChanged;
        executionEngine.LogReceived += HandleExecutionLogReceived;
    }

    public event EventHandler<string>? StopHotkeyChangeRequested;

    public ObservableCollection<ClickTask> Tasks { get; } = [];

    public ObservableCollection<string> RuntimeLogs { get; } = [];

    public ObservableCollection<string> RecentLogs { get; } = [];

    public IReadOnlyList<ClickTypeOption> ClickTypeOptions { get; } =
    [
        new ClickTypeOption(ClickType.LeftSingle, "左键单击"),
        new ClickTypeOption(ClickType.LeftDouble, "左键双击"),
        new ClickTypeOption(ClickType.RightSingle, "右键单击")
    ];

    public IReadOnlyList<InputActionTypeOption> InputActionTypeOptions { get; } =
    [
        new InputActionTypeOption(InputActionType.MouseClick, "鼠标点击"),
        new InputActionTypeOption(InputActionType.KeyboardPress, "键盘按键")
    ];

    public AsyncRelayCommand CreateTaskCommand { get; }

    public AsyncRelayCommand DuplicateTaskCommand { get; }

    public AsyncRelayCommand DeleteTaskCommand { get; }

    public AsyncRelayCommand SaveTaskCommand { get; }

    public RelayCommand AddStepCommand { get; }

    public RelayCommand AddKeyboardStepCommand { get; }

    public RelayCommand RemoveStepCommand { get; }

    public RelayCommand CaptureCoordinateCommand { get; }

    public AsyncRelayCommand StartCommand { get; }

    public AsyncRelayCommand PauseCommand { get; }

    public AsyncRelayCommand ResumeCommand { get; }

    public AsyncRelayCommand StopCommand { get; }

    public AsyncRelayCommand SaveStopHotkeyCommand { get; }

    public ClickTask? SelectedTask
    {
        get => selectedTask;
        set
        {
            if (SetProperty(ref selectedTask, value))
            {
                LoadCurrentSteps(value);
                NotifyCommandStates();
            }
        }
    }

    public ClickStep? SelectedStep
    {
        get => selectedStep;
        set
        {
            if (SetProperty(ref selectedStep, value))
            {
                NotifyCommandStates();
            }
        }
    }

    public ObservableCollection<ClickStep> CurrentSteps
    {
        get => currentSteps;
        private set
        {
            if (SetProperty(ref currentSteps, value))
            {
                SelectedStep = CurrentSteps.FirstOrDefault();
            }
        }
    }

    public string StatusText
    {
        get => statusText;
        private set => SetProperty(ref statusText, value);
    }

    public string HotkeyStatus
    {
        get => hotkeyStatus;
        private set => SetProperty(ref hotkeyStatus, value);
    }

    public string StopHotkeyInput
    {
        get => stopHotkeyInput;
        set => SetProperty(ref stopHotkeyInput, value);
    }

    public string ExecutionSafetyText
    {
        get => executionSafetyText;
        private set => SetProperty(ref executionSafetyText, value);
    }

    public bool IsTaskEditorEnabled
    {
        get => isTaskEditorEnabled;
        private set => SetProperty(ref isTaskEditorEnabled, value);
    }

    public bool IsBusy
    {
        get => isBusy;
        private set
        {
            if (SetProperty(ref isBusy, value))
            {
                NotifyCommandStates();
            }
        }
    }

    /// <summary>
    /// 更新全局快捷键注册状态，供界面展示。
    /// </summary>
    public void SetHotkeyStatus(string message)
    {
        HotkeyStatus = message;
        AddRuntimeLog(message);
    }

    /// <summary>
    /// 设置全局停止快捷键输入框内容，通常来自持久化设置。
    /// </summary>
    public void SetStopHotkeyInput(string hotkeyText)
    {
        StopHotkeyInput = hotkeyText;
    }

    /// <summary>
    /// 应用快捷键注册结果，并把系统反馈展示给用户。
    /// </summary>
    public void ApplyHotkeyRegistrationResult(HotkeyRegistrationResult result)
    {
        StopHotkeyInput = result.HotkeyText;
        SetHotkeyStatus(result.Message);
    }

    /// <summary>
    /// 响应全局立即停止快捷键。
    /// </summary>
    public async Task RequestStopFromHotkeyAsync()
    {
        AddRuntimeLog("收到全局停止快捷键，正在请求停止任务。");
        await executionEngine.StopAsync();
    }

    /// <summary>
    /// 初始化界面数据，首次运行时创建一个可编辑的默认任务。
    /// </summary>
    public async Task InitializeAsync()
    {
        await ReloadTasksAsync();
        await LoadRecentLogsAsync();

        if (Tasks.Count == 0)
        {
            await CreateTaskAsync();
        }
    }

    /// <summary>
    /// 读取全部任务，并尽量保持当前选中项。
    /// </summary>
    private async Task ReloadTasksAsync(Guid? selectedTaskId = null)
    {
        IsBusy = true;

        try
        {
            var tasks = await taskService.GetAllAsync();
            var targetId = selectedTaskId ?? SelectedTask?.Id;

            Tasks.Clear();
            foreach (var task in tasks)
            {
                Tasks.Add(task);
            }

            SelectedTask = targetId.HasValue
                ? Tasks.FirstOrDefault(task => task.Id == targetId.Value) ?? Tasks.FirstOrDefault()
                : Tasks.FirstOrDefault();
        }
        finally
        {
            IsBusy = false;
        }
    }

    /// <summary>
    /// 新建任务并写入数据库，确保用户打开后可以直接编辑。
    /// </summary>
    private async Task CreateTaskAsync()
    {
        var task = new ClickTask
        {
            Name = $"点击任务 {Tasks.Count + 1}",
            Description = "用于记录一组鼠标点击或键盘连按步骤。",
            RepeatCount = 1,
            StartDelayMs = 3000
        };

        task.Steps.Add(new ClickStep
        {
            TaskId = task.Id,
            Name = "步骤 1",
            Order = 0,
            AfterDelayMs = 500
        });

        await taskService.SaveAsync(task);
        await ReloadTasksAsync(task.Id);
    }

    /// <summary>
    /// 复制当前任务，便于基于已有配置快速创建新任务。
    /// </summary>
    private async Task DuplicateTaskAsync()
    {
        if (SelectedTask is null)
        {
            return;
        }

        ApplyCurrentStepsToTask();
        var duplicatedTask = await taskService.DuplicateAsync(SelectedTask);
        await ReloadTasksAsync(duplicatedTask.Id);
    }

    /// <summary>
    /// 删除当前任务，并在删除前向用户确认。
    /// </summary>
    private async Task DeleteTaskAsync()
    {
        if (SelectedTask is null)
        {
            return;
        }

        var result = MessageBox.Show(
            $"确认删除任务“{SelectedTask.Name}”吗？",
            "删除任务",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);

        if (result != MessageBoxResult.Yes)
        {
            return;
        }

        await taskService.DeleteAsync(SelectedTask.Id);
        await ReloadTasksAsync();
    }

    /// <summary>
    /// 保存当前任务，并可选重新读取数据库数据。
    /// </summary>
    private async Task SaveSelectedTaskAsync(bool reloadAfterSave)
    {
        if (SelectedTask is null)
        {
            return;
        }

        ApplyCurrentStepsToTask();
        await taskService.SaveAsync(SelectedTask);
        AddRuntimeLog("任务配置已保存。");

        if (reloadAfterSave)
        {
            await ReloadTasksAsync(SelectedTask.Id);
        }
    }

    /// <summary>
    /// 新增一个输入步骤，动作类型由按钮决定。
    /// </summary>
    private void AddStep(InputActionType actionType)
    {
        if (SelectedTask is null)
        {
            return;
        }

        var step = new ClickStep
        {
            TaskId = SelectedTask.Id,
            Name = actionType == InputActionType.KeyboardPress
                ? $"键盘步骤 {CurrentSteps.Count + 1}"
                : $"鼠标步骤 {CurrentSteps.Count + 1}",
            ActionType = actionType,
            Order = CurrentSteps.Count,
            AfterDelayMs = 500
        };

        CurrentSteps.Add(step);
        SelectedStep = step;
    }

    /// <summary>
    /// 移除当前选中的输入步骤，并让剩余步骤保存时重新排序。
    /// </summary>
    private void RemoveStep()
    {
        if (SelectedStep is null)
        {
            return;
        }

        var nextIndex = Math.Max(0, CurrentSteps.IndexOf(SelectedStep) - 1);
        CurrentSteps.Remove(SelectedStep);
        SelectedStep = CurrentSteps.ElementAtOrDefault(nextIndex);
    }

    /// <summary>
    /// 捕获当前鼠标位置，写入选中步骤的屏幕绝对坐标。
    /// </summary>
    private void CaptureCoordinate()
    {
        if (SelectedStep is null)
        {
            return;
        }

        if (SelectedStep.ActionType != InputActionType.MouseClick)
        {
            AddRuntimeLog("当前步骤是键盘按键，不需要捕获鼠标坐标。");
            return;
        }

        var point = cursorPositionService.GetCurrentPosition();
        SelectedStep.X = point.X;
        SelectedStep.Y = point.Y;

        var selectedIndex = CurrentSteps.IndexOf(SelectedStep);
        if (selectedIndex >= 0)
        {
            CurrentSteps[selectedIndex] = SelectedStep;
        }

        AddRuntimeLog($"已捕获坐标：X={point.X}，Y={point.Y}。");
    }

    /// <summary>
    /// 保存当前配置后启动执行引擎。
    /// </summary>
    private async Task StartAsync()
    {
        if (SelectedTask is null)
        {
            return;
        }

        ApplyCurrentStepsToTask();
        SelectedTask.ValidateForExecution();

        if (!ConfirmStartExecution())
        {
            AddRuntimeLog("已取消启动任务。");
            return;
        }

        await taskService.SaveAsync(SelectedTask);
        AddRuntimeLog("任务配置已保存。");
        await executionEngine.StartAsync(SelectedTask.Id);
        await LoadRecentLogsAsync();
    }

    /// <summary>
    /// 请求窗口层重新注册并保存全局停止快捷键。
    /// </summary>
    private Task SaveStopHotkeyAsync()
    {
        if (string.IsNullOrWhiteSpace(StopHotkeyInput))
        {
            MessageBox.Show("请填写停止快捷键。", "快捷键设置", MessageBoxButton.OK, MessageBoxImage.Warning);
            return Task.CompletedTask;
        }

        StopHotkeyChangeRequested?.Invoke(this, StopHotkeyInput.Trim());
        return Task.CompletedTask;
    }

    /// <summary>
    /// 执行真实输入前向用户确认，降低误点击和误输入风险。
    /// </summary>
    private bool ConfirmStartExecution()
    {
        if (SelectedTask is null)
        {
            return false;
        }

        var enabledSteps = SelectedTask.Steps.Count(step => step.Enabled);
        var mouseSteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.MouseClick);
        var keyboardSteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.KeyboardPress);

        var result = MessageBox.Show(
            $"即将执行任务“{SelectedTask.Name}”。\n\n" +
            $"重复次数：{SelectedTask.RepeatCount}\n" +
            $"启用步骤：{enabledSteps} 个（鼠标 {mouseSteps} 个，键盘 {keyboardSteps} 个）\n" +
            $"开始延迟：{SelectedTask.StartDelayMs} 毫秒\n" +
            $"停止方式：点击停止按钮，或使用 {StopHotkeyInput}。\n\n" +
            "执行期间会产生真实鼠标点击或键盘输入，确认启动吗？",
            "执行前确认",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);

        return result == MessageBoxResult.Yes;
    }

    /// <summary>
    /// 将界面步骤集合写回领域任务，保存和复制前统一调用。
    /// </summary>
    private void ApplyCurrentStepsToTask()
    {
        if (SelectedTask is null)
        {
            return;
        }

        SelectedTask.Steps = CurrentSteps
            .Select((step, index) =>
            {
                step.TaskId = SelectedTask.Id;
                step.Order = index;
                return step;
            })
            .ToList();
    }

    /// <summary>
    /// 根据当前任务刷新步骤集合。
    /// </summary>
    private void LoadCurrentSteps(ClickTask? task)
    {
        CurrentSteps = task is null
            ? []
            : new ObservableCollection<ClickStep>(task.Steps.OrderBy(step => step.Order));
    }

    /// <summary>
    /// 读取最近执行结果，显示在右侧日志区域。
    /// </summary>
    private async Task LoadRecentLogsAsync()
    {
        var logs = await executionLogRepository.GetRecentAsync(20);

        RecentLogs.Clear();
        foreach (var log in logs)
        {
            var endedAt = log.EndedAt?.ToString("HH:mm:ss") ?? "未结束";
            RecentLogs.Add($"{log.StartedAt:MM-dd HH:mm:ss} - {endedAt} | {log.Status} | {log.Message}");
        }
    }

    /// <summary>
    /// 捕获命令异常并用中文提示，避免异步命令错误静默失败。
    /// </summary>
    private async Task RunSafelyAsync(Func<Task> action)
    {
        try
        {
            await action();
        }
        catch (Exception exception)
        {
            MessageBox.Show(exception.Message, "操作失败", MessageBoxButton.OK, MessageBoxImage.Error);
            AddRuntimeLog($"操作失败：{exception.Message}");
        }
    }

    /// <summary>
    /// 响应执行状态变化，并刷新按钮状态。
    /// </summary>
    private void HandleExecutionStatusChanged(object? sender, ExecutionStatus status)
    {
        RunOnUiThread(() =>
        {
            StatusText = ToStatusText(status);
            ExecutionSafetyText = ToExecutionSafetyText(status);
            NotifyCommandStates();
        });
    }

    /// <summary>
    /// 接收执行引擎实时日志。
    /// </summary>
    private void HandleExecutionLogReceived(object? sender, string message)
    {
        RunOnUiThread(() => AddRuntimeLog(message));
    }

    /// <summary>
    /// 添加实时日志并限制列表长度，避免长时间运行后界面过重。
    /// </summary>
    private void AddRuntimeLog(string message)
    {
        RuntimeLogs.Insert(0, message);

        while (RuntimeLogs.Count > 120)
        {
            RuntimeLogs.RemoveAt(RuntimeLogs.Count - 1);
        }
    }

    /// <summary>
    /// 确保后台执行事件回到 UI 线程后再更新界面集合。
    /// </summary>
    private static void RunOnUiThread(Action action)
    {
        var dispatcher = System.Windows.Application.Current?.Dispatcher;

        if (dispatcher is null || dispatcher.CheckAccess())
        {
            action();
            return;
        }

        dispatcher.Invoke(action);
    }

    /// <summary>
    /// 集中刷新所有命令的可用状态。
    /// </summary>
    private void NotifyCommandStates()
    {
        IsTaskEditorEnabled = CanEdit();
        CreateTaskCommand.NotifyCanExecuteChanged();
        DuplicateTaskCommand.NotifyCanExecuteChanged();
        DeleteTaskCommand.NotifyCanExecuteChanged();
        SaveTaskCommand.NotifyCanExecuteChanged();
        AddStepCommand.NotifyCanExecuteChanged();
        AddKeyboardStepCommand.NotifyCanExecuteChanged();
        RemoveStepCommand.NotifyCanExecuteChanged();
        CaptureCoordinateCommand.NotifyCanExecuteChanged();
        StartCommand.NotifyCanExecuteChanged();
        PauseCommand.NotifyCanExecuteChanged();
        ResumeCommand.NotifyCanExecuteChanged();
        StopCommand.NotifyCanExecuteChanged();
        SaveStopHotkeyCommand.NotifyCanExecuteChanged();
    }

    /// <summary>
    /// 判断当前是否允许编辑任务配置。
    /// </summary>
    private bool CanEdit()
    {
        return !IsBusy && executionEngine.Status is not ExecutionStatus.Running and not ExecutionStatus.Paused and not ExecutionStatus.Starting;
    }

    /// <summary>
    /// 判断当前是否存在可操作的选中任务。
    /// </summary>
    private bool CanUseSelectedTask()
    {
        return CanEdit() && SelectedTask is not null;
    }

    /// <summary>
    /// 只有存在选中任务且执行引擎处于终止态时才允许启动。
    /// </summary>
    private bool CanStart()
    {
        return SelectedTask is not null && executionEngine.Status is (ExecutionStatus.Idle
            or ExecutionStatus.Completed
            or ExecutionStatus.Stopped
            or ExecutionStatus.Failed);
    }

    /// <summary>
    /// 运行态允许暂停。
    /// </summary>
    private bool CanPause()
    {
        return executionEngine.Status == ExecutionStatus.Running;
    }

    /// <summary>
    /// 暂停态允许继续。
    /// </summary>
    private bool CanResume()
    {
        return executionEngine.Status == ExecutionStatus.Paused;
    }

    /// <summary>
    /// 启动中、运行中和暂停态允许停止。
    /// </summary>
    private bool CanStop()
    {
        return executionEngine.Status is ExecutionStatus.Running or ExecutionStatus.Paused or ExecutionStatus.Starting;
    }

    /// <summary>
    /// 将领域状态转换为中文界面文案。
    /// </summary>
    private static string ToStatusText(ExecutionStatus status)
    {
        return status switch
        {
            ExecutionStatus.Idle => "空闲",
            ExecutionStatus.Starting => "启动中",
            ExecutionStatus.Running => "运行中",
            ExecutionStatus.Paused => "已暂停",
            ExecutionStatus.Completed => "已完成",
            ExecutionStatus.Stopped => "已停止",
            ExecutionStatus.Failed => "执行失败",
            _ => status.ToString()
        };
    }

    /// <summary>
    /// 将执行状态转换为安全提示文案。
    /// </summary>
    private static string ToExecutionSafetyText(ExecutionStatus status)
    {
        return status switch
        {
            ExecutionStatus.Starting => "任务正在启动，配置已锁定，可使用停止按钮或全局快捷键中止。",
            ExecutionStatus.Running => "任务正在执行，配置已锁定，可使用停止按钮或全局快捷键中止。",
            ExecutionStatus.Paused => "任务已暂停，配置仍被锁定，可继续或停止。",
            _ => "启动前请确认坐标、按键、重复次数和开始延迟。"
        };
    }
}
