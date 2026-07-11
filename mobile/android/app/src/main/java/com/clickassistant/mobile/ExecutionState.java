package com.clickassistant.mobile;

/// <summary>
/// 执行状态，对应电脑端 ExecutionStatus 的移动端子集。
/// 在手机端增加“准备执行”与“已暂停”，覆盖启动、暂停、继续、停止全流程。
/// </summary>
public enum ExecutionState {
    IDLE("未运行"),
    SAVED("任务已保存"),
    PREPARING("准备执行"),
    RUNNING("运行中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    STOPPED("已停止"),
    FAILED("执行失败");

    private final String displayName;

    ExecutionState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
