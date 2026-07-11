using ClickAssistant.Infrastructure.Persistence;
using Microsoft.Data.Sqlite;

namespace ClickAssistant.Tests.Infrastructure;

public sealed class SqliteAppSettingsRepositoryTests
{
    [Fact]
    public async Task SetAndGetAsync_ShouldPersistAndRetrieveValue()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteAppSettingsRepository(connectionFactory);

            // 写入
            await repository.SetValueAsync("StopHotkey", "Ctrl+Alt+S");

            // 读取
            var value = await repository.GetValueAsync("StopHotkey");
            Assert.Equal("Ctrl+Alt+S", value);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task GetValueAsync_WhenKeyDoesNotExist_ShouldReturnNull()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteAppSettingsRepository(connectionFactory);

            var value = await repository.GetValueAsync("NonExistentKey");
            Assert.Null(value);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task SetValueAsync_WhenKeyAlreadyExists_ShouldUpdateInPlace()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteAppSettingsRepository(connectionFactory);

            await repository.SetValueAsync("Theme", "Light");
            await repository.SetValueAsync("Theme", "Dark");

            var value = await repository.GetValueAsync("Theme");
            Assert.Equal("Dark", value);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task SetAndGetAsync_WhenEmptyStringValue_ShouldPersistAndRetrieve()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteAppSettingsRepository(connectionFactory);

            await repository.SetValueAsync("EmptySetting", string.Empty);

            var value = await repository.GetValueAsync("EmptySetting");
            Assert.Equal(string.Empty, value);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    private static string CreateTempDatabasePath()
    {
        return Path.Combine(
            Path.GetTempPath(),
            "ClickAssistant.Tests",
            $"{Guid.NewGuid():N}.db");
    }

    private static void DeleteDatabaseFile(string databasePath)
    {
        foreach (var path in new[] { databasePath, $"{databasePath}-wal", $"{databasePath}-shm" })
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
    }
}
