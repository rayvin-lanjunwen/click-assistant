using System.Runtime.InteropServices;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Infrastructure.Windows;

/// <summary>
/// Windows 鼠标坐标读取服务。
/// </summary>
public sealed class WindowsCursorPositionService : ICursorPositionService
{
    /// <summary>
    /// 获取当前鼠标屏幕绝对坐标。
    /// </summary>
    public ScreenPoint GetCurrentPosition()
    {
        if (!GetCursorPos(out var point))
        {
            throw new InvalidOperationException("无法读取当前鼠标坐标。");
        }

        return new ScreenPoint(point.X, point.Y);
    }

    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out NativePoint point);

    [StructLayout(LayoutKind.Sequential)]
    private readonly struct NativePoint
    {
        public readonly int X;

        public readonly int Y;
    }
}
