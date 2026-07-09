using ClickAssistant.Domain.Enums;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.Application.Abstractions;

/// <summary>
/// 鼠标点击服务，执行引擎只依赖该抽象。
/// </summary>
public interface IMouseClickService
{
    Task ClickAsync(ScreenPoint point, ClickType clickType, CancellationToken cancellationToken = default);
}
