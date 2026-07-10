using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 鼠标点击可视化事件参数，用于在界面层展示点击位置提示。
/// </summary>
public sealed class MouseClickVisualEventArgs : EventArgs
{
    public MouseClickVisualEventArgs(ScreenPoint point, ClickType clickType)
    {
        Point = point;
        ClickType = clickType;
    }

    public ScreenPoint Point { get; }

    public ClickType ClickType { get; }
}
