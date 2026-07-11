using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.Exceptions;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Domain.Entities;

/// <summary>
/// 输入步骤，表示任务中的一次鼠标/键盘/文本/滑动动作。
/// 等待时间统一为步骤前等待（BeforeDelayMs），不再有独立的等待步骤类型。
/// </summary>
public sealed class ClickStep
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid TaskId { get; set; }

    public string Name { get; set; } = "新步骤";

    public bool Enabled { get; set; } = true;

    public InputActionType ActionType { get; set; } = InputActionType.MouseClick;

    // 鼠标点击坐标
    public int X { get; set; }

    public int Y { get; set; }

    public ClickType ClickType { get; set; } = ClickType.LeftSingle;

    public int MouseClickCount { get; set; } = 1;

    /// <summary>
    /// 连续点击之间的间隔（毫秒），替代原 AfterDelayMs 在连点场景的作用。
    /// </summary>
    public int ClickIntervalMs { get; set; } = 100;

    /// <summary>
    /// 单次按压时长（毫秒），0 表示系统默认按压时长。
    /// </summary>
    public int PressDurationMs { get; set; }

    // 键盘按键
    public string KeyName { get; set; } = string.Empty;

    public int KeyPressCount { get; set; } = 1;

    public int KeyIntervalMs { get; set; } = 100;

    public string ShortcutKeys { get; set; } = "Ctrl+C";

    // 文本输入
    public string TextContent { get; set; } = string.Empty;

    /// <summary>
    /// 输入文本前是否自动点击目标位置获得焦点。
    /// </summary>
    public bool AutoFocusBeforeInput { get; set; }

    // 滑动（桌面端也支持滑动）
    public int EndX { get; set; }

    public int EndY { get; set; }

    public int SwipeDurationMs { get; set; } = 300;

    // 通用延迟
    /// <summary>
    /// 执行当前步骤前的等待时间（毫秒）。对于第一步即任务启动后的初始等待。
    /// 步骤间等待由下一步骤的 BeforeDelayMs 表示，不再使用 AfterDelayMs。
    /// </summary>
    public int BeforeDelayMs { get; set; }

    public int Order { get; set; }

    /// <summary>
    /// 将分散坐标转换为值对象，便于执行引擎调用。
    /// </summary>
    public ScreenPoint ToPoint()
    {
        return new ScreenPoint(X, Y);
    }

    /// <summary>
    /// 校验步骤草稿配置，允许键盘按键名称暂时留空，便于用户稍后从键盘捕获。
    /// </summary>
    public void ValidateForSave()
    {
        if (string.IsNullOrWhiteSpace(Name))
        {
            throw new DomainValidationException("步骤名称不能为空。");
        }

        if (BeforeDelayMs < 0)
        {
            throw new DomainValidationException("步骤前等待时间不能小于 0。");
        }

        if (KeyIntervalMs < 0 || ClickIntervalMs < 0 || PressDurationMs < 0 || SwipeDurationMs < 0)
        {
            throw new DomainValidationException("间隔和持续时间不能小于 0。");
        }

        if (ActionType == InputActionType.MouseClick)
        {
            ValidateMouseClick();
        }

        if (ActionType == InputActionType.Swipe)
        {
            ValidateSwipe();
        }

        if (ActionType == InputActionType.KeyboardPress)
        {
            ValidateKeyboardPress(requireKeyName: false);
        }

        if (ActionType == InputActionType.KeyboardShortcut)
        {
            ValidateKeyboardShortcut();
        }

        if (ActionType == InputActionType.TextInput)
        {
            ValidateTextInput();
        }

        if (Order < 0)
        {
            throw new DomainValidationException("步骤顺序不能小于 0。");
        }
    }

    /// <summary>
    /// 校验可执行步骤配置，避免执行过程中才发现基础数据错误。
    /// </summary>
    public void Validate()
    {
        ValidateForSave();

        if (ActionType == InputActionType.KeyboardPress)
        {
            ValidateKeyboardPress(requireKeyName: true);
        }
    }

    /// <summary>
    /// 校验鼠标点击步骤配置，避免单个位置误配置为 0 次或过大的连点次数。
    /// </summary>
    private void ValidateMouseClick()
    {
        if (MouseClickCount < 1)
        {
            throw new DomainValidationException("鼠标点击次数必须大于或等于 1。");
        }

        if (MouseClickCount > 10000)
        {
            throw new DomainValidationException("鼠标点击次数不能超过 10000。");
        }
    }

    /// <summary>
    /// 校验滑动步骤配置，起点和终点不能相同，持续时间必须大于 0。
    /// </summary>
    private void ValidateSwipe()
    {
        if (SwipeDurationMs < 1)
        {
            throw new DomainValidationException("滑动持续时间必须大于 0。");
        }

        if (X == EndX && Y == EndY)
        {
            throw new DomainValidationException("滑动起点和终点不能相同。");
        }
    }

    /// <summary>
    /// 校验键盘步骤配置，避免执行时无法识别按键或产生失控连按。
    /// </summary>
    private void ValidateKeyboardPress(bool requireKeyName)
    {
        if (requireKeyName && string.IsNullOrWhiteSpace(KeyName))
        {
            throw new DomainValidationException("键盘步骤必须填写按键名称。");
        }

        if (KeyPressCount < 1)
        {
            throw new DomainValidationException("键盘连按次数必须大于或等于 1。");
        }

        if (KeyPressCount > 10000)
        {
            throw new DomainValidationException("键盘连按次数不能超过 10000。");
        }
    }

    /// <summary>
    /// 校验组合键配置，至少需要一个修饰键和一个主按键。
    /// </summary>
    private void ValidateKeyboardShortcut()
    {
        if (string.IsNullOrWhiteSpace(ShortcutKeys))
        {
            throw new DomainValidationException("组合键步骤必须填写组合键。");
        }

        var keyParts = ShortcutKeys
            .Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

        if (keyParts.Length < 2)
        {
            throw new DomainValidationException("组合键至少需要一个修饰键和一个主按键，例如 Ctrl+C。");
        }

        if (IsModifierKeyName(keyParts[^1]))
        {
            throw new DomainValidationException("组合键最后一个按键必须是主按键，不能只是修饰键。");
        }
    }

    /// <summary>
    /// 校验文本输入配置，避免空文本或过长文本导致误输入。
    /// </summary>
    private void ValidateTextInput()
    {
        if (string.IsNullOrEmpty(TextContent))
        {
            throw new DomainValidationException("文本输入步骤必须填写文本内容。");
        }

        if (TextContent.Length > 10000)
        {
            throw new DomainValidationException("文本输入内容不能超过 10000 个字符。");
        }
    }

    /// <summary>
    /// 判断按键名称是否为修饰键。
    /// </summary>
    private static bool IsModifierKeyName(string keyName)
    {
        return keyName.Trim().ToUpperInvariant() switch
        {
            "CTRL" or "CONTROL" or "ALT" or "SHIFT" or "WIN" or "WINDOWS" or "META" => true,
            _ => false
        };
    }
}
