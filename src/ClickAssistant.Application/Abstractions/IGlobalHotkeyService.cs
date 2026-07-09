namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 全局快捷键服务接口，用于处理窗口失焦时的紧急控制操作。
/// </summary>
public interface IGlobalHotkeyService : IDisposable
{
    event EventHandler? StopRequested;

    string StopHotkeyText { get; }

    /// <summary>
    /// 为指定窗口注册立即停止快捷键。
    /// </summary>
    HotkeyRegistrationResult RegisterStopHotkey(IntPtr windowHandle, string hotkeyText);

    /// <summary>
    /// 注销指定窗口上的立即停止快捷键。
    /// </summary>
    void UnregisterStopHotkey(IntPtr windowHandle);

    /// <summary>
    /// 处理窗口消息，识别全局快捷键触发事件。
    /// </summary>
    bool ProcessWindowMessage(IntPtr windowHandle, int message, IntPtr hotkeyId);
}
