using ClickAssistant.Domain.Enums;

namespace ClickAssistant.App.ViewModels;

/// <summary>
/// 输入动作类型下拉选项，保留领域枚举值并提供中文显示名。
/// </summary>
public sealed record InputActionTypeOption(InputActionType Value, string DisplayName);
