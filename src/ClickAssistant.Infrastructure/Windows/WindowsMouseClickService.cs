using System.Runtime.InteropServices;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Infrastructure.Windows;

/// <summary>
/// Windows 鼠标点击服务，首版使用屏幕绝对坐标。
/// </summary>
public sealed class WindowsMouseClickService : IMouseClickService
{
    private const uint MouseEventLeftDown = 0x0002;
    private const uint MouseEventLeftUp = 0x0004;
    private const uint MouseEventRightDown = 0x0008;
    private const uint MouseEventRightUp = 0x0010;

    /// <summary>
    /// 移动鼠标到目标坐标并执行指定点击。
    /// </summary>
    public async Task ClickAsync(ScreenPoint point, ClickType clickType, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        if (!SetCursorPos(point.X, point.Y))
        {
            throw new InvalidOperationException($"无法移动鼠标到坐标 {point}。");
        }

        await Task.Delay(40, cancellationToken);

        switch (clickType)
        {
            case ClickType.LeftSingle:
                SendLeftClick();
                break;
            case ClickType.LeftDouble:
                SendLeftClick();
                await Task.Delay(80, cancellationToken);
                SendLeftClick();
                break;
            case ClickType.RightSingle:
                SendRightClick();
                break;
            default:
                throw new ArgumentOutOfRangeException(nameof(clickType), clickType, "未知点击类型。");
        }
    }

    /// <summary>
    /// 发送左键单击事件。
    /// </summary>
    private static void SendLeftClick()
    {
        mouse_event(MouseEventLeftDown, 0, 0, 0, UIntPtr.Zero);
        mouse_event(MouseEventLeftUp, 0, 0, 0, UIntPtr.Zero);
    }

    /// <summary>
    /// 发送右键单击事件。
    /// </summary>
    private static void SendRightClick()
    {
        mouse_event(MouseEventRightDown, 0, 0, 0, UIntPtr.Zero);
        mouse_event(MouseEventRightUp, 0, 0, 0, UIntPtr.Zero);
    }

    [DllImport("user32.dll")]
    private static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    private static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
}
