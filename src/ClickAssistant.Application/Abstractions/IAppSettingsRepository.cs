namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 应用设置仓储，用于保存轻量键值配置。
/// </summary>
public interface IAppSettingsRepository
{
    Task<string?> GetValueAsync(string key, CancellationToken cancellationToken = default);

    Task SetValueAsync(string key, string value, CancellationToken cancellationToken = default);
}
