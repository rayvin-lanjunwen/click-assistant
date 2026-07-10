using System.Collections.ObjectModel;
using System.Windows;
using ClickAssistant.App.Commands;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Application.Services;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.App.ViewModels;

/// <summary>
/// 主窗口 ViewModel，负责任务列表、步骤编辑、执行控制和日志展示。
/// </summary>
public sealed class MainWindowViewModel : ObservableObject
{
    private readonly ClickTaskService taskService;
    private readonly IClickExecutionEngine executionEngine;
    private readonly IMouseClickService mouseClickService;
    private readonly IExecutionLogRepository executionLogRepository;
    private ClickTask? selectedTask;
    private ClickStep? selectedStep;
    private ObservableCollection<ClickStep> currentSteps = [];
    private string statusText = "空闲";
    private string hotkeyStatus = "全局停止快捷键未注册";
    private string keyboardCaptureStatus = "点击按键框后，按下键盘上的一个键。";
    private string coordinateCaptureStatus = "点击“选择坐标”后移动鼠标，左键确认位置，Esc 取消。";
    private string stopHotkeyInput = "Ctrl + Alt + S";
    private string executionSafetyText = "启动前请确认坐标、按键、重复次数和开始延迟。";
    private bool isTaskEditorEnabled = true;
    private int taskLibrarySelectedTabIndex;
    private Visibility homePageVisibility = Visibility.Visible;
    private Visibility newTaskTypePageVisibility = Visibility.Collapsed;
    private Visibility taskLibraryPageVisibility = Visibility.Collapsed;
    private Visibility executionLogsPageVisibility = Visibility.Collapsed;
    private Visibility mouseClickEditorPageVisibility = Visibility.Collapsed;
    private Visibility floatingWindowVisibility = Visibility.Collapsed;
    private Visibility floatingExpandedVisibility = Visibility.Collapsed;
    private Visibility floatingCollapsedVisibility = Visibility.Visible;
    private bool isFloatingWindowExpanded;
    private bool isCoordinateCapturePending;
    private bool isBusy;

    public MainWindowViewModel(
        ClickTaskService taskService,
        IClickExecutionEngine executionEngine,
        IMouseClickService mouseClickService,
        IExecutionLogRepository executionLogRepository)
    {
        this.taskService = taskService;
        this.executionEngine = executionEngine;
        this.mouseClickService = mouseClickService;
        this.executionLogRepository = executionLogRepository;

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
        new InputActionTypeOption(InputActionType.TextInput, "文本输入")
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

    public string CoordinateCaptureStatus
    {
        get => coordinateCaptureStatus;
        private set => SetProperty(ref coordinateCaptureStatus, value);
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

    public Visibility HomePageVisibility
    {
        get => homePageVisibility;
        private set => SetProperty(ref homePageVisibility, value);
    }

    public Visibility NewTaskTypePageVisibility
    {
        get => newTaskTypePageVisibility;
        private set => SetProperty(ref newTaskTypePageVisibility, value);
    }

    public Visibility TaskLibraryPageVisibility
    {
        get => taskLibraryPageVisibility;
        private set => SetProperty(ref taskLibraryPageVisibility, value);
    }

    public Visibility ExecutionLogsPageVisibility
    {
        get => executionLogsPageVisibility;
        private set => SetProperty(ref executionLogsPageVisibility, value);
    }

    public Visibility MouseClickEditorPageVisibility
    {
        get => mouseClickEditorPageVisibility;
        private set => SetProperty(ref mouseClickEditorPageVisibility, value);
    }

    public Visibility FloatingWindowVisibility
    {
        get => floatingWindowVisibility;
        private set => SetProperty(ref floatingWindowVisibility, value);
    }

    public Visibility FloatingExpandedVisibility
    {
        get => floatingExpandedVisibility;
        private set => SetProperty(ref floatingExpandedVisibility, value);
    }

    public Visibility FloatingCollapsedVisibility
    {
        get => floatingCollapsedVisibility;
        private set => SetProperty(ref floatingCollapsedVisibility, value);
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
        }
    }

    public int MouseClickIntervalMs
    {
        get => GetEditableMouseStep()?.AfterDelayMs ?? 0;
        set
        {
            var step = GetEditableMouseStep();
            if (step is null || step.AfterDelayMs == value)
            {
                return;
            }

            step.AfterDelayMs = value;
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
        }
    }

    public int SelectedStepAfterDelayMs
    {
        get => SelectedStep?.AfterDelayMs ?? 0;
        set
        {
            if (SelectedStep is null || SelectedStep.AfterDelayMs == value)
            {
                return;
            }

            SelectedStep.AfterDelayMs = value;
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
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
            NotifyEditorDerivedProperties();
        }
    }

    public Visibility MouseStepFieldsVisibility => SelectedStep?.ActionType == InputActionType.MouseClick
        ? Visibility.Visible
        : Visibility.Collapsed;

    public Visibility KeyboardStepFieldsVisibility => SelectedStep?.ActionType == InputActionType.KeyboardPress
        ? Visibility.Visible
        : Visibility.Collapsed;

    public Visibility ShortcutStepFieldsVisibility => SelectedStep?.ActionType == InputActionType.KeyboardShortcut
        ? Visibility.Visible
        : Visibility.Collapsed;

    public Visibility TextInputStepFieldsVisibility => SelectedStep?.ActionType == InputActionType.TextInput
        ? Visibility.Visible
        : Visibility.Collapsed;

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
            return $"开始后等待 {FormatMilliseconds(SelectedTask.StartDelayMs)}，每轮执行 {enabledSteps}/{totalSteps} 个启用步骤，" +
                $"重复 {SelectedTask.RepeatCount} 次。";
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

            return GetEditableMouseStep() is { } step
                ? $"X={step.X}，Y={step.Y} · {SelectedTask.RepeatCount} 次 · 间隔 {FormatMilliseconds(step.AfterDelayMs)}"
                : $"{SelectedTask.Steps.Count} 个步骤";
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
        HomePageVisibility = pageName == "Home" ? Visibility.Visible : Visibility.Collapsed;
        NewTaskTypePageVisibility = pageName == "NewTaskType" ? Visibility.Visible : Visibility.Collapsed;
        TaskLibraryPageVisibility = pageName == "TaskLibrary" ? Visibility.Visible : Visibility.Collapsed;
        ExecutionLogsPageVisibility = pageName == "ExecutionLogs" ? Visibility.Visible : Visibility.Collapsed;
        MouseClickEditorPageVisibility = pageName == "MouseClickEditor" ? Visibility.Visible : Visibility.Collapsed;
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
            Order = order,
            AfterDelayMs = actionType == InputActionType.MouseClick ? 800 : 500
        };
        ApplyStepDefaults(step);
        return step;
    }

    private static void ApplyStepDefaults(ClickStep step)
    {
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
            Order = CurrentSteps.Count,
            AfterDelayMs = 500
        };
        ApplyStepDefaults(step);

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
            CancelCoordinateSelection();
            return;
        }

        step.X = point.X;
        step.Y = point.Y;
        SelectedStep = step;
        RefreshSelectedStepListItem();

        isCoordinateCapturePending = false;
        CoordinateCaptureStatus = $"已选择坐标：X={point.X}，Y={point.Y}。";
        NotifyEditorDerivedProperties();
        NotifyCommandStates();
        AddRuntimeLog($"已选择坐标：X={point.X}，Y={point.Y}。");
    }

    /// <summary>
    /// 坐标选择取消后恢复界面状态。
    /// </summary>
    public void CancelCoordinateSelection()
    {
        isCoordinateCapturePending = false;
        CoordinateCaptureStatus = "已取消坐标选择。点击“选择坐标”可重新选择。";
        NotifyCommandStates();
        AddRuntimeLog("已取消坐标选择。");
    }

    private async Task TestMouseClickOnceAsync()
    {
        var step = GetEditableMouseStep();
        if (step is null)
        {
            return;
        }

        var result = MessageBox.Show(
            $"即将在 X={step.X}、Y={step.Y} 执行一次{MouseClickTypeText}。\n\n确认测试点击吗？",
            "测试点击一次",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);

        if (result != MessageBoxResult.Yes)
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
        var keySteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.KeyboardPress);
        var shortcutSteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.KeyboardShortcut);
        var textInputSteps = SelectedTask.Steps.Count(step => step.Enabled && step.ActionType == InputActionType.TextInput);

        var result = MessageBox.Show(
            $"即将执行任务“{SelectedTask.Name}”。\n\n" +
            $"重复次数：{SelectedTask.RepeatCount}\n" +
            $"启用步骤：{enabledSteps} 个（鼠标 {mouseSteps} 个，按键 {keySteps} 个，组合键 {shortcutSteps} 个，文本 {textInputSteps} 个）\n" +
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

    private void RefreshSelectedStepListItem()
    {
        if (SelectedStep is null)
        {
            return;
        }

        var selectedIndex = CurrentSteps.IndexOf(SelectedStep);
        if (selectedIndex >= 0)
        {
            CurrentSteps[selectedIndex] = SelectedStep;
        }
    }

    private void NotifyEditorDerivedProperties()
    {
        OnPropertyChanged(nameof(MouseTaskName));
        OnPropertyChanged(nameof(MouseTaskDescription));
        OnPropertyChanged(nameof(MouseRepeatCount));
        OnPropertyChanged(nameof(MouseStartDelayMs));
        OnPropertyChanged(nameof(MouseX));
        OnPropertyChanged(nameof(MouseY));
        OnPropertyChanged(nameof(MouseClickIntervalMs));
        OnPropertyChanged(nameof(MouseClickType));
        OnPropertyChanged(nameof(MouseClickTypeText));
        OnPropertyChanged(nameof(SelectedStepName));
        OnPropertyChanged(nameof(SelectedStepEnabled));
        OnPropertyChanged(nameof(SelectedStepActionType));
        OnPropertyChanged(nameof(SelectedStepBeforeDelayMs));
        OnPropertyChanged(nameof(SelectedStepAfterDelayMs));
        OnPropertyChanged(nameof(SelectedStepX));
        OnPropertyChanged(nameof(SelectedStepY));
        OnPropertyChanged(nameof(SelectedStepClickType));
        OnPropertyChanged(nameof(SelectedStepKeyName));
        OnPropertyChanged(nameof(SelectedStepKeyDisplayText));
        OnPropertyChanged(nameof(SelectedStepKeyPressCount));
        OnPropertyChanged(nameof(SelectedStepKeyIntervalMs));
        OnPropertyChanged(nameof(SelectedStepShortcutKeys));
        OnPropertyChanged(nameof(SelectedStepTextContent));
        OnPropertyChanged(nameof(MouseStepFieldsVisibility));
        OnPropertyChanged(nameof(KeyboardStepFieldsVisibility));
        OnPropertyChanged(nameof(ShortcutStepFieldsVisibility));
        OnPropertyChanged(nameof(TextInputStepFieldsVisibility));
        OnPropertyChanged(nameof(ExecutionSummary));
        OnPropertyChanged(nameof(SelectedTaskMetaText));
        OnPropertyChanged(nameof(FloatingTaskName));
        OnPropertyChanged(nameof(FloatingTaskSummary));
        RefreshSelectedStepListItem();
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
            UpdateFloatingWindowState(status);
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

    private void ToggleFloatingWindow()
    {
        isFloatingWindowExpanded = !isFloatingWindowExpanded;
        FloatingExpandedVisibility = isFloatingWindowExpanded ? Visibility.Visible : Visibility.Collapsed;
        FloatingCollapsedVisibility = isFloatingWindowExpanded ? Visibility.Collapsed : Visibility.Visible;
    }

    private void UpdateFloatingWindowState(ExecutionStatus status)
    {
        FloatingWindowVisibility = status is ExecutionStatus.Starting or ExecutionStatus.Running or ExecutionStatus.Paused
            ? Visibility.Visible
            : Visibility.Collapsed;

        if (FloatingWindowVisibility == Visibility.Collapsed)
        {
            isFloatingWindowExpanded = false;
            FloatingExpandedVisibility = Visibility.Collapsed;
            FloatingCollapsedVisibility = Visibility.Visible;
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

        dispatcher.Invoke(action);
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
            _ => "启动前请确认坐标、按键、重复次数和开始延迟。"
        };
    }

    private static string FormatMilliseconds(int milliseconds)
    {
        return milliseconds >= 1000 && milliseconds % 1000 == 0
            ? $"{milliseconds / 1000} 秒"
            : $"{milliseconds} 毫秒";
    }

    /// <summary>
    /// 根据动作类型生成默认步骤名称。
    /// </summary>
    private static string CreateStepName(InputActionType actionType, int index)
    {
        return actionType switch
        {
            InputActionType.MouseClick => $"鼠标步骤 {index}",
            InputActionType.KeyboardPress => $"按键步骤 {index}",
            InputActionType.KeyboardShortcut => $"组合键步骤 {index}",
            InputActionType.TextInput => $"文本步骤 {index}",
            _ => $"输入步骤 {index}"
        };
    }
}
