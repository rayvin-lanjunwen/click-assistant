namespace ClickAssistant.Domain.Enums;

/// <summary>
/// 任务执行状态，用于驱动界面按钮可用性和日志记录。
/// </summary>
public enum ExecutionStatus
{
    Idle = 0,
    Starting = 1,
    Running = 2,
    Paused = 3,
    Completed = 4,
    Stopped = 5,
    Failed = 6
}
