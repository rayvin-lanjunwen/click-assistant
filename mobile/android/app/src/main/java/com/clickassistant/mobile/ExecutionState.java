package com.clickassistant.mobile;

public enum ExecutionState {
    IDLE("未执行"),
    SAVED("任务已保存"),
    RUNNING("执行中"),
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
