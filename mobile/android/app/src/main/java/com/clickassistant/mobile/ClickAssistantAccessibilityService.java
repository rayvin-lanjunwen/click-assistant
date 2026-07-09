package com.clickassistant.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public final class ClickAssistantAccessibilityService extends AccessibilityService {
    private static volatile ClickAssistantAccessibilityService activeService;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile boolean stopRequested;

    public static boolean isActive() {
        return activeService != null;
    }

    public static boolean startTask(PrototypeTask task) {
        ClickAssistantAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }

        service.start(task);
        return true;
    }

    public static void requestStop() {
        ClickAssistantAccessibilityService service = activeService;
        if (service != null) {
            service.stop("已停止");
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;
        PrototypeTaskStore.saveLastStatus(this, "辅助功能服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 原型阶段只需要手势分发能力，暂不读取窗口内容。
    }

    @Override
    public void onInterrupt() {
        stop("已停止");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        stop("辅助功能服务已断开");
        activeService = null;
        return super.onUnbind(intent);
    }

    private void start(PrototypeTask task) {
        if (!task.isValid()) {
            PrototypeTaskStore.saveLastStatus(this, "执行失败：任务参数无效");
            return;
        }

        stopRequested = false;
        running = true;
        handler.removeCallbacksAndMessages(null);
        PrototypeTaskStore.saveLastStatus(this, ExecutionState.RUNNING.getDisplayName());
        handler.postDelayed(() -> runStep(task, 0), task.getStartDelayMs());
    }

    private void runStep(PrototypeTask task, int index) {
        if (stopRequested || !running) {
            stop("已停止");
            return;
        }

        if (index >= task.getRepeatCount()) {
            running = false;
            PrototypeTaskStore.saveLastStatus(this, ExecutionState.COMPLETED.getDisplayName());
            return;
        }

        dispatchTap(task, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                handler.postDelayed(() -> runStep(task, index + 1), task.getIntervalMs());
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                running = false;
                PrototypeTaskStore.saveLastStatus(
                    ClickAssistantAccessibilityService.this,
                    "执行失败：手势被系统取消");
            }
        });
    }

    private void dispatchTap(PrototypeTask task, GestureResultCallback callback) {
        Path path = new Path();
        path.moveTo(task.getX(), task.getY());

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 80);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        boolean accepted = dispatchGesture(gesture, callback, null);
        if (!accepted) {
            running = false;
            PrototypeTaskStore.saveLastStatus(this, "执行失败：系统拒绝点击手势");
        }
    }

    private void stop(String status) {
        stopRequested = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        PrototypeTaskStore.saveLastStatus(this, status);
    }
}
