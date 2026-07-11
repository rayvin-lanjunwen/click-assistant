using System.Globalization;
using System.Windows.Data;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;

namespace ClickAssistant.App.Converters;

/// <summary>
/// 将步骤配置转换为任务库列表中的可读摘要，避免只显示内部枚举名称。
/// </summary>
public sealed class StepSummaryConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is not ClickStep step)
        {
            return "未选择步骤";
        }

        var enabledPrefix = step.Enabled ? string.Empty : "已停用 · ";
        return enabledPrefix + (step.ActionType switch
        {
            InputActionType.MouseClick => $"鼠标点击：X={step.X}，Y={step.Y} · {ToClickTypeText(step.ClickType)} · {step.MouseClickCount} 次 · 间隔 {FormatMilliseconds(step.AfterDelayMs)}",
            InputActionType.KeyboardPress => $"键盘按键：{ToKeyText(step.KeyName)} · 连按 {Math.Max(1, step.KeyPressCount)} 次 · 间隔 {FormatMilliseconds(step.KeyIntervalMs)}",
            InputActionType.KeyboardShortcut => $"组合键：{ToKeyText(step.ShortcutKeys)}",
            InputActionType.TextInput => $"文本输入：{ToTextPreview(step.TextContent)} · 字符间隔 {FormatMilliseconds(step.KeyIntervalMs)}",
            _ => "未知步骤"
        });
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
    {
        throw new NotSupportedException();
    }

    private static string ToClickTypeText(ClickType clickType)
    {
        return clickType switch
        {
            ClickType.LeftDouble => "左键双击",
            ClickType.RightSingle => "右键单击",
            _ => "左键单击"
        };
    }

    private static string ToKeyText(string value)
    {
        return string.IsNullOrWhiteSpace(value) ? "未设置" : value.Trim();
    }

    private static string ToTextPreview(string value)
    {
        if (string.IsNullOrEmpty(value))
        {
            return "未设置";
        }

        var normalized = value.ReplaceLineEndings(" ");
        return normalized.Length <= 12 ? normalized : $"{normalized[..12]}...";
    }

    private static string FormatMilliseconds(int milliseconds)
    {
        return milliseconds >= 1000 && milliseconds % 1000 == 0
            ? $"{milliseconds / 1000} 秒"
            : $"{milliseconds} 毫秒";
    }
}
