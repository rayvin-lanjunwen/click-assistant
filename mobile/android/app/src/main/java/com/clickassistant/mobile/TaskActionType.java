package com.clickassistant.mobile;

/// <summary>
/// 步骤动作类型，对应电脑端 InputActionType 在移动端的可用子集。
/// 移动端不支持全局键盘注入，因此用“文本输入”对应电脑端文本输入能力，
/// 并新增“滑动”以匹配触控手势；点击对应电脑端鼠标点击。
/// </summary>
public enum TaskActionType {
    TAP("点击"),
    SWIPE("滑动"),
    WAIT("等待"),
    TEXT_INPUT("文本输入");

    private final String displayName;

    TaskActionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /// <summary>
    /// 根据名称安全转换为枚举，未知值回退到 TAP。
    /// </summary>
    public static TaskActionType fromName(String name) {
        if (name == null) {
            return TAP;
        }

        for (TaskActionType value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }

        return TAP;
    }
}
