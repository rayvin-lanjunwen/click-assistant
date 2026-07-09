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
    public void Validate_WhenKeyboardPressCountIsInvalid_ThrowsDomainValidationException()
    {
        var step = new ClickStep
        {
            Name = "键盘步骤",
            ActionType = InputActionType.KeyboardPress,
            KeyName = "A",
            KeyPressCount = 0
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
