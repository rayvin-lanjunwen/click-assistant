namespace ClickAssistant.Domain.Exceptions;

/// <summary>
/// 领域校验异常，用于表达任务或步骤配置不满足执行规则。
/// </summary>
public sealed class DomainValidationException : Exception
{
    public DomainValidationException(string message)
        : base(message)
    {
    }
}
