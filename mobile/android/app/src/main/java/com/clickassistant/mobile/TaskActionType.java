package com.clickassistant.mobile;

import android.graphics.Color;

/// <summary>
/// 步骤动作类型，对应电脑端 InputActionType 在移动端的可用子集。
/// 移动端不支持全局键盘注入，因此用"文本输入"对应电脑端文本输入能力，
/// 并新增"滑动"以匹配触控手势；点击对应电脑端鼠标点击。
/// 不再提供独立的等待步骤类型，等待时间作为每个步骤的基础属性。
/// </summary>
public enum TaskActionType {
    TAP("点击"),
    LONG_PRESS("长按"),
    SWIPE("滑动"),
    TEXT_INPUT("文本输入");

    private final String displayName;

    TaskActionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /// <summary>
    /// 返回本类型对应的标记颜色。
    /// 使用与主界面一致的状态色：点击=蓝、长按=橙、滑动=绿、文本输入=紫。
    /// </summary>
    public int getColor() {
        switch (this) {
            case TAP:
                return Color.parseColor("#3978F6");
            case LONG_PRESS:
                return Color.parseColor("#E98732");
            case SWIPE:
                return Color.parseColor("#169B72");
            case TEXT_INPUT:
                return Color.parseColor("#7067D8");
            default:
                return Color.parseColor("#3978F6");
        }
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
