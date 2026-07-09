using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.Exceptions;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Domain.Entities;

/// <summary>
/// 输入步骤，表示任务中的一次鼠标或键盘动作。
/// </summary>
public sealed class ClickStep
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid TaskId { get; set; }

    public string Name { get; set; } = "新步骤";

    public bool Enabled { get; set; } = true;

    public InputActionType ActionType { get; set; } = InputActionType.MouseClick;

    public int X { get; set; }

    public int Y { get; set; }

    public ClickType ClickType { get; set; } = ClickType.LeftSingle;

    public string KeyName { get; set; } = "A";

    public int KeyPressCount { get; set; } = 1;

    public int KeyIntervalMs { get; set; } = 100;

    public int BeforeDelayMs { get; set; }

    public int AfterDelayMs { get; set; } = 500;

    public int Order { get; set; }

    /// <summary>
    /// 将分散坐标转换为值对象，便于执行引擎调用。
    /// </summary>
    public ScreenPoint ToPoint()
    {
        return new ScreenPoint(X, Y);
    }

    /// <summary>
    /// 校验步骤配置，避免执行过程中才发现基础数据错误。
    /// </summary>
    public void Validate()
    {
        if (string.IsNullOrWhiteSpace(Name))
        {
            throw new DomainValidationException("步骤名称不能为空。");
        }

        if (BeforeDelayMs < 0 || AfterDelayMs < 0)
        {
            throw new DomainValidationException("步骤等待时间不能小于 0。");
        }

        if (KeyIntervalMs < 0)
        {
            throw new DomainValidationException("键盘连按间隔不能小于 0。");
        }

        if (ActionType == InputActionType.KeyboardPress)
        {
            ValidateKeyboardPress();
        }

        if (Order < 0)
        {
            throw new DomainValidationException("步骤顺序不能小于 0。");
        }
    }

    /// <summary>
    /// 校验键盘步骤配置，避免执行时无法识别按键或产生失控连按。
    /// </summary>
    private void ValidateKeyboardPress()
    {
        if (string.IsNullOrWhiteSpace(KeyName))
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
}
