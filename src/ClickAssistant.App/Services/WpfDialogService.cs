using System.Windows;
using ClickAssistant.Application.Abstractions;

namespace ClickAssistant.App.Services;

/// <summary>
/// WPF 对话框服务实现，将平台依赖隔离在 View 层。
/// </summary>
public sealed class WpfDialogService : IDialogService
{
    /// <inheritdoc />
    public bool Confirm(string message, string title)
    {
        return MessageBox.Show(message, title, MessageBoxButton.YesNo, MessageBoxImage.Warning)
            == MessageBoxResult.Yes;
    }

    /// <inheritdoc />
    public void ShowWarning(string message, string title)
    {
        MessageBox.Show(message, title, MessageBoxButton.OK, MessageBoxImage.Warning);
    }

    /// <inheritdoc />
    public void ShowError(string message, string title)
    {
        MessageBox.Show(message, title, MessageBoxButton.OK, MessageBoxImage.Error);
    }
}
