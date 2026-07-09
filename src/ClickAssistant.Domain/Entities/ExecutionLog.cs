using ClickAssistant.Domain.Enums;

namespace ClickAssistant.Domain.Entities;

/// <summary>
/// 执行日志，记录一次任务运行结果。
/// </summary>
public sealed class ExecutionLog
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid TaskId { get; set; }

    public ExecutionStatus Status { get; set; }

    public DateTime StartedAt { get; set; }

    public DateTime? EndedAt { get; set; }

    public string Message { get; set; } = string.Empty;
}
