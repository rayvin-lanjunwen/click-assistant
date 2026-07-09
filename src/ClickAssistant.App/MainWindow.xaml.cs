using System.Windows;
using System.Windows.Interop;
using ClickAssistant.App.ViewModels;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Application.Services;
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
            var databaseInitializer = new DatabaseInitializer(connectionFactory);
            await databaseInitializer.InitializeAsync();

            var taskRepository = new SqliteClickTaskRepository(connectionFactory);
            var executionLogRepository = new SqliteExecutionLogRepository(connectionFactory);
            var settingsRepository = new SqliteAppSettingsRepository(connectionFactory);
            var mouseClickService = new WindowsMouseClickService();
            var keyboardInputService = new WindowsKeyboardInputService();
            var cursorPositionService = new WindowsCursorPositionService();
            var hotkeyService = new WindowsGlobalHotkeyService();
            var taskService = new ClickTaskService(taskRepository);
            var executionEngine = new ClickExecutionEngine(
                taskRepository,
                executionLogRepository,
                mouseClickService,
                keyboardInputService);

            viewModel = new MainWindowViewModel(
                taskService,
                executionEngine,
                cursorPositionService,
                executionLogRepository);
            globalHotkeyService = hotkeyService;
            appSettingsRepository = settingsRepository;

            DataContext = viewModel;
            await viewModel.InitializeAsync();
            viewModel.StopHotkeyChangeRequested += HandleStopHotkeyChangeRequested;

            var savedStopHotkey = await settingsRepository.GetValueAsync(StopHotkeySettingKey);
            var stopHotkeyText = string.IsNullOrWhiteSpace(savedStopHotkey)
                ? hotkeyService.StopHotkeyText
                : savedStopHotkey;
            viewModel.SetStopHotkeyInput(stopHotkeyText);
            RegisterGlobalHotkeys(stopHotkeyText);
        }
        catch (Exception exception)
        {
            MessageBox.Show(exception.Message, "启动失败", MessageBoxButton.OK, MessageBoxImage.Error);
            Close();
        }
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
            MessageBox.Show(exception.Message, "快捷键保存失败", MessageBoxButton.OK, MessageBoxImage.Error);
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
        }

        if (globalHotkeyService is not null)
        {
            globalHotkeyService.StopRequested -= HandleStopHotkeyRequested;
            globalHotkeyService.Dispose();
        }

        hwndSource?.RemoveHook(HandleWindowMessage);
    }
}
