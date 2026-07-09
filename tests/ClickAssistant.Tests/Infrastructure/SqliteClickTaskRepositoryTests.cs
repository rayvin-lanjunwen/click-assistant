using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Infrastructure.Persistence;
using Microsoft.Data.Sqlite;

namespace ClickAssistant.Tests.Infrastructure;

public sealed class SqliteClickTaskRepositoryTests
{
    [Fact]
    public async Task SaveGetAndDeleteAsync_ShouldPersistTaskAndSteps()
    {
        var databasePath = Path.Combine(
            Path.GetTempPath(),
            "ClickAssistant.Tests",
            $"{Guid.NewGuid():N}.db");

        try
        {
            var connectionFactory = new SqliteConnectionFactory(databasePath);
            var databaseInitializer = new DatabaseInitializer(connectionFactory);
            await databaseInitializer.InitializeAsync();

            var repository = new SqliteClickTaskRepository(connectionFactory);
            var task = CreateTask();

            await repository.SaveAsync(task);

            var loadedTask = await repository.GetByIdAsync(task.Id);
            Assert.NotNull(loadedTask);
            Assert.Equal(task.Name, loadedTask.Name);
            Assert.Equal(task.Description, loadedTask.Description);
            Assert.Equal(task.RepeatCount, loadedTask.RepeatCount);
            Assert.Equal(task.StartDelayMs, loadedTask.StartDelayMs);
            Assert.Collection(
                loadedTask.Steps,
                mouseStep =>
                {
                    Assert.Equal(InputActionType.MouseClick, mouseStep.ActionType);
                    Assert.Equal(120, mouseStep.X);
                    Assert.Equal(240, mouseStep.Y);
                    Assert.Equal(ClickType.LeftDouble, mouseStep.ClickType);
                },
                keyboardStep =>
                {
                    Assert.Equal(InputActionType.KeyboardPress, keyboardStep.ActionType);
                    Assert.Equal("Enter", keyboardStep.KeyName);
                    Assert.Equal(3, keyboardStep.KeyPressCount);
                    Assert.Equal(80, keyboardStep.KeyIntervalMs);
                });

            await repository.DeleteAsync(task.Id);

            var deletedTask = await repository.GetByIdAsync(task.Id);
            Assert.Null(deletedTask);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            DeleteDatabaseFile(databasePath);
        }
    }

    private static ClickTask CreateTask()
    {
        var taskId = Guid.NewGuid();
        return new ClickTask
        {
            Id = taskId,
            Name = "SQLite 仓储测试任务",
            Description = "验证任务和步骤可以保存、读取和删除。",
            RepeatCount = 2,
            StartDelayMs = 100,
            CreatedAt = DateTime.Now,
            UpdatedAt = DateTime.Now,
            Steps =
            [
                new ClickStep
                {
                    Id = Guid.NewGuid(),
                    TaskId = taskId,
                    Name = "鼠标步骤",
                    ActionType = InputActionType.MouseClick,
                    X = 120,
                    Y = 240,
                    ClickType = ClickType.LeftDouble,
                    BeforeDelayMs = 10,
                    AfterDelayMs = 20,
                    Order = 0
                },
                new ClickStep
                {
                    Id = Guid.NewGuid(),
                    TaskId = taskId,
                    Name = "键盘步骤",
                    ActionType = InputActionType.KeyboardPress,
                    KeyName = "Enter",
                    KeyPressCount = 3,
                    KeyIntervalMs = 80,
                    BeforeDelayMs = 30,
                    AfterDelayMs = 40,
                    Order = 1
                }
            ]
        };
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
