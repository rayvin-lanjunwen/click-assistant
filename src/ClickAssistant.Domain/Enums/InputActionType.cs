namespace ClickAssistant.Domain.Enums;

/// <summary>
/// 输入动作类型，用于区分步骤执行鼠标操作、键盘操作或文本输入。
/// </summary>
public enum InputActionType
{
    MouseClick = 0,
    KeyboardPress = 1,
    KeyboardShortcut = 2,
    TextInput = 3
}
