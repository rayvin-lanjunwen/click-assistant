using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 鼠标坐标服务，屏蔽底层 Windows API 调用。
/// </summary>
public interface ICursorPositionService
{
    ScreenPoint GetCurrentPosition();
}
