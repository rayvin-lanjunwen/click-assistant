using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using ClickAssistant.App.Services;
using ClickAssistant.App.ViewModels;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Application.Services;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Infrastructure.Persistence;
using ClickAssistant.Infrastructure.Windows;

namespace ClickAssistant.App;

/// <summary>
/// 主窗口入口，负责组装首版所需的应用服务和基础设施服务。
/// </summary>
public partial class MainWindow : Window
{
    private const string StopHotkeySettingKey = "StopHotkey";
    private MainWindowViewModel? viewModel;
    private IGlobalHotkeyService? globalHotkeyService;
    private IAppSettingsRepository? appSettingsRepository;
    private IClickExecutionEngine? executionEngine;
    private ICursorPositionService? cursorPositionService;
    private IDialogService? dialogService;
    private FloatingControlWindow? floatingWindow;
    private MouseClickVisualWindow? mouseClickVisualWindow;
    private HwndSource? hwndSource;

    public MainWindow()
    {
        InitializeComponent();
        Loaded += HandleLoaded;
        Closed += HandleClosed;
    }

    /// <summary>
    /// 窗口加载后初始化数据库和 ViewModel。
    /// </summary>
    private async void HandleLoaded(object sender, RoutedEventArgs e)
    {
        Loaded -= HandleLoaded;

        try
        {
            var connectionFactory = new SqliteConnectionFactory();
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var taskRepository = new SqliteClickTaskRepository(connectionFactory);
            var executionLogRepository = new SqliteExecutionLogRepository(connectionFactory);
            var settingsRepository = new SqliteAppSettingsRepository(connectionFactory);
            var mouseClickService = new WindowsMouseClickService();
            var keyboardInputService = new WindowsKeyboardInputService();
            var cursorPositionService = new WindowsCursorPositionService();
            var hotkeyService = new WindowsGlobalHotkeyService();
            var taskService = new ClickTaskService(taskRepository);
            var dialogService = new WpfDialogService();
            var clickExecutionEngine = new ClickExecutionEngine(
                taskRepository,
                executionLogRepository,
                mouseClickService,
                keyboardInputService);

            viewModel = new MainWindowViewModel(
                taskService,
                clickExecutionEngine,
                mouseClickService,
                executionLogRepository,
                dialogService);
            executionEngine = clickExecutionEngine;
            this.cursorPositionService = cursorPositionService;
            this.dialogService = dialogService;
            globalHotkeyService = hotkeyService;
            appSettingsRepository = settingsRepository;

            DataContext = viewModel;
            floatingWindow = new FloatingControlWindow
            {
                DataContext = viewModel,
                Left = Left + Width - 330,
                Top = Top + 92
            };
            floatingWindow.Show();
            mouseClickVisualWindow = new MouseClickVisualWindow();
            executionEngine.MouseClickVisualRequested += HandleMouseClickVisualRequested;

            await viewModel.InitializeAsync();
            viewModel.StopHotkeyChangeRequested += HandleStopHotkeyChangeRequested;
            viewModel.CoordinateSelectionRequested += HandleCoordinateSelectionRequested;

            var savedStopHotkey = await settingsRepository.GetValueAsync(StopHotkeySettingKey);
            var stopHotkeyText = string.IsNullOrWhiteSpace(savedStopHotkey)
                ? hotkeyService.StopHotkeyText
                : savedStopHotkey;
            viewModel.SetStopHotkeyInput(stopHotkeyText);
            RegisterGlobalHotkeys(stopHotkeyText);
        }
        catch (Exception exception)
        {
            dialogService?.ShowError(exception.Message, "启动失败");
            Close();
        }
    }

    private void HandleKeyboardCapturePreviewKeyDown(object sender, KeyEventArgs e)
    {
        var capturedKey = e.Key == Key.System
            ? e.SystemKey
            : e.Key == Key.ImeProcessed
                ? e.ImeProcessedKey
                : e.Key;
        var keyName = ToSupportedKeyName(capturedKey);

        if (string.IsNullOrWhiteSpace(keyName))
        {
            viewModel?.RejectKeyboardCapture(capturedKey.ToString());
            e.Handled = true;
            return;
        }

        viewModel?.CaptureKeyboardStepKey(keyName);
        e.Handled = true;
    }

    private void HandleKeyboardCapturePreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is not UIElement element || element.IsKeyboardFocusWithin)
        {
            return;
        }

        e.Handled = true;
        element.Focus();
    }

    private void HandleKeyboardCaptureGotKeyboardFocus(object sender, KeyboardFocusChangedEventArgs e)
    {
        viewModel?.BeginKeyboardCapture();
    }

    private void HandleKeyboardCaptureLostKeyboardFocus(object sender, KeyboardFocusChangedEventArgs e)
    {
        viewModel?.EndKeyboardCapture();
    }

    private void HandleMouseClickVisualRequested(object? sender, MouseClickVisualEventArgs e)
    {
        _ = Dispatcher.InvokeAsync(() => mouseClickVisualWindow?.ShowAt(e.Point.X, e.Point.Y));
    }

    private void HandleCoordinateSelectionRequested(object? sender, EventArgs e)
    {
        if (viewModel is null || cursorPositionService is null)
        {
            return;
        }

        var allSteps = viewModel.CurrentSteps.ToList();
        var targetStep = viewModel.SelectedStep as ClickStep;

        var pickerWindow = new CoordinatePickerWindow(
            allSteps,
            targetStep: targetStep,
            onStepClicked: clickedStep =>
            {
                _ = Dispatcher.InvokeAsync(() =>
                {
                    viewModel.SelectedStep = clickedStep;
                });
            })
        {
            Owner = this,
            HighlightedStep = targetStep
        };
        pickerWindow.ShowDialog();

        viewModel.CoordinatePickerClosed(pickerWindow.IsCoordinateChanged);
        Activate();
    }

    /// <summary>
    /// 注册全局快捷键，并把窗口消息转交给快捷键服务。
    /// </summary>
    private HotkeyRegistrationResult? RegisterGlobalHotkeys(string stopHotkeyText)
    {
        if (viewModel is null || globalHotkeyService is null)
        {
            return null;
        }

        var windowHandle = new WindowInteropHelper(this).Handle;
        if (hwndSource is null)
        {
            hwndSource = HwndSource.FromHwnd(windowHandle);
            hwndSource?.AddHook(HandleWindowMessage);
        }

        globalHotkeyService.StopRequested -= HandleStopHotkeyRequested;
        globalHotkeyService.StopRequested += HandleStopHotkeyRequested;
        var result = globalHotkeyService.RegisterStopHotkey(windowHandle, stopHotkeyText);
        viewModel.ApplyHotkeyRegistrationResult(result);
        return result;
    }

    /// <summary>
    /// 处理窗口消息，识别全局快捷键。
    /// </summary>
    private IntPtr HandleWindowMessage(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (globalHotkeyService?.ProcessWindowMessage(hwnd, message, wParam) == true)
        {
            handled = true;
        }

        return IntPtr.Zero;
    }

    /// <summary>
    /// 收到全局停止快捷键后请求执行引擎停止。
    /// </summary>
    private async void HandleStopHotkeyRequested(object? sender, EventArgs e)
    {
        if (viewModel is not null)
        {
            await viewModel.RequestStopFromHotkeyAsync();
        }
    }

    /// <summary>
    /// 处理用户保存停止快捷键请求，注册成功后再持久化。
    /// </summary>
    private async void HandleStopHotkeyChangeRequested(object? sender, string hotkeyText)
    {
        if (viewModel is null || appSettingsRepository is null)
        {
            return;
        }

        try
        {
            var result = RegisterGlobalHotkeys(hotkeyText);
            if (result?.IsRegistered != true)
            {
                return;
            }

            await appSettingsRepository.SetValueAsync(StopHotkeySettingKey, result.HotkeyText);
            viewModel.SetHotkeyStatus($"全局停止快捷键已保存：{result.HotkeyText}");
        }
        catch (Exception exception)
        {
            dialogService?.ShowError(exception.Message, "快捷键保存失败");
            viewModel.SetHotkeyStatus($"快捷键保存失败：{exception.Message}");
        }
    }

    /// <summary>
    /// 窗口关闭时注销快捷键和窗口消息钩子。
    /// </summary>
    private void HandleClosed(object? sender, EventArgs e)
    {
        if (viewModel is not null)
        {
            viewModel.StopHotkeyChangeRequested -= HandleStopHotkeyChangeRequested;
            viewModel.CoordinateSelectionRequested -= HandleCoordinateSelectionRequested;
            viewModel.Dispose();
        }

        if (globalHotkeyService is not null)
        {
            globalHotkeyService.StopRequested -= HandleStopHotkeyRequested;
            globalHotkeyService.Dispose();
        }

        if (executionEngine is not null)
        {
            executionEngine.MouseClickVisualRequested -= HandleMouseClickVisualRequested;
        }

        hwndSource?.RemoveHook(HandleWindowMessage);
        floatingWindow?.Close();
        floatingWindow = null;
        mouseClickVisualWindow?.Close();
        mouseClickVisualWindow = null;
        executionEngine = null;
    }

    private static string? ToSupportedKeyName(Key key)
    {
        if (key >= Key.A && key <= Key.Z)
        {
            return key.ToString();
        }

        if (key >= Key.D0 && key <= Key.D9)
        {
            return ((int)(key - Key.D0)).ToString();
        }

        if (key >= Key.NumPad0 && key <= Key.NumPad9)
        {
            return $"NumPad{(int)(key - Key.NumPad0)}";
        }

        if (key >= Key.F1 && key <= Key.F24)
        {
            return key.ToString();
        }

        return key switch
        {
            Key.Return => "Enter",
            Key.Space => "Space",
            Key.Tab => "Tab",
            Key.Escape => "Esc",
            Key.Back => "Backspace",
            Key.Delete => "Delete",
            Key.Insert => "Insert",
            Key.Home => "Home",
            Key.End => "End",
            Key.PageUp => "PageUp",
            Key.PageDown => "PageDown",
            Key.Left => "Left",
            Key.Up => "Up",
            Key.Right => "Right",
            Key.Down => "Down",
            Key.LeftCtrl or Key.RightCtrl => "Ctrl",
            Key.LeftAlt or Key.RightAlt => "Alt",
            Key.LeftShift or Key.RightShift => "Shift",
            Key.LWin or Key.RWin => "Win",
            Key.CapsLock => "CapsLock",
            _ => null
        };
    }
}
