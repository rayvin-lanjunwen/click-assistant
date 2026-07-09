using ClickAssistant.Domain.Exceptions;

namespace ClickAssistant.Domain.Entities;

/// <summary>
/// 点击任务，聚合任务基础配置和输入步骤。
/// </summary>
public sealed class ClickTask
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public string Name { get; set; } = "新建点击任务";

    public string Description { get; set; } = string.Empty;

    public bool Enabled { get; set; } = true;

    public int RepeatCount { get; set; } = 1;

    public int StartDelayMs { get; set; } = 3000;

    public List<ClickStep> Steps { get; set; } = [];

    public DateTime CreatedAt { get; set; } = DateTime.Now;

    public DateTime UpdatedAt { get; set; } = DateTime.Now;

    /// <summary>
    /// 校验任务保存规则，保存前调用。
    /// </summary>
    public void ValidateForSave()
    {
        if (string.IsNullOrWhiteSpace(Name))
        {
            throw new DomainValidationException("任务名称不能为空。");
        }

        if (RepeatCount < 1)
        {
            throw new DomainValidationException("重复次数必须大于或等于 1。");
        }

        if (StartDelayMs < 0)
        {
            throw new DomainValidationException("开始延迟不能小于 0。");
        }

        foreach (var step in Steps)
        {
            step.Validate();
        }
    }

    /// <summary>
    /// 校验任务执行规则，启动前调用。
    /// </summary>
    public void ValidateForExecution()
    {
        ValidateForSave();

        if (!Enabled)
        {
            throw new DomainValidationException("任务已禁用，不能执行。");
        }

        if (!Steps.Any(step => step.Enabled))
        {
            throw new DomainValidationException("任务至少需要一个启用的输入步骤。");
        }
    }

    /// <summary>
    /// 复制任务并重置标识，便于用户基于已有任务快速创建新任务。
    /// </summary>
    public ClickTask Duplicate()
    {
        var now = DateTime.Now;
        var duplicatedTask = new ClickTask
        {
            Id = Guid.NewGuid(),
            Name = $"{Name} 副本",
            Description = Description,
            Enabled = Enabled,
            RepeatCount = RepeatCount,
            StartDelayMs = StartDelayMs,
            CreatedAt = now,
            UpdatedAt = now
        };

        duplicatedTask.Steps = Steps
            .OrderBy(step => step.Order)
            .Select(step => new ClickStep
            {
                Id = Guid.NewGuid(),
                TaskId = duplicatedTask.Id,
                Name = step.Name,
                Enabled = step.Enabled,
                ActionType = step.ActionType,
                X = step.X,
                Y = step.Y,
                ClickType = step.ClickType,
                KeyName = step.KeyName,
                KeyPressCount = step.KeyPressCount,
                KeyIntervalMs = step.KeyIntervalMs,
                BeforeDelayMs = step.BeforeDelayMs,
                AfterDelayMs = step.AfterDelayMs,
                Order = step.Order
            })
            .ToList();

        return duplicatedTask;
    }
}
