namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 键盘输入服务，执行引擎通过该抽象触发按键和连按。
/// </summary>
public interface IKeyboardInputService
{
    Task PressKeyAsync(
        string keyName,
        int pressCount,
        int intervalMs,
        CancellationToken cancellationToken = default);
}
