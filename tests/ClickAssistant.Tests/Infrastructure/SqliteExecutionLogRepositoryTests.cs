using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Infrastructure.Persistence;
using Microsoft.Data.Sqlite;

namespace ClickAssistant.Tests.Infrastructure;

public sealed class SqliteExecutionLogRepositoryTests
{
    [Fact]
    public async Task AddAndGetRecentAsync_ShouldPersistAndRetrieveLogs()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteExecutionLogRepository(connectionFactory);

            var taskId = Guid.NewGuid();
            var log1 = new ExecutionLog
            {
                Id = Guid.NewGuid(),
                TaskId = taskId,
                Status = ExecutionStatus.Running,
                StartedAt = DateTime.UtcNow,
                Message = "任务开始执行"
            };

            var log2 = new ExecutionLog
            {
                Id = Guid.NewGuid(),
                TaskId = taskId,
                Status = ExecutionStatus.Stopped,
                StartedAt = DateTime.UtcNow.AddSeconds(5),
                EndedAt = DateTime.UtcNow.AddSeconds(10),
                Message = "任务已停止"
            };

            await repository.AddAsync(log1);
            await repository.AddAsync(log2);

            var recentLogs = await repository.GetRecentAsync(10);

            Assert.NotNull(recentLogs);
            Assert.True(recentLogs.Count >= 2);

            // 最近日志排前面，所以 log2（更晚 startedAt）应在前
            var foundStopped = recentLogs.FirstOrDefault(l => l.Id == log2.Id);
            Assert.NotNull(foundStopped);
            Assert.Equal(ExecutionStatus.Stopped, foundStopped.Status);
            Assert.Equal("任务已停止", foundStopped.Message);

            var foundRunning = recentLogs.FirstOrDefault(l => l.Id == log1.Id);
            Assert.NotNull(foundRunning);
            Assert.Equal(ExecutionStatus.Running, foundRunning.Status);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task GetRecentAsync_WhenLimitApplied_ShouldReturnOnlyRequestedCount()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteExecutionLogRepository(connectionFactory);
            var taskId = Guid.NewGuid();

            for (var i = 0; i < 5; i++)
            {
                await repository.AddAsync(new ExecutionLog
                {
                    Id = Guid.NewGuid(),
                    TaskId = taskId,
                    Status = ExecutionStatus.Completed,
                    StartedAt = DateTime.UtcNow.AddMinutes(-i),
                    Message = $"日志 #{i + 1}"
                });
            }

            var limited = await repository.GetRecentAsync(3);
            Assert.Equal(3, limited.Count);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    [Fact]
    public async Task AddAsync_WhenEndedAtIsNull_ShouldPersistWithoutCrash()
    {
        var databasePath = CreateTempDatabasePath();

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseMigrator = new DatabaseMigrator(connectionFactory);
            await databaseMigrator.InitializeAsync();

            var repository = new SqliteExecutionLogRepository(connectionFactory);

            var log = new ExecutionLog
            {
                Id = Guid.NewGuid(),
                TaskId = Guid.NewGuid(),
                Status = ExecutionStatus.Running,
                StartedAt = DateTime.UtcNow,
                EndedAt = null,
                Message = "未结束的任务"
            };

            await repository.AddAsync(log);

            var recent = await repository.GetRecentAsync(10);
            var found = Assert.Single(recent.Where(l => l.Id == log.Id));
            Assert.Null(found.EndedAt);
            Assert.Equal("未结束的任务", found.Message);
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
