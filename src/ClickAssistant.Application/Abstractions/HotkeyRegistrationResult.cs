namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 全局快捷键注册结果，用于把系统注册状态反馈到界面。
/// </summary>
public sealed record HotkeyRegistrationResult(
    bool IsRegistered,
    string HotkeyText,
    string Message);
