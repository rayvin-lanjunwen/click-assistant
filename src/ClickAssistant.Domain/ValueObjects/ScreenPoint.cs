namespace ClickAssistant.Domain.ValueObjects;

/// <summary>
/// 屏幕绝对坐标，首版不处理窗口相对坐标。
/// </summary>
public readonly record struct ScreenPoint(int X, int Y)
{
    /// <summary>
    /// 坐标展示文本，供界面和日志直接使用。
    /// </summary>
    public override string ToString()
    {
        return $"{X}, {Y}";
    }
}
