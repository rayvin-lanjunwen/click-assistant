using ClickAssistant.Application.Abstractions;

namespace ClickAssistant.Infrastructure.Persistence;

/// <summary>
/// 基于 SQLite app_settings 表的应用设置仓储。
/// </summary>
public sealed class SqliteAppSettingsRepository : IAppSettingsRepository
{
    private readonly SqliteConnectionFactory connectionFactory;

    public SqliteAppSettingsRepository(SqliteConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }

    /// <summary>
    /// 读取指定设置值，未保存时返回 null。
    /// </summary>
    public async Task<string?> GetValueAsync(string key, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT value FROM app_settings WHERE key = $key;";
        command.Parameters.AddWithValue("$key", key);

        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value as string;
    }

    /// <summary>
    /// 保存或更新指定设置值。
    /// </summary>
    public async Task SetValueAsync(string key, string value, CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO app_settings (key, value, updated_at)
            VALUES ($key, $value, $updatedAt)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value,
                updated_at = excluded.updated_at;
            """;
        command.Parameters.AddWithValue("$key", key);
        command.Parameters.AddWithValue("$value", value);
        command.Parameters.AddWithValue("$updatedAt", DateTime.UtcNow.ToString("O"));

        await command.ExecuteNonQueryAsync(cancellationToken);
    }
}
