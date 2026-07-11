namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 对话框服务抽象，用于在 ViewModel 中展示确认、警告和错误提示，
/// 避免直接依赖 WPF 的 MessageBox。
/// </summary>
public interface IDialogService
{
    /// <summary>
    /// 显示确认对话框，返回用户是否选择了"是"。
    /// </summary>
    bool Confirm(string message, string title);

    /// <summary>
    /// 显示警告提示。
    /// </summary>
    void ShowWarning(string message, string title);

    /// <summary>
    /// 显示错误提示。
    /// </summary>
    void ShowError(string message, string title);
}
