using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using System.ComponentModel;
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
    private const string ThemeSettingKey = "Theme";
    private const string ThemeDark = "Dark";
    private const string ThemeLight = "Light";
    private ResourceDictionary? darkThemeDictionary;
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
            viewModel.PropertyChanged += HandleViewModelPropertyChanged;
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

            // 恢复保存的主题设置
            await RestoreThemeAsync(settingsRepository);
        }
        catch (Exception exception)
        {
            dialogService?.ShowError(exception.Message, "启动失败");
            Close();
        }
    }

    /// <summary>
    /// 主题切换按钮点击：切换深浅色模式并更新按钮文案。
    /// </summary>
    private async void HandleThemeToggleClick(object sender, RoutedEventArgs e)
    {
        await ToggleThemeAsync();
        var currentTheme = System.Windows.Application.Current.Resources["ThemeName"] as string ?? ThemeLight;
        ThemeToggleButton.Content = string.Equals(currentTheme, ThemeDark, System.StringComparison.OrdinalIgnoreCase)
            ? "☀️ 浅色模式"
            : "🌙 深色模式";
    }

    /// <summary>
    /// 从数据库恢复上次的主题设置。
    /// </summary>
    private async System.Threading.Tasks.Task RestoreThemeAsync(IAppSettingsRepository settingsRepo)
    {
        var savedTheme = await settingsRepo.GetValueAsync(ThemeSettingKey);
        if (string.Equals(savedTheme, ThemeDark, System.StringComparison.OrdinalIgnoreCase))
        {
            ApplyTheme(ThemeDark);
        }
    }

    /// <summary>
    /// 切换深浅色主题，并持久化到数据库。
    /// </summary>
    public async System.Threading.Tasks.Task ToggleThemeAsync()
    {
        var currentTheme = System.Windows.Application.Current.Resources["ThemeName"] as string ?? ThemeLight;
        var newTheme = string.Equals(currentTheme, ThemeDark, System.StringComparison.OrdinalIgnoreCase)
            ? ThemeLight
            : ThemeDark;
        ApplyTheme(newTheme);

        if (appSettingsRepository != null)
        {
            await appSettingsRepository.SetValueAsync(ThemeSettingKey, newTheme);
        }
    }

    /// <summary>
    /// 应用指定主题：将深色字典注入/移出 Application.Resources.MergedDictionaries。
    /// 深色字典通过覆盖默认值实现主题切换，不再时浅色默认值生效。
    /// </summary>
    private void ApplyTheme(string theme)
    {
        var appResources = System.Windows.Application.Current.Resources;
        var mergedDictionaries = appResources.MergedDictionaries;

        // 获取深色主题字典引用（App.xaml 中以 x:Key="DarkThemeDictionary" 存储在根资源中）
        if (darkThemeDictionary == null)
        {
            darkThemeDictionary = appResources["DarkThemeDictionary"] as ResourceDictionary;
        }

        if (string.Equals(theme, ThemeDark, System.StringComparison.OrdinalIgnoreCase))
        {
            if (darkThemeDictionary != null && !mergedDictionaries.Contains(darkThemeDictionary))
            {
                mergedDictionaries.Insert(0, darkThemeDictionary);
            }
        }
        else
        {
            if (darkThemeDictionary != null && mergedDictionaries.Contains(darkThemeDictionary))
            {
                mergedDictionaries.Remove(darkThemeDictionary);
            }
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

    /// <summary>
    /// 仅在任务启动、运行或暂停时显示悬浮控制窗，避免空闲窗口遮挡主界面。
    /// </summary>
    private void HandleViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName != nameof(MainWindowViewModel.IsFloatingWindowVisible) ||
            viewModel is null || floatingWindow is null)
        {
            return;
        }

        if (viewModel.IsFloatingWindowVisible)
        {
            if (!floatingWindow.IsVisible)
            {
                floatingWindow.Show();
            }
        }
        else if (floatingWindow.IsVisible)
        {
            floatingWindow.Hide();
        }
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
            viewModel.PropertyChanged -= HandleViewModelPropertyChanged;
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
