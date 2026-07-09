using System.Windows.Input;

namespace ClickAssistant.App.Commands;

/// <summary>
/// 同步命令封装，用于绑定不需要异步等待的界面操作。
/// </summary>
public sealed class RelayCommand : ICommand
{
    private readonly Action execute;
    private readonly Func<bool>? canExecute;

    public RelayCommand(Action execute, Func<bool>? canExecute = null)
    {
        this.execute = execute;
        this.canExecute = canExecute;
    }

    public event EventHandler? CanExecuteChanged;

    /// <summary>
    /// 判断当前命令是否可以执行。
    /// </summary>
    public bool CanExecute(object? parameter)
    {
        return canExecute?.Invoke() ?? true;
    }

    /// <summary>
    /// 执行绑定的同步操作。
    /// </summary>
    public void Execute(object? parameter)
    {
        execute();
    }

    /// <summary>
    /// 通知界面重新计算按钮可用状态。
    /// </summary>
    public void NotifyCanExecuteChanged()
    {
        CanExecuteChanged?.Invoke(this, EventArgs.Empty);
    }
}
