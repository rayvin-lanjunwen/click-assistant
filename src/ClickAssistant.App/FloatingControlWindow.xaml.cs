using System.Windows;
using System.Windows.Input;

namespace ClickAssistant.App;

/// <summary>
/// 任务执行时显示的基础悬浮控制窗。
/// </summary>
public partial class FloatingControlWindow : Window
{
    public FloatingControlWindow()
    {
        InitializeComponent();
    }

    private void HandleMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ButtonState != MouseButtonState.Pressed)
        {
            return;
        }

        try
        {
            DragMove();
        }
        catch (InvalidOperationException)
        {
            // Button clicks can briefly conflict with DragMove; keeping the click responsive is enough.
        }
    }
}
