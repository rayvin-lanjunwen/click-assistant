using System.Collections.ObjectModel;
using ClickAssistant.App.Commands;
using ClickAssistant.App.Helpers;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Application.Services;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.App.ViewModels;

/// <summary>
/// 主窗口 ViewModel，负责任务列表、步骤编辑、执行控制和日志展示。
/// </summary>
public sealed class MainWindowViewModel : ObservableObject, IDisposable
{
    private readonly ClickTaskService taskService;
    private readonly IClickExecutionEngine executionEngine;
    private readonly IMouseClickService mouseClickService;
    private readonly IExecutionLogRepository executionLogRepository;
    private readonly IDialogService dialogService;
    private ClickTask? selectedTask;
    private ClickStep? selectedStep;
    private ObservableCollection<ClickStep> currentSteps = [];
    private string statusText = "空闲";
    private string hotkeyStatus = "全局停止快捷键未注册";
    private string keyboardCaptureStatus = "点击按键框后，按下键盘上的一个键。";
    private string coordinateCaptureStatus = "点击“选择坐标”后移动鼠标，左键确认位置，Esc 取消。";
    private string stopHotkeyInput = "Ctrl + Alt + S";
    private string executionSafetyText = "启动前请确认坐标、按键、执行轮数、点击次数和开始延迟。";
    private bool isTaskEditorEnabled = true;
    private int taskLibrarySelectedTabIndex;
    private bool isHomePageVisible = true;
    private bool isNewTaskTypePageVisible;
    private bool isTaskLibraryPageVisible;
    private bool isExecutionLogsPageVisible;
    private bool isMouseClickEditorPageVisible;
    private bool isFloatingWindowVisible;
    private bool isFloatingExpandedVisible;
    private bool isFloatingCollapsedVisible = true;
    private bool isFloatingWindowExpanded;
    private bool isCoordinateCapturePending;
    private bool isBusy;

    public MainWindowViewModel(
        ClickTaskService taskService,
        IClickExecutionEngine executionEngine,
        IMouseClickService mouseClickService,
        IExecutionLogRepository executionLogRepository,
        IDialogService dialogService)
    {
        this.taskService = taskService;
        this.executionEngine = executionEngine;
        this.mouseClickService = mouseClickService;
        this.executionLogRepository = executionLogRepository;
        this.dialogService = dialogService;

        ShowHomeCommand = new RelayCommand(ShowHome);
        ShowNewTaskTypeCommand = new RelayCommand(ShowNewTaskType);
        ShowTaskLibraryCommand = new RelayCommand(ShowTaskLibrary);
        ShowExecutionLogsCommand = new AsyncRelayCommand(() => RunSafelyAsync(ShowExecutionLogsAsync));
        ShowMouseClickEditorCommand = new RelayCommand(ShowMouseClickEditor, CanOpenMouseClickEditor);
        CreateMouseClickTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(CreateMouseClickTaskAsync), CanEdit);
        CreateKeyboardTaskCommand = new AsyncRelayCommand(
            () => RunSafelyAsync(() => CreateTypedTaskAsync(InputActionType.KeyboardPress)),
            CanEdit);
        CreateTextInputTaskCommand = new AsyncRelayCommand(
            () => RunSafelyAsync(() => CreateTypedTaskAsync(InputActionType.TextInput)),
            CanEdit);
        CreateComboTaskCommand = new AsyncRelayCommand(
            () => RunSafelyAsync(() => CreateTypedTaskAsync(InputActionType.MouseClick, InputActionType.KeyboardPress)),
            CanEdit);
        CreateTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(CreateTaskAsync), CanEdit);
        DuplicateTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(DuplicateTaskAsync), CanUseSelectedTask);
        DeleteTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(DeleteTaskAsync), CanUseSelectedTask);
        SaveTaskCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => SaveSelectedTaskAsync(true)), CanUseSelectedTask);
        AddStepCommand = new RelayCommand(() => AddStep(InputActionType.MouseClick), CanUseSelectedTask);
        AddKeyboardStepCommand = new RelayCommand(() => AddStep(InputActionType.KeyboardPress), CanUseSelectedTask);
        AddShortcutStepCommand = new RelayCommand(() => AddStep(InputActionType.KeyboardShortcut), CanUseSelectedTask);
        AddTextInputStepCommand = new RelayCommand(() => AddStep(InputActionType.TextInput), CanUseSelectedTask);
        RemoveStepCommand = new RelayCommand(RemoveStep, () => CanEdit() && SelectedStep is not null);
        CaptureCoordinateCommand = new RelayCommand(RequestCoordinateSelection, CanCaptureCoordinate);
        TestMouseClickCommand = new AsyncRelayCommand(() => RunSafelyAsync(TestMouseClickOnceAsync), CanUseSelectedMouseStep);
        StartCommand = new AsyncRelayCommand(() => RunSafelyAsync(StartAsync), CanStart);
        PauseCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => executionEngine.PauseAsync()), CanPause);
        ResumeCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => executionEngine.ResumeAsync()), CanResume);
        StopCommand = new AsyncRelayCommand(() => RunSafelyAsync(() => executionEngine.StopAsync()), CanStop);
        SaveStopHotkeyCommand = new AsyncRelayCommand(() => RunSafelyAsync(SaveStopHotkeyAsync), CanEdit);
        ToggleFloatingWindowCommand = new RelayCommand(ToggleFloatingWindow);

        executionEngine.StatusChanged += HandleExecutionStatusChanged;
        executionEngine.LogReceived += HandleExecutionLogReceived;
    }

    public event EventHandler<string>? StopHotkeyChangeRequested;

    public event EventHandler? CoordinateSelectionRequested;

    public ObservableCollection<ClickTask> Tasks { get; } = [];

    /// <summary>
    /// 最近任务（取前 3 个），供首页快速执行卡片使用。
    /// </summary>
    public IEnumerable<ClickTask> RecentTasks => Tasks.Take(3);

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
        new InputActionTypeOption(InputActionType.KeyboardPress, "键盘按键"),
        new InputActionTypeOption(InputActionType.KeyboardShortcut, "组合键"),
        new InputActionTypeOption(InputActionType.TextInput, "文本输入"),
        new InputActionTypeOption(InputActionType.Swipe, "滑动")
    ];

    public RelayCommand ShowHomeCommand { get; }

    public RelayCommand ShowNewTaskTypeCommand { get; }

    public RelayCommand ShowTaskLibraryCommand { get; }

    public AsyncRelayCommand ShowExecutionLogsCommand { get; }

    public RelayCommand ShowMouseClickEditorCommand { get; }

    public AsyncRelayCommand CreateMouseClickTaskCommand { get; }

    public AsyncRelayCommand CreateKeyboardTaskCommand { get; }

    public AsyncRelayCommand CreateTextInputTaskCommand { get; }

    public AsyncRelayCommand CreateComboTaskCommand { get; }

    public AsyncRelayCommand CreateTaskCommand { get; }

    public AsyncRelayCommand DuplicateTaskCommand { get; }

    public AsyncRelayCommand DeleteTaskCommand { get; }

    public AsyncRelayCommand SaveTaskCommand { get; }

    public RelayCommand AddStepCommand { get; }

    public RelayCommand AddKeyboardStepCommand { get; }

    public RelayCommand AddShortcutStepCommand { get; }

    public RelayCommand AddTextInputStepCommand { get; }

    public RelayCommand RemoveStepCommand { get; }

    public RelayCommand CaptureCoordinateCommand { get; }

    public AsyncRelayCommand TestMouseClickCommand { get; }

    public AsyncRelayCommand StartCommand { get; }

    public AsyncRelayCommand PauseCommand { get; }

    public AsyncRelayCommand ResumeCommand { get; }

    public AsyncRelayCommand StopCommand { get; }

    public AsyncRelayCommand SaveStopHotkeyCommand { get; }

    public RelayCommand ToggleFloatingWindowCommand { get; }

    public ClickTask? SelectedTask
    {
        get => selectedTask;
        set
        {
            if (SetProperty(ref selectedTask, value))
            {
                LoadCurrentSteps(value);
                NotifyEditorDerivedProperties();
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
                RefreshKeyboardCaptureStatus();
                NotifyEditorDerivedProperties();
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

    public IEnumerable<ClickStep> MouseSteps => CurrentSteps
        .Where(step => step.ActionType == InputActionType.MouseClick)
        .OrderBy(step => step.Order);

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

    public string KeyboardCaptureStatus
    {
        get => keyboardCaptureStatus;
        private set => SetProperty(ref keyboardCaptureStatus, value);
    }

    public string VersionText { get; } = "v" +
        (System.Reflection.Assembly.GetExecutingAssembly()
            .GetName().Version?.ToString(3) ?? "0.0.0");

    public bool IsExecutionRunning => executionEngine.Status
        is ExecutionStatus.Running or ExecutionStatus.Paused or ExecutionStatus.Starting;

    public bool IsExecutionPaused => executionEngine.Status == ExecutionStatus.Paused;
    public bool IsNotExecutionRunning => !IsExecutionRunning;

    public string CoordinateCaptureStatus
    {
        get => coordinateCaptureStatus;
        private set
        {
            if (SetProperty(ref coordinateCaptureStatus, value))
                OnPropertyChanged(nameof(LiveCursorPosition));
        }
    }

    /// <summary>
    /// 实时光标屏幕坐标（选择坐标时动态更新）。
    /// </summary>
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool GetCursorPos(out POINT lpPoint);

    private struct POINT { public int X; public int Y; }

    public string LiveCursorPosition
    {
        get
        {
            if (!isCoordinateCapturePending) return "X=— , Y=—";
            if (GetCursorPos(out var pt))
                return $"X={pt.X} , Y={pt.Y}";
            return "X=— , Y=—";
        }
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

    public int TaskLibrarySelectedTabIndex
    {
        get => taskLibrarySelectedTabIndex;
        set => SetProperty(ref taskLibrarySelectedTabIndex, value);
    }

    public bool IsHomePageVisible
    {
        get => isHomePageVisible;
        private set => SetProperty(ref isHomePageVisible, value);
    }

    public bool IsNewTaskTypePageVisible
    {
        get => isNewTaskTypePageVisible;
        private set => SetProperty(ref isNewTaskTypePageVisible, value);
    }

    public bool IsTaskLibraryPageVisible
    {
        get => isTaskLibraryPageVisible;
        private set => SetProperty(ref isTaskLibraryPageVisible, value);
    }

    public bool IsExecutionLogsPageVisible
    {
        get => isExecutionLogsPageVisible;
        private set => SetProperty(ref isExecutionLogsPageVisible, value);
    }

    public bool IsMouseClickEditorPageVisible
    {
        get => isMouseClickEditorPageVisible;
        private set => SetProperty(ref isMouseClickEditorPageVisible, value);
    }

    public bool IsFloatingWindowVisible
    {
        get => isFloatingWindowVisible;
        private set => SetProperty(ref isFloatingWindowVisible, value);
    }

    public bool IsFloatingExpandedVisible
    {
        get => isFloatingExpandedVisible;
        private set => SetProperty(ref isFloatingExpandedVisible, value);
    }

    public bool IsFloatingCollapsedVisible
    {
        get => isFloatingCollapsedVisible;
        private set => SetProperty(ref isFloatingCollapsedVisible, value);
    }

    public string MouseTaskName
    {
        get => SelectedTask?.Name ?? string.Empty;
        set
        {
            if (SelectedTask is null || SelectedTask.Name == value)
            {
                return;
            }

            SelectedTask.Name = value;
            NotifyTaskSummaryGroup();
        }
    }

    public string MouseTaskDescription
    {
        get => SelectedTask?.Description ?? string.Empty;
        set
        {
            if (SelectedTask is null || SelectedTask.Description == value)
            {
                return;
            }

            SelectedTask.Description = value;
            NotifyTaskSummaryGroup();
        }
    }

    public int MouseRepeatCount
    {
        get => SelectedTask?.RepeatCount ?? 1;
        set
        {
            if (SelectedTask is null || SelectedTask.RepeatCount == value)
            {
                return;
            }

            SelectedTask.RepeatCount = value;
            NotifyTaskSummaryGroup();
        }
    }

    public int MouseStartDelayMs
    {
        get => SelectedTask?.StartDelayMs ?? 0;
        set
        {
            if (SelectedTask is null || SelectedTask.StartDelayMs == value)
            {
                return;
            }

            SelectedTask.StartDelayMs = value;
            NotifyTaskSummaryGroup();
        }
    }

    public int MouseX
    {
        get => GetEditableMouseStep()?.X ?? 0;
        set
        {
            var step = GetEditableMouseStep();
            if (step is null || step.X == value)
            {
                return;
            }

            step.X = value;
            NotifyCoordinateGroup();
        }
    }

    public int MouseY
    {
        get => GetEditableMouseStep()?.Y ?? 0;
        set
        {
            var step = GetEditableMouseStep();
            if (step is null || step.Y == value)
            {
                return;
            }

            step.Y = value;
            NotifyCoordinateGroup();
        }
    }

    public int MouseClickIntervalMs
    {
        get => GetEditableMouseStep()?.ClickIntervalMs ?? 100;
        set
        {
            var step = GetEditableMouseStep();
            if (step is null || step.ClickIntervalMs == value)
            {
                return;
            }

            step.ClickIntervalMs = value;
            NotifyMouseGroup();
        }
    }

    public int MouseClickCount
    {
        get => GetEditableMouseStep()?.MouseClickCount ?? 1;
        set
        {
            var step = GetEditableMouseStep();
            if (step is null || step.MouseClickCount == value)
            {
                return;
            }

            step.MouseClickCount = value;
            NotifyMouseGroup();
        }
    }

    public ClickType MouseClickType
    {
        get => GetEditableMouseStep()?.ClickType ?? ClickType.LeftSingle;
        set
        {
            var step = GetEditableMouseStep();
            if (step is null || step.ClickType == value)
            {
                return;
            }

            step.ClickType = value;
            NotifyMouseGroup();
        }
    }

    public string MouseClickTypeText
    {
        get => GetEditableMouseStep()?.ClickType switch
        {
            ClickType.LeftDouble => "左键双击",
            ClickType.RightSingle => "右键单击",
            _ => "左键单击"
        };
    }

    public string MousePositionSummary
    {
        get
        {
            if (SelectedTask is null)
            {
                return "选择鼠标点击任务后，这里会展示位置执行摘要。";
            }

            var mouseSteps = MouseSteps.ToList();
            var enabledMouseSteps = mouseSteps.Where(step => step.Enabled).ToList();
            var clicksPerRound = enabledMouseSteps.Sum(step => step.MouseClickCount);
            var totalClicks = clicksPerRound * SelectedTask.RepeatCount;

            return $"每轮按顺序点击 {enabledMouseSteps.Count}/{mouseSteps.Count} 个位置，共 {clicksPerRound} 次；" +
                $"执行 {SelectedTask.RepeatCount} 轮，总计 {totalClicks} 次鼠标点击。";
        }
    }

    public string SelectedStepName
    {
        get => SelectedStep?.Name ?? string.Empty;
        set
        {
            if (SelectedStep is null || SelectedStep.Name == value)
            {
                return;
            }

            SelectedStep.Name = value;
            NotifyStepMetaGroup();
        }
    }

    public bool SelectedStepEnabled
    {
        get => SelectedStep?.Enabled ?? false;
        set
        {
            if (SelectedStep is null || SelectedStep.Enabled == value)
            {
                return;
            }

            SelectedStep.Enabled = value;
            NotifyStepMetaGroup();
        }
    }

    public InputActionType SelectedStepActionType
    {
        get => SelectedStep?.ActionType ?? InputActionType.MouseClick;
        set
        {
            if (SelectedStep is null || SelectedStep.ActionType == value)
            {
                return;
            }

            SelectedStep.ActionType = value;
            ApplyStepDefaults(SelectedStep);
            RefreshKeyboardCaptureStatus();
            NotifyEditorDerivedProperties();
        }
    }

    public int SelectedStepBeforeDelayMs
    {
        get => SelectedStep?.BeforeDelayMs ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.BeforeDelayMs == value)
            {
                return;
            }

            SelectedStep.BeforeDelayMs = value;
            NotifyStepMetaGroup();
        }
    }

    public int SelectedStepClickIntervalMs
    {
        get => SelectedStep?.ClickIntervalMs ?? 100;
        set
        {
            if (SelectedStep is null || SelectedStep.ClickIntervalMs == value)
            {
                return;
            }

            SelectedStep.ClickIntervalMs = value;
            NotifyMouseGroup();
        }
    }

    public int SelectedStepPressDurationMs
    {
        get => SelectedStep?.PressDurationMs ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.PressDurationMs == value)
            {
                return;
            }

            SelectedStep.PressDurationMs = value;
            NotifyMouseGroup();
        }
    }

    public bool SelectedStepAutoFocusBeforeInput
    {
        get => SelectedStep?.AutoFocusBeforeInput ?? false;
        set
        {
            if (SelectedStep is null || SelectedStep.AutoFocusBeforeInput == value)
            {
                return;
            }

            SelectedStep.AutoFocusBeforeInput = value;
            NotifyTextGroup();
        }
    }

    public int SelectedStepX
    {
        get => SelectedStep?.X ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.X == value)
            {
                return;
            }

            SelectedStep.X = value;
            NotifyCoordinateGroup();
        }
    }

    public int SelectedStepY
    {
        get => SelectedStep?.Y ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.Y == value)
            {
                return;
            }

            SelectedStep.Y = value;
            NotifyCoordinateGroup();
        }
    }

    public ClickType SelectedStepClickType
    {
        get => SelectedStep?.ClickType ?? ClickType.LeftSingle;
        set
        {
            if (SelectedStep is null || SelectedStep.ClickType == value)
            {
                return;
            }

            SelectedStep.ClickType = value;
            NotifyMouseGroup();
        }
    }

    public int SelectedStepMouseClickCount
    {
        get => SelectedStep?.MouseClickCount ?? 1;
        set
        {
            if (SelectedStep is null || SelectedStep.MouseClickCount == value)
            {
                return;
            }

            SelectedStep.MouseClickCount = value;
            NotifyMouseGroup();
        }
    }

    public string SelectedStepKeyName
    {
        get => SelectedStep?.KeyName ?? string.Empty;
        set
        {
            if (SelectedStep is null || SelectedStep.KeyName == value)
            {
                return;
            }

            SelectedStep.KeyName = value;
            NotifyKeyboardGroup();
        }
    }

    public int SelectedStepKeyPressCount
    {
        get => SelectedStep?.KeyPressCount ?? 1;
        set
        {
            if (SelectedStep is null || SelectedStep.KeyPressCount == value)
            {
                return;
            }

            SelectedStep.KeyPressCount = value;
            NotifyKeyboardGroup();
        }
    }

    public int SelectedStepKeyIntervalMs
    {
        get => SelectedStep?.KeyIntervalMs ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.KeyIntervalMs == value)
            {
                return;
            }

            SelectedStep.KeyIntervalMs = value;
            NotifyKeyboardGroup();
        }
    }

    public string SelectedStepShortcutKeys
    {
        get => SelectedStep?.ShortcutKeys ?? string.Empty;
        set
        {
            if (SelectedStep is null || SelectedStep.ShortcutKeys == value)
            {
                return;
            }

            SelectedStep.ShortcutKeys = value;
            NotifyShortcutGroup();
        }
    }

    public string SelectedStepTextContent
    {
        get => SelectedStep?.TextContent ?? string.Empty;
        set
        {
            if (SelectedStep is null || SelectedStep.TextContent == value)
            {
                return;
            }

            SelectedStep.TextContent = value;
            NotifyTextGroup();
        }
    }

    public bool IsMouseStepFieldsVisible => SelectedStep?.ActionType == InputActionType.MouseClick;

    public bool IsSwipeStepFieldsVisible => SelectedStep?.ActionType == InputActionType.Swipe;

    public bool IsKeyboardStepFieldsVisible => SelectedStep?.ActionType == InputActionType.KeyboardPress;

    public bool IsShortcutStepFieldsVisible => SelectedStep?.ActionType == InputActionType.KeyboardShortcut;

    public bool IsTextInputStepFieldsVisible => SelectedStep?.ActionType == InputActionType.TextInput;

    // Swipe-specific properties
    public int SelectedStepEndX
    {
        get => SelectedStep?.EndX ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.EndX == value) return;
            SelectedStep.EndX = value;
            NotifySwipeGroup();
        }
    }

    public int SelectedStepEndY
    {
        get => SelectedStep?.EndY ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.EndY == value) return;
            SelectedStep.EndY = value;
            NotifySwipeGroup();
        }
    }

    public int SelectedStepSwipeDurationMs
    {
        get => SelectedStep?.SwipeDurationMs ?? 300;
        set
        {
            if (SelectedStep is null || SelectedStep.SwipeDurationMs == value) return;
            SelectedStep.SwipeDurationMs = value;
            NotifySwipeGroup();
        }
    }

    public string SelectedStepKeyDisplayText => string.IsNullOrWhiteSpace(SelectedStepKeyName)
        ? "当前按键：未设置"
        : $"当前按键：{SelectedStepKeyName}";

    public string ExecutionSummary
    {
        get
        {
            if (SelectedTask is null)
            {
                return "选择任务后，这里会展示执行摘要。";
            }

            var enabledSteps = CurrentSteps.Count(step => step.Enabled);
            var totalSteps = CurrentSteps.Count;
            var mouseSteps = CurrentSteps.Where(step => step.ActionType == InputActionType.MouseClick).ToList();
            var enabledMouseSteps = mouseSteps.Where(step => step.Enabled).ToList();
            var clicksPerRound = enabledMouseSteps.Sum(step => step.MouseClickCount);
            var totalMouseClicks = clicksPerRound * SelectedTask.RepeatCount;

            return $"开始后等待 {TimeFormattingHelper.FormatMilliseconds(SelectedTask.StartDelayMs)}，每轮按顺序执行 {enabledSteps}/{totalSteps} 个启用步骤，" +
                $"其中鼠标位置 {enabledMouseSteps.Count}/{mouseSteps.Count} 个、点击 {clicksPerRound} 次；" +
                $"执行 {SelectedTask.RepeatCount} 轮，总鼠标点击 {totalMouseClicks} 次。";
        }
    }

    public string SelectedTaskMetaText
    {
        get
        {
            if (SelectedTask is null)
            {
                return "未选择任务";
            }

            var stepCount = SelectedTask.Steps.Count;
            var enabledText = SelectedTask.Enabled ? "已启用" : "已禁用";
            return $"{enabledText} · {stepCount} 个步骤 · 更新于 {SelectedTask.UpdatedAt:MM-dd HH:mm}";
        }
    }

    public string FloatingTaskName => SelectedTask?.Name ?? "未选择任务";

    public string FloatingTaskSummary
    {
        get
        {
            if (SelectedTask is null)
            {
                return "打开主窗口选择任务";
            }

            var mouseSteps = CurrentSteps
                .Where(step => step.ActionType == InputActionType.MouseClick)
                .ToList();

            if (mouseSteps.Count == 0)
            {
                return $"{SelectedTask.Steps.Count} 个步骤";
            }

            var clicksPerRound = mouseSteps
                .Where(step => step.Enabled)
                .Sum(step => step.MouseClickCount);

            return $"{mouseSteps.Count} 个位置 · 每轮 {clicksPerRound} 次点击 · {SelectedTask.RepeatCount} 轮";
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

    public void BeginKeyboardCapture()
    {
        var step = GetEditableKeyboardStep();
        if (step is null)
        {
            KeyboardCaptureStatus = "请先选择一个键盘按键步骤。";
            return;
        }

        SelectedStep = step;
        KeyboardCaptureStatus = "正在监听键盘输入，请按一个键。";
    }

    public void EndKeyboardCapture()
    {
        RefreshKeyboardCaptureStatus();
    }

    public void RejectKeyboardCapture(string keyName)
    {
        KeyboardCaptureStatus = $"暂不支持：{keyName}。请按 A-Z、0-9、F1-F24 或常用功能键。";
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
        ShowHome();
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

    private void ShowHome()
    {
        SetCurrentPage("Home");
    }

    private void ShowNewTaskType()
    {
        SetCurrentPage("NewTaskType");
    }

    private void ShowTaskLibrary()
    {
        SetCurrentPage("TaskLibrary");
    }

    private async Task ShowExecutionLogsAsync()
    {
        await LoadRecentLogsAsync();
        SetCurrentPage("ExecutionLogs");
    }

    private void ShowMouseClickEditor()
    {
        if (!CanOpenMouseClickEditor())
        {
            AddRuntimeLog("当前任务暂未提供专属编辑页，请先选择鼠标点击或组合任务。");
            ShowTaskLibrary();
            return;
        }

        EnsureMouseStepSelected();
        SetCurrentPage("MouseClickEditor");
    }

    private void SetCurrentPage(string pageName)
    {
        IsHomePageVisible = pageName == "Home";
        IsNewTaskTypePageVisible = pageName == "NewTaskType";
        IsTaskLibraryPageVisible = pageName == "TaskLibrary";
        IsExecutionLogsPageVisible = pageName == "ExecutionLogs";
        IsMouseClickEditorPageVisible = pageName == "MouseClickEditor";
        NotifyCommandStates();
    }

    private async Task CreateMouseClickTaskAsync()
    {
        var task = CreateBaseTask("鼠标点击任务", "用于固定位置点击、重复点击或连点操作。");
        task.Steps.Add(CreateStep(InputActionType.MouseClick, task.Id, 0));

        await taskService.SaveAsync(task);
        await ReloadTasksAsync(task.Id);
        ShowMouseClickEditor();
        AddRuntimeLog($"已新建任务：{task.Name}。");
    }

    private async Task CreateTypedTaskAsync(params InputActionType[] actionTypes)
    {
        var taskTypeName = actionTypes.Length > 1
            ? "组合任务"
            : ToTaskTypeName(actionTypes.FirstOrDefault(InputActionType.MouseClick));
        var task = CreateBaseTask(taskTypeName, $"用于配置{taskTypeName}。");

        var targetActionTypes = actionTypes.Length == 0
            ? [InputActionType.MouseClick]
            : actionTypes;
        foreach (var actionType in targetActionTypes)
        {
            task.Steps.Add(CreateStep(actionType, task.Id, task.Steps.Count));
        }

        await taskService.SaveAsync(task);
        await ReloadTasksAsync(task.Id);
        SelectFirstStepOfType(targetActionTypes.FirstOrDefault(InputActionType.MouseClick));
        TaskLibrarySelectedTabIndex = 1;
        ShowTaskLibrary();
        AddRuntimeLog($"已新建任务：{task.Name}。");
    }

    /// <summary>
    /// 新建任务并写入数据库，确保用户打开后可以直接编辑。
    /// </summary>
    private async Task CreateTaskAsync()
    {
        await CreateMouseClickTaskAsync();
    }

    private ClickTask CreateBaseTask(string taskTypeName, string description)
    {
        return new ClickTask
        {
            Name = $"{taskTypeName} {Tasks.Count + 1}",
            Description = description,
            RepeatCount = 1,
            StartDelayMs = 1000
        };
    }

    /// <summary>
    /// 从界面按键捕获框写入键盘步骤的按键名称。
    /// </summary>
    public void CaptureKeyboardStepKey(string keyName)
    {
        var step = GetEditableKeyboardStep();
        if (step is null)
        {
            AddRuntimeLog("请先选择一个键盘按键步骤。");
            return;
        }

        SelectedStep = step;
        step.ActionType = InputActionType.KeyboardPress;
        step.KeyName = keyName;
        ApplyStepDefaults(step);
        KeyboardCaptureStatus = $"已捕获：{keyName}。连按时会重复触发这个键。";
        NotifyEditorDerivedProperties();
        AddRuntimeLog($"已设置键盘按键：{keyName}。");
    }

    private static ClickStep CreateStep(InputActionType actionType, Guid taskId, int order)
    {
        var step = new ClickStep
        {
            TaskId = taskId,
            Name = CreateStepName(actionType, order + 1),
            ActionType = actionType,
            Order = order
        };
        ApplyStepDefaults(step);
        return step;
    }

    private static void ApplyStepDefaults(ClickStep step)
    {
        if (step.ActionType == InputActionType.MouseClick && step.MouseClickCount < 1)
        {
            step.MouseClickCount = 1;
        }

        if (step.ActionType == InputActionType.KeyboardPress && step.KeyPressCount < 1)
        {
            step.KeyPressCount = 1;
        }

        if (step.ActionType == InputActionType.KeyboardShortcut && string.IsNullOrWhiteSpace(step.ShortcutKeys))
        {
            step.ShortcutKeys = "Ctrl+C";
        }

        if (step.ActionType == InputActionType.TextInput && string.IsNullOrEmpty(step.TextContent))
        {
            step.TextContent = "示例文本";
        }
    }

    private static string ToTaskTypeName(InputActionType actionType)
    {
        return actionType switch
        {
            InputActionType.KeyboardPress => "键盘按键任务",
            InputActionType.TextInput => "文本输入任务",
            InputActionType.KeyboardShortcut => "组合任务",
            _ => "鼠标点击任务"
        };
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
        ShowTaskLibrary();
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

        if (!dialogService.Confirm($"确认删除任务“{SelectedTask.Name}”吗？", "删除任务"))
        {
            return;
        }

        await taskService.DeleteAsync(SelectedTask.Id);
        await ReloadTasksAsync();
        ShowTaskLibrary();
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
            Name = CreateStepName(actionType, CurrentSteps.Count + 1),
            ActionType = actionType,
            Order = CurrentSteps.Count
        };
        ApplyStepDefaults(step);

        CurrentSteps.Add(step);
        SelectedStep = step;
        NotifyEditorDerivedProperties();
        NotifyCommandStates();
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
        ReorderCurrentSteps();
        SelectedStep = CurrentSteps.ElementAtOrDefault(nextIndex);
        NotifyEditorDerivedProperties();
        NotifyCommandStates();
    }

    /// <summary>
    /// 拖拽排序：将步骤从源索引移到目标索引，然后自动重新编号。
    /// 供代码后置层在 ListBox 拖放操作时调用。
    /// </summary>
    public void MoveStep(int fromIndex, int toIndex)
    {
        if (fromIndex < 0 || fromIndex >= CurrentSteps.Count
            || toIndex < 0 || toIndex >= CurrentSteps.Count)
        {
            return;
        }

        CurrentSteps.Move(fromIndex, toIndex);
        ReorderCurrentSteps();
        NotifyEditorDerivedProperties();
        NotifyCommandStates();
    }

    /// <summary>
    /// 请求窗口层打开坐标选择覆盖层。
    /// </summary>
    private void RequestCoordinateSelection()
    {
        var step = GetEditableMouseStep();
        if (step is null)
        {
            AddRuntimeLog("请先选择一个鼠标点击步骤。");
            return;
        }

        SelectedStep = step;
        isCoordinateCapturePending = true;
        CoordinateCaptureStatus = "正在选择坐标：移动鼠标，左键确认，Esc 取消。";
        NotifyCommandStates();
        CoordinateSelectionRequested?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>
    /// 写入坐标选择覆盖层确认的屏幕绝对坐标。
    /// </summary>
    public void ApplySelectedCoordinate(ScreenPoint point)
    {
        var step = GetEditableMouseStep();
        if (step is null)
        {
            CoordinatePickerClosed(false);
            return;
        }

        step.X = point.X;
        step.Y = point.Y;
        SelectedStep = step;
        step.RaisePropertyChanged(null);

        isCoordinateCapturePending = false;
        CoordinateCaptureStatus = $"已选择坐标：X={point.X}，Y={point.Y}。";
        NotifyEditorDerivedProperties();
        NotifyCommandStates();
        AddRuntimeLog($"已选择坐标：X={point.X}，Y={point.Y}。");
    }

    /// <summary>
    /// 坐标选择器关闭后恢复状态。若未变更坐标，则视为取消。
    /// </summary>
    public void CoordinatePickerClosed(bool coordinateChanged)
    {
        isCoordinateCapturePending = false;
        CoordinateCaptureStatus = coordinateChanged
            ? "已更新坐标，请记得保存任务。"
            : "已取消坐标选择。点击“选择坐标”可重新选择。";
        NotifyCommandStates();
    }

    private async Task TestMouseClickOnceAsync()
    {
        var step = GetEditableMouseStep();
        if (step is null)
        {
            return;
        }

        if (!dialogService.Confirm(
            $"即将在 X={step.X}、Y={step.Y} 执行一次{MouseClickTypeText}。\n\n确认测试点击吗？",
            "测试点击一次"))
        {
            AddRuntimeLog("已取消测试点击。");
            return;
        }

        await mouseClickService.ClickAsync(step.ToPoint(), step.ClickType);
        AddRuntimeLog($"已测试点击一次：X={step.X}，Y={step.Y}。");
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
            dialogService.ShowWarning("请填写停止快捷键。", "快捷键设置");
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
        var enabledMouseSteps = SelectedTask.Steps
            .Where(step => step.Enabled && step.ActionType == InputActionType.MouseClick)
            .ToList();
        var mouseClicksPerRound = enabledMouseSteps.Sum(step => step.MouseClickCount);
        var totalMouseClicks = mouseClicksPerRound * SelectedTask.RepeatCount;
        var keySteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.KeyboardPress);
        var shortcutSteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.KeyboardShortcut);
        var textInputSteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.TextInput);

        return dialogService.Confirm(
            $"即将执行任务“{SelectedTask.Name}”。\n\n" +
            $"执行轮数：{SelectedTask.RepeatCount}\n" +
            $"启用步骤：{enabledSteps} 个（鼠标位置 {enabledMouseSteps.Count} 个，按键 {keySteps} 个，组合键 {shortcutSteps} 个，文本 {textInputSteps} 个）\n" +
            $"鼠标点击：每轮 {mouseClicksPerRound} 次，总计 {totalMouseClicks} 次\n" +
            $"开始延迟：{SelectedTask.StartDelayMs} 毫秒\n" +
            $"停止方式：点击停止按钮，或使用 {StopHotkeyInput}。\n\n" +
            "执行期间会产生真实鼠标点击或键盘输入，确认启动吗？",
            "执行前确认");
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

    private void ReorderCurrentSteps()
    {
        for (var index = 0; index < CurrentSteps.Count; index++)
        {
            CurrentSteps[index].Order = index;
        }
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

    private void RefreshKeyboardCaptureStatus()
    {
        if (SelectedStep is null)
        {
            KeyboardCaptureStatus = "请先选择一个键盘按键步骤。";
            return;
        }

        if (SelectedStep.ActionType != InputActionType.KeyboardPress)
        {
            KeyboardCaptureStatus = "将动作类型切换为“键盘按键”后，可捕获一个按键。";
            return;
        }

        KeyboardCaptureStatus = string.IsNullOrWhiteSpace(SelectedStep.KeyName)
            ? "点击按键框后，按下键盘上的一个键。"
            : $"已设置为 {SelectedStep.KeyName}，点击按键框可重新捕获。";
    }

    private void EnsureMouseStepSelected()
    {
        if (SelectedTask is null)
        {
            return;
        }

        var mouseStep = CurrentSteps.FirstOrDefault(step => step.ActionType == InputActionType.MouseClick);
        if (mouseStep is null)
        {
            mouseStep = CreateStep(InputActionType.MouseClick, SelectedTask.Id, CurrentSteps.Count);
            CurrentSteps.Add(mouseStep);
        }

        SelectedStep = mouseStep;
    }

    private void SelectFirstStepOfType(InputActionType actionType)
    {
        SelectedStep = CurrentSteps.FirstOrDefault(step => step.ActionType == actionType)
            ?? CurrentSteps.FirstOrDefault();
    }

    private ClickStep? GetEditableMouseStep()
    {
        if (SelectedStep?.ActionType == InputActionType.MouseClick)
        {
            return SelectedStep;
        }

        return CurrentSteps.FirstOrDefault(step => step.ActionType == InputActionType.MouseClick);
    }

    private ClickStep? GetEditableKeyboardStep()
    {
        if (SelectedStep?.ActionType == InputActionType.KeyboardPress)
        {
            return SelectedStep;
        }

        return CurrentSteps.FirstOrDefault(step => step.ActionType == InputActionType.KeyboardPress);
    }

    /// <summary>
    /// 全量通知：SelectedTask/SelectedStep 切换或任务加载后调用。
    /// </summary>
    private void NotifyEditorDerivedProperties()
    {
        NotifyStepMetaGroup();
        NotifyCoordinateGroup();
        NotifyMouseGroup();
        NotifySwipeGroup();
        NotifyKeyboardGroup();
        NotifyShortcutGroup();
        NotifyTextGroup();
        NotifyTaskSummaryGroup();
        SelectedStep?.RaisePropertyChanged(null);
    }

    private void NotifyStepMetaGroup()
    {
        OnPropertyChanged(nameof(MouseSteps));
        OnPropertyChanged(nameof(SelectedStepName));
        OnPropertyChanged(nameof(SelectedStepEnabled));
        OnPropertyChanged(nameof(SelectedStepActionType));
        OnPropertyChanged(nameof(SelectedStepBeforeDelayMs));
        NotifyStepVisibility();
    }

    private void NotifyCoordinateGroup()
    {
        OnPropertyChanged(nameof(MouseX));
        OnPropertyChanged(nameof(MouseY));
        OnPropertyChanged(nameof(MousePositionSummary));
        OnPropertyChanged(nameof(SelectedStepX));
        OnPropertyChanged(nameof(SelectedStepY));
        NotifyStepVisibility();
        NotifyTaskSummaryGroup();
        SelectedStep?.RaisePropertyChanged(nameof(ClickStep.X));
        SelectedStep?.RaisePropertyChanged(nameof(ClickStep.Y));
    }

    private void NotifyMouseGroup()
    {
        OnPropertyChanged(nameof(MouseClickType));
        OnPropertyChanged(nameof(MouseClickTypeText));
        OnPropertyChanged(nameof(MouseClickIntervalMs));
        OnPropertyChanged(nameof(MouseClickCount));
        OnPropertyChanged(nameof(SelectedStepClickType));
        OnPropertyChanged(nameof(SelectedStepMouseClickCount));
        OnPropertyChanged(nameof(SelectedStepClickIntervalMs));
        OnPropertyChanged(nameof(SelectedStepPressDurationMs));
        NotifyStepVisibility();
        NotifyTaskSummaryGroup();
        SelectedStep?.RaisePropertyChanged(null);
    }

    private void NotifySwipeGroup()
    {
        OnPropertyChanged(nameof(SelectedStepEndX));
        OnPropertyChanged(nameof(SelectedStepEndY));
        OnPropertyChanged(nameof(SelectedStepSwipeDurationMs));
        NotifyStepVisibility();
        NotifyTaskSummaryGroup();
        SelectedStep?.RaisePropertyChanged(null);
    }

    private void NotifyKeyboardGroup()
    {
        OnPropertyChanged(nameof(SelectedStepKeyName));
        OnPropertyChanged(nameof(SelectedStepKeyDisplayText));
        OnPropertyChanged(nameof(SelectedStepKeyPressCount));
        OnPropertyChanged(nameof(SelectedStepKeyIntervalMs));
        NotifyStepVisibility();
        SelectedStep?.RaisePropertyChanged(null);
    }

    private void NotifyShortcutGroup()
    {
        OnPropertyChanged(nameof(SelectedStepShortcutKeys));
        NotifyStepVisibility();
        SelectedStep?.RaisePropertyChanged(null);
    }

    private void NotifyTextGroup()
    {
        OnPropertyChanged(nameof(SelectedStepTextContent));
        OnPropertyChanged(nameof(SelectedStepAutoFocusBeforeInput));
        NotifyStepVisibility();
        SelectedStep?.RaisePropertyChanged(null);
    }

    private void NotifyTaskSummaryGroup()
    {
        OnPropertyChanged(nameof(MouseTaskName));
        OnPropertyChanged(nameof(MouseTaskDescription));
        OnPropertyChanged(nameof(MouseRepeatCount));
        OnPropertyChanged(nameof(MouseStartDelayMs));
        OnPropertyChanged(nameof(ExecutionSummary));
        OnPropertyChanged(nameof(SelectedTaskMetaText));
        OnPropertyChanged(nameof(FloatingTaskName));
        OnPropertyChanged(nameof(FloatingTaskSummary));
    }

    /// <summary>
    /// 重新评估步骤字段可见性（鼠标/滑动/键盘/组合键/文本输入字段）。
    /// </summary>
    private void NotifyStepVisibility()
    {
        OnPropertyChanged(nameof(IsMouseStepFieldsVisible));
        OnPropertyChanged(nameof(IsSwipeStepFieldsVisible));
        OnPropertyChanged(nameof(IsKeyboardStepFieldsVisible));
        OnPropertyChanged(nameof(IsShortcutStepFieldsVisible));
        OnPropertyChanged(nameof(IsTextInputStepFieldsVisible));
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
            RecentLogs.Add($"{log.StartedAt:MM-dd HH:mm:ss} - {endedAt} | {ToStatusText(log.Status)} | {log.Message}");
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
            dialogService.ShowError(exception.Message, "操作失败");
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
            UpdateFloatingWindowState(status);
            OnPropertyChanged(nameof(IsExecutionRunning));
            OnPropertyChanged(nameof(IsExecutionPaused));
            OnPropertyChanged(nameof(IsNotExecutionRunning));
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
    /// 供坐标选择器关闭后刷新步骤列表等界面数据。
    /// </summary>
    public void NotifyEditorDerivedValuesAfterPicker()
    {
        NotifyEditorDerivedProperties();
        AddRuntimeLog("已从目标界面更新步骤坐标。");
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

    private void ToggleFloatingWindow()
    {
        isFloatingWindowExpanded = !isFloatingWindowExpanded;
        IsFloatingExpandedVisible = isFloatingWindowExpanded;
        IsFloatingCollapsedVisible = !isFloatingWindowExpanded;
    }

    private void UpdateFloatingWindowState(ExecutionStatus status)
    {
        IsFloatingWindowVisible = status is ExecutionStatus.Starting or ExecutionStatus.Running or ExecutionStatus.Paused;

        if (!IsFloatingWindowVisible)
        {
            isFloatingWindowExpanded = false;
            IsFloatingExpandedVisible = false;
            IsFloatingCollapsedVisible = true;
        }

        NotifyEditorDerivedProperties();
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

        dispatcher.BeginInvoke(action);
    }

    /// <summary>
    /// 集中刷新所有命令的可用状态。
    /// </summary>
    private void NotifyCommandStates()
    {
        IsTaskEditorEnabled = CanEdit();
        ShowMouseClickEditorCommand.NotifyCanExecuteChanged();
        CreateMouseClickTaskCommand.NotifyCanExecuteChanged();
        CreateKeyboardTaskCommand.NotifyCanExecuteChanged();
        CreateTextInputTaskCommand.NotifyCanExecuteChanged();
        CreateComboTaskCommand.NotifyCanExecuteChanged();
        CreateTaskCommand.NotifyCanExecuteChanged();
        DuplicateTaskCommand.NotifyCanExecuteChanged();
        DeleteTaskCommand.NotifyCanExecuteChanged();
        SaveTaskCommand.NotifyCanExecuteChanged();
        AddStepCommand.NotifyCanExecuteChanged();
        AddKeyboardStepCommand.NotifyCanExecuteChanged();
        AddShortcutStepCommand.NotifyCanExecuteChanged();
        AddTextInputStepCommand.NotifyCanExecuteChanged();
        RemoveStepCommand.NotifyCanExecuteChanged();
        CaptureCoordinateCommand.NotifyCanExecuteChanged();
        TestMouseClickCommand.NotifyCanExecuteChanged();
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
        return !IsBusy && executionEngine.Status is not ExecutionStatus.Running and not ExecutionStatus.Starting;
    }

    /// <summary>
    /// 判断当前是否存在可操作的选中任务。
    /// </summary>
    private bool CanUseSelectedTask()
    {
        return CanEdit() && SelectedTask is not null;
    }

    private bool CanOpenMouseClickEditor()
    {
        return SelectedTask is not null && CurrentSteps.Any(step => step.ActionType == InputActionType.MouseClick);
    }

    private bool CanUseSelectedMouseStep()
    {
        return CanEdit() && SelectedTask is not null && GetEditableMouseStep() is not null;
    }

    private bool CanCaptureCoordinate()
    {
        return CanUseSelectedMouseStep() && !isCoordinateCapturePending;
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
            ExecutionStatus.Paused => "任务已暂停，可以编辑并保存任务；如需新配置生效，请停止后重新执行。",
            _ => "启动前请确认坐标、按键、执行轮数、点击次数和开始延迟。"
        };
    }

    /// <summary>
    /// 根据动作类型生成默认步骤名称。
    /// </summary>
    private static string CreateStepName(InputActionType actionType, int index)
    {
        return actionType switch
        {
            InputActionType.MouseClick => $"点击步骤 {index}",
            InputActionType.Swipe => $"滑动步骤 {index}",
            InputActionType.KeyboardPress => $"按键步骤 {index}",
            InputActionType.KeyboardShortcut => $"组合键步骤 {index}",
            InputActionType.TextInput => $"文本步骤 {index}",
            _ => $"输入步骤 {index}"
        };
    }

    /// <summary>
    /// 取消执行引擎事件订阅，避免内存泄漏。
    /// </summary>
    public void Dispose()
    {
        executionEngine.StatusChanged -= HandleExecutionStatusChanged;
        executionEngine.LogReceived -= HandleExecutionLogReceived;
    }
}
