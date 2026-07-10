using System.Windows;
using System.Windows.Media.Animation;

namespace ClickAssistant.App;

/// <summary>
/// 透明置顶点击提示窗，在真实鼠标点击位置显示箭头和扩散动效。
/// </summary>
public partial class MouseClickVisualWindow : Window
{
    private readonly Storyboard storyboard;

    public MouseClickVisualWindow()
    {
        InitializeComponent();
        storyboard = CreateStoryboard();
    }

    public void ShowAt(int x, int y)
    {
        Left = x - 26;
        Top = y - 14;

        if (!IsVisible)
        {
            Show();
        }

        Visibility = Visibility.Visible;
        ActivateTopmost();
        storyboard.Stop(this);
        storyboard.Begin(this, true);
    }

    private void ActivateTopmost()
    {
        Topmost = false;
        Topmost = true;
    }

    private Storyboard CreateStoryboard()
    {
        var animation = new Storyboard
        {
            Duration = TimeSpan.FromMilliseconds(560),
            FillBehavior = FillBehavior.Stop
        };

        AddDoubleAnimation(animation, RootVisual, "Opacity", 0, 1, 70);
        AddDoubleAnimation(animation, RootVisual, "Opacity", 1, 0, 560, 190);
        AddDoubleAnimation(animation, RootScale, "ScaleX", 0.88, 1.08, 150);
        AddDoubleAnimation(animation, RootScale, "ScaleY", 0.88, 1.08, 150);
        AddDoubleAnimation(animation, RootScale, "ScaleX", 1.08, 1, 260, 150);
        AddDoubleAnimation(animation, RootScale, "ScaleY", 1.08, 1, 260, 150);
        AddDoubleAnimation(animation, PulseScale, "ScaleX", 0.45, 1.35, 520);
        AddDoubleAnimation(animation, PulseScale, "ScaleY", 0.45, 1.35, 520);
        AddDoubleAnimation(animation, PulseRing, "Opacity", 0.58, 0, 520);

        animation.Completed += (_, _) => Visibility = Visibility.Hidden;
        return animation;
    }

    private static void AddDoubleAnimation(
        Storyboard storyboard,
        DependencyObject target,
        string propertyPath,
        double from,
        double to,
        int durationMs,
        int beginMs = 0)
    {
        var animation = new DoubleAnimation
        {
            From = from,
            To = to,
            BeginTime = TimeSpan.FromMilliseconds(beginMs),
            Duration = TimeSpan.FromMilliseconds(durationMs),
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
        };

        Storyboard.SetTarget(animation, target);
        Storyboard.SetTargetProperty(animation, new PropertyPath(propertyPath));
        storyboard.Children.Add(animation);
    }
}
