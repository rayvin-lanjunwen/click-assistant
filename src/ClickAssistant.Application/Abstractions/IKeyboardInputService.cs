namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 键盘输入服务，执行引擎通过该抽象触发按键、组合键和文本输入。
/// </summary>
public interface IKeyboardInputService
{
    Task PressKeyAsync(
        string keyName,
        int pressCount,
        int intervalMs,
        CancellationToken cancellationToken = default);

    Task PressShortcutAsync(
        string shortcutKeys,
        CancellationToken cancellationToken = default);

    Task TypeTextAsync(
        string text,
        int intervalMs,
        CancellationToken cancellationToken = default);
}
