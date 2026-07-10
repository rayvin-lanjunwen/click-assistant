package com.clickassistant.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class ClickAssistantAccessibilityService extends AccessibilityService {
    private static volatile ClickAssistantAccessibilityService activeService;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View coordinatePickerOverlay;
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

    public static boolean startCoordinatePick(int delayMs) {
        ClickAssistantAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }

        service.prepareCoordinatePick(delayMs);
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
        removeCoordinatePickerOverlay();
        stop("已停止");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        removeCoordinatePickerOverlay();
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
        removeCoordinatePickerOverlay();
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

    private void prepareCoordinatePick(int delayMs) {
        stopRequested = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        removeCoordinatePickerOverlay();

        int safeDelayMs = Math.max(0, delayMs);
        int delaySeconds = Math.max(1, safeDelayMs / 1000);
        PrototypeTaskStore.saveLastStatus(
            this,
            String.format(Locale.ROOT, "%d 秒后显示取点层，请切到目标界面", delaySeconds));
        handler.postDelayed(this::showCoordinatePickerOverlay, safeDelayMs);
    }

    private void showCoordinatePickerOverlay() {
        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            PrototypeTaskStore.saveLastStatus(this, "取点失败：无法创建屏幕取点层");
            return;
        }

        removeCoordinatePickerOverlay();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(48, 0, 0, 0));
        overlay.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                savePickedCoordinate(Math.round(event.getRawX()), Math.round(event.getRawY()));
            }

            return true;
        });

        TextView guideText = new TextView(this);
        guideText.setText("点一下要保存的位置\n左上角为 (0,0)，X 向右增大，Y 向下增大");
        guideText.setTextColor(Color.WHITE);
        guideText.setTextSize(16);
        guideText.setGravity(Gravity.CENTER);
        guideText.setPadding(24, 24, 24, 24);
        guideText.setBackgroundColor(Color.argb(220, 0, 0, 0));
        overlay.addView(guideText, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP));

        Button cancelButton = new Button(this);
        cancelButton.setText("取消取点");
        cancelButton.setOnClickListener(view -> cancelCoordinatePick());
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        cancelParams.setMargins(24, 24, 24, 48);
        overlay.addView(cancelButton, cancelParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        coordinatePickerOverlay = overlay;
        windowManager.addView(overlay, params);
        PrototypeTaskStore.saveLastStatus(this, "取点中：请点击目标位置");
    }

    private void savePickedCoordinate(int x, int y) {
        PrototypeTask current = PrototypeTaskStore.loadTask(this);
        PrototypeTask updatedTask = new PrototypeTask(
            current.getName(),
            x,
            y,
            current.getRepeatCount(),
            current.getStartDelayMs(),
            current.getIntervalMs());

        PrototypeTaskStore.saveTask(this, updatedTask);
        PrototypeTaskStore.saveLastStatus(
            this,
            String.format(Locale.ROOT, "已选择坐标：X=%d，Y=%d", x, y));
        removeCoordinatePickerOverlay();
        Toast.makeText(
            this,
            String.format(Locale.ROOT, "已保存坐标：X=%d，Y=%d", x, y),
            Toast.LENGTH_SHORT).show();
        openMainActivity();
    }

    private void cancelCoordinatePick() {
        removeCoordinatePickerOverlay();
        PrototypeTaskStore.saveLastStatus(this, "已取消坐标拾取");
        openMainActivity();
    }

    private void removeCoordinatePickerOverlay() {
        if (coordinatePickerOverlay == null) {
            return;
        }

        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager != null) {
            try {
                windowManager.removeView(coordinatePickerOverlay);
            } catch (IllegalArgumentException ignored) {
                // 视图可能已经被系统移除，清空引用即可。
            }
        }

        coordinatePickerOverlay = null;
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
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
        removeCoordinatePickerOverlay();
        PrototypeTaskStore.saveLastStatus(this, status);
    }
}
