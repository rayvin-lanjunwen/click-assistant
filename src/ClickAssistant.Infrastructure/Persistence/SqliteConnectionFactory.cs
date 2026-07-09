using Microsoft.Data.Sqlite;

namespace ClickAssistant.Infrastructure.Persistence;

/// <summary>
/// SQLite 连接工厂，统一管理数据库文件位置和连接字符串。
/// </summary>
public sealed class SqliteConnectionFactory
{
    private readonly string databasePath;

    public SqliteConnectionFactory(string? databasePath = null)
    {
        this.databasePath = string.IsNullOrWhiteSpace(databasePath)
            ? GetDefaultDatabasePath()
            : databasePath;

        var dataDirectory = Path.GetDirectoryName(this.databasePath);
        if (!string.IsNullOrWhiteSpace(dataDirectory))
        {
            Directory.CreateDirectory(dataDirectory);
        }
    }

    public string DatabasePath => databasePath;

    /// <summary>
    /// 创建一个新的数据库连接，调用方负责释放。
    /// </summary>
    public SqliteConnection CreateConnection()
    {
        var connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = databasePath
        }.ToString();

        return new SqliteConnection(connectionString);
    }

    /// <summary>
    /// 获取用户级默认数据库路径。
    /// </summary>
    private static string GetDefaultDatabasePath()
    {
        var appDataPath = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var dataDirectory = Path.Combine(appDataPath, "ClickAssistant");
        Directory.CreateDirectory(dataDirectory);

        return Path.Combine(dataDirectory, "clickassistant.db");
    }
}
