using System.Windows;
using System.Windows.Controls;

namespace ClickAssistant.App;

/// <summary>
/// 液态玻璃卡片控件，继承 ContentControl 以支持 ControlTemplate 多层级效果。
/// 提供 CornerRadius 依赖属性用于 TemplateBinding。
/// </summary>
public class GlassCard : ContentControl
{
    public static readonly DependencyProperty CornerRadiusProperty =
        Border.CornerRadiusProperty.AddOwner(typeof(GlassCard));

    public CornerRadius CornerRadius
    {
        get => (CornerRadius)GetValue(CornerRadiusProperty);
        set => SetValue(CornerRadiusProperty, value);
    }

    static GlassCard()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(GlassCard),
            new FrameworkPropertyMetadata(typeof(GlassCard)));
    }
}
