namespace ClickAssistant.App.Helpers;

/// <summary>
/// 时间格式化工具，统一毫秒/秒的可读显示格式。
/// </summary>
public static class TimeFormattingHelper
{
    /// <summary>
    /// 将毫秒值格式化为中文可读字符串：
    /// 整秒显示为 "N 秒"，否则显示为 "N 毫秒"。
    /// </summary>
    public static string FormatMilliseconds(int milliseconds)
    {
        return milliseconds >= 1000 && milliseconds % 1000 == 0
            ? $"{milliseconds / 1000} 秒"
            : $"{milliseconds} 毫秒";
    }
}
