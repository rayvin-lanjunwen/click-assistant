using System.Windows.Input;

namespace ClickAssistant.App.Commands;

/// <summary>
/// 异步命令封装，用于绑定保存、删除、启动任务等异步操作。
/// </summary>
public sealed class AsyncRelayCommand : ICommand
{
    private readonly Func<Task> executeAsync;
    private readonly Func<bool>? canExecute;
    private bool isExecuting;

    public AsyncRelayCommand(Func<Task> executeAsync, Func<bool>? canExecute = null)
    {
        this.executeAsync = executeAsync;
        this.canExecute = canExecute;
    }

    public event EventHandler? CanExecuteChanged;

    /// <summary>
    /// 异步命令执行期间禁止重复触发同一个命令。
    /// </summary>
    public bool CanExecute(object? parameter)
    {
        return !isExecuting && (canExecute?.Invoke() ?? true);
    }

    /// <summary>
    /// 执行异步操作，并在结束后刷新命令状态。
    /// </summary>
    public async void Execute(object? parameter)
    {
        if (!CanExecute(parameter))
        {
            return;
        }

        try
        {
            isExecuting = true;
            NotifyCanExecuteChanged();
            await executeAsync();
        }
        finally
        {
            isExecuting = false;
            NotifyCanExecuteChanged();
        }
    }

    /// <summary>
    /// 通知界面重新计算按钮可用状态。
    /// </summary>
    public void NotifyCanExecuteChanged()
    {
        CanExecuteChanged?.Invoke(this, EventArgs.Empty);
    }
}
