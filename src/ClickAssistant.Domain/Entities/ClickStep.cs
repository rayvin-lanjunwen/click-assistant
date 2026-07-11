using System.ComponentModel;
using System.Runtime.CompilerServices;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.Exceptions;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Domain.Entities;

/// <summary>
/// 输入步骤，表示任务中的一次鼠标/键盘/文本/滑动动作。
/// 等待时间统一为步骤前等待（BeforeDelayMs），不再有独立的等待步骤类型。
/// </summary>
public sealed class ClickStep : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    /// <summary>
    /// 由 ViewModel 在修改属性后调用，驱动 WPF ItemsControl 增量刷新。
    /// </summary>
    public void RaisePropertyChanged([CallerMemberName] string? propertyName = null)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }
    public Guid Id { get; init; } = Guid.NewGuid();

    public Guid TaskId { get; set; }

    public string Name { get; set; } = "新步骤";

    public bool Enabled { get; set; } = true;

    public InputActionType ActionType { get; set; } = InputActionType.MouseClick;

    // 鼠标点击坐标
    public int X { get; set; }

    public int Y { get; set; }

    public ClickType ClickType { get; set; } = ClickType.LeftSingle;

    private int mouseClickCount = 1;

    public int MouseClickCount
    {
        get => mouseClickCount;
        set => mouseClickCount = value >= 1 ? value
            : throw new DomainValidationException("鼠标点击次数必须大于或等于 1。");
    }

    /// <summary>
    /// 连续点击之间的间隔（毫秒），替代原 AfterDelayMs 在连点场景的作用。
    /// </summary>
    private int clickIntervalMs = 100;

    public int ClickIntervalMs
    {
        get => clickIntervalMs;
        set => clickIntervalMs = value >= 0 ? value
            : throw new DomainValidationException("连点间隔不能小于 0。");
    }

    /// <summary>
    /// 单次按压时长（毫秒），0 表示系统默认按压时长。
    /// </summary>
    private int pressDurationMs;

    public int PressDurationMs
    {
        get => pressDurationMs;
        set => pressDurationMs = value >= 0 ? value
            : throw new DomainValidationException("按压时长不能小于 0。");
    }

    // 键盘按键
    public string KeyName { get; set; } = string.Empty;

    private int keyPressCount = 1;

    public int KeyPressCount
    {
        get => keyPressCount;
        set => keyPressCount = value >= 1 ? value
            : throw new DomainValidationException("键盘连按次数必须大于或等于 1。");
    }

    private int keyIntervalMs = 100;

    public int KeyIntervalMs
    {
        get => keyIntervalMs;
        set => keyIntervalMs = value >= 0 ? value
            : throw new DomainValidationException("按键间隔不能小于 0。");
    }

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

    private int swipeDurationMs = 300;

    public int SwipeDurationMs
    {
        get => swipeDurationMs;
        set => swipeDurationMs = value >= 1 ? value
            : throw new DomainValidationException("滑动持续时间必须大于 0。");
    }

    // 通用延迟
    /// <summary>
    /// 执行当前步骤前的等待时间（毫秒）。对于第一步即任务启动后的初始等待。
    /// 步骤间等待由下一步骤的 BeforeDelayMs 表示，不再使用 AfterDelayMs。
    /// </summary>
    private int beforeDelayMs;

    public int BeforeDelayMs
    {
        get => beforeDelayMs;
        set => beforeDelayMs = value >= 0 ? value
            : throw new DomainValidationException("步骤前等待时间不能小于 0。");
    }

    public int Order { get; set; }

    /// <summary>
    /// 深拷贝步骤并分配到新的任务 ID，新增属性时只需维护此方法。
    /// </summary>
    public ClickStep Copy(Guid newTaskId)
    {
        return new ClickStep
        {
            Id = Guid.NewGuid(),
            TaskId = newTaskId,
            Name = Name,
            Enabled = Enabled,
            ActionType = ActionType,
            X = X,
            Y = Y,
            ClickType = ClickType,
            MouseClickCount = MouseClickCount,
            ClickIntervalMs = ClickIntervalMs,
            PressDurationMs = PressDurationMs,
            KeyName = KeyName,
            KeyPressCount = KeyPressCount,
            KeyIntervalMs = KeyIntervalMs,
            ShortcutKeys = ShortcutKeys,
            TextContent = TextContent,
            AutoFocusBeforeInput = AutoFocusBeforeInput,
            EndX = EndX,
            EndY = EndY,
            SwipeDurationMs = SwipeDurationMs,
            BeforeDelayMs = BeforeDelayMs,
            Order = Order
        };
    }

    /// <summary>
    /// 将分散坐标转换为值对象，便于执行引擎调用。
    /// </summary>
    public ScreenPoint ToPoint()
    {
        return new ScreenPoint(X, Y);
    }

    /// <summary>
    /// 校验步骤草稿配置，允许键盘按键名称暂时留空，便于用户稍后从键盘捕获。
    /// 数值范围不变量已由属性 setter 保证，此处仅执行类型相关校验。
    /// </summary>
    public void ValidateForSave()
    {
        if (string.IsNullOrWhiteSpace(Name))
        {
            throw new DomainValidationException("步骤名称不能为空。");
        }

        if (Order < 0)
        {
            throw new DomainValidationException("步骤顺序不能小于 0。");
        }

        DispatchActionValidation();
    }

    public void Validate()
    {
        ValidateForSave();

        if (ActionType == InputActionType.KeyboardPress)
        {
            ValidateKeyboardPress(requireKeyName: true);
        }
    }

    /// <summary>
    /// 按 ActionType 分派类型专属校验逻辑，新增动作类型只需注册新 validator 即可。
    /// </summary>
    private static readonly Dictionary<InputActionType, Action<ClickStep>> ActionValidators = new()
    {
        [InputActionType.MouseClick] = step =>
        {
            if (step.MouseClickCount > 10000)
                throw new DomainValidationException("鼠标点击次数不能超过 10000。");
        },
        [InputActionType.Swipe] = step =>
        {
            if (step.X == step.EndX && step.Y == step.EndY)
                throw new DomainValidationException("滑动起点和终点不能相同。");
        },
        [InputActionType.KeyboardPress] = step =>
        {
            if (step.KeyPressCount > 10000)
                throw new DomainValidationException("键盘连按次数不能超过 10000。");
        },
        [InputActionType.KeyboardShortcut] = step =>
        {
            if (string.IsNullOrWhiteSpace(step.ShortcutKeys))
                throw new DomainValidationException("组合键步骤必须填写组合键。");
            var keyParts = step.ShortcutKeys
                .Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            if (keyParts.Length < 2)
                throw new DomainValidationException("组合键至少需要一个修饰键和一个主按键，例如 Ctrl+C。");
            if (IsModifierKeyName(keyParts[^1]))
                throw new DomainValidationException("组合键最后一个按键必须是主按键，不能只是修饰键。");
        },
        [InputActionType.TextInput] = step =>
        {
            if (string.IsNullOrEmpty(step.TextContent))
                throw new DomainValidationException("文本输入步骤必须填写文本内容。");
            if (step.TextContent.Length > 10000)
                throw new DomainValidationException("文本输入内容不能超过 10000 个字符。");
        }
    };

    /// <summary>
    /// 分派到动作类型对应的校验器，类型未注册时放行。
    /// </summary>
    private void DispatchActionValidation()
    {
        if (ActionValidators.TryGetValue(ActionType, out var validator))
        {
            validator(this);
        }
    }

    /// <summary>
    /// 校验键盘步骤配置（执行前额外要求按键名称非空）。
    /// </summary>
    private void ValidateKeyboardPress(bool requireKeyName)
    {
        if (requireKeyName && string.IsNullOrWhiteSpace(KeyName))
        {
            throw new DomainValidationException("键盘步骤必须填写按键名称。");
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
