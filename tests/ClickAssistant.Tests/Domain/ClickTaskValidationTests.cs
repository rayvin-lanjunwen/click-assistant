using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.Exceptions;

namespace ClickAssistant.Tests.Domain;

public sealed class ClickTaskValidationTests
{
    [Fact]
    public void ValidateForSave_WhenNameIsBlank_ThrowsDomainValidationException()
    {
        var task = new ClickTask
        {
            Name = " ",
            RepeatCount = 1,
            StartDelayMs = 0
        };

        Assert.Throws<DomainValidationException>(task.ValidateForSave);
    }

    [Fact]
    public void ValidateForExecution_WhenTaskHasNoEnabledSteps_ThrowsDomainValidationException()
    {
        var task = new ClickTask
        {
            Name = "测试任务",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "禁用步骤",
                    Enabled = false
                }
            ]
        };

        Assert.Throws<DomainValidationException>(task.ValidateForExecution);
    }

    [Fact]
    public void Setter_WhenKeyboardPressCountIsZero_ThrowsImmediately()
    {
        // D5 后 setter 即时校验，非法值在赋值时就抛异常
        Assert.Throws<DomainValidationException>(() =>
        {
            var step = new ClickStep { KeyPressCount = 0 };
        });
    }

    [Fact]
    public void Setter_WhenMouseClickCountIsZero_ThrowsImmediately()
    {
        Assert.Throws<DomainValidationException>(() =>
        {
            var step = new ClickStep { MouseClickCount = 0 };
        });
    }

    [Fact]
    public void ValidateForSave_WhenKeyboardKeyNameIsMissing_DoesNotThrow()
    {
        var task = new ClickTask
        {
            Name = "键盘草稿",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "键盘步骤",
                    ActionType = InputActionType.KeyboardPress,
                    KeyName = string.Empty
                }
            ]
        };

        task.ValidateForSave();
    }

    [Fact]
    public void ValidateForExecution_WhenEnabledKeyboardKeyNameIsMissing_ThrowsDomainValidationException()
    {
        var task = new ClickTask
        {
            Name = "键盘任务",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "键盘步骤",
                    ActionType = InputActionType.KeyboardPress,
                    KeyName = string.Empty
                }
            ]
        };

        Assert.Throws<DomainValidationException>(task.ValidateForExecution);
    }

    [Fact]
    public void Validate_WhenShortcutIsMissingMainKey_ThrowsDomainValidationException()
    {
        var step = new ClickStep
        {
            Name = "组合键步骤",
            ActionType = InputActionType.KeyboardShortcut,
            ShortcutKeys = "Ctrl"
        };

        Assert.Throws<DomainValidationException>(step.Validate);
    }

    [Fact]
    public void Validate_WhenShortcutEndsWithModifier_ThrowsDomainValidationException()
    {
        var step = new ClickStep
        {
            Name = "组合键步骤",
            ActionType = InputActionType.KeyboardShortcut,
            ShortcutKeys = "Ctrl+Shift"
        };

        Assert.Throws<DomainValidationException>(step.Validate);
    }

    [Fact]
    public void Validate_WhenTextInputIsEmpty_ThrowsDomainValidationException()
    {
        var step = new ClickStep
        {
            Name = "文本步骤",
            ActionType = InputActionType.TextInput,
            TextContent = string.Empty
        };

        Assert.Throws<DomainValidationException>(step.Validate);
    }

    [Fact]
    public void ValidateForExecution_WhenTaskIsRunnable_DoesNotThrow()
    {
        var task = new ClickTask
        {
            Name = "可执行任务",
            RepeatCount = 1,
            StartDelayMs = 0,
            Steps =
            [
                new ClickStep
                {
                    Name = "鼠标步骤",
                    ActionType = InputActionType.MouseClick,
                    X = 10,
                    Y = 20
                }
            ]
        };

        task.ValidateForExecution();
    }
}
