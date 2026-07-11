using ClickAssistant.Domain.Exceptions;

namespace ClickAssistant.Domain.Entities;

/// <summary>
/// 点击任务，聚合任务基础配置和输入步骤。
/// </summary>
public sealed class ClickTask
{
    private int repeatCount = 1;
    private int startDelayMs = 3000;

    public Guid Id { get; init; } = Guid.NewGuid();

    public string Name { get; set; } = "新建点击任务";

    public string Description { get; set; } = string.Empty;

    public bool Enabled { get; set; } = true;

    public int RepeatCount
    {
        get => repeatCount;
        set => repeatCount = value >= 1 ? value
            : throw new DomainValidationException("重复次数必须大于或等于 1。");
    }

    public int StartDelayMs
    {
        get => startDelayMs;
        set => startDelayMs = value >= 0 ? value
            : throw new DomainValidationException("开始延迟不能小于 0。");
    }

    public List<ClickStep> Steps { get; set; } = [];

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// 校验任务保存规则，保存前调用。
    /// RepeatCount/StartDelayMs 的不变量已由 setter 保证，此处仅校验名称和步骤。
    /// </summary>
    public void ValidateForSave()
    {
        if (string.IsNullOrWhiteSpace(Name))
        {
            throw new DomainValidationException("任务名称不能为空。");
        }

        foreach (var step in Steps)
        {
            step.ValidateForSave();
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

        foreach (var step in Steps.Where(step => step.Enabled))
        {
            step.Validate();
        }
    }

    /// <summary>
    /// 复制任务并重置标识，便于用户基于已有任务快速创建新任务。
    /// 步骤通过 Copy() 方法深拷贝，新增属性时只需修改 Copy() 方法。
    /// </summary>
    public ClickTask Duplicate()
    {
        var now = DateTime.UtcNow;
        return new ClickTask
        {
            Id = Guid.NewGuid(),
            Name = $"{Name} 副本",
            Description = Description,
            Enabled = Enabled,
            RepeatCount = RepeatCount,
            StartDelayMs = StartDelayMs,
            CreatedAt = now,
            UpdatedAt = now,
            Steps = Steps
                .OrderBy(step => step.Order)
                .Select(step => step.Copy(Id))
                .ToList()
        };
    }
}
