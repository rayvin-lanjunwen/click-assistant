package com.clickassistant.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.Intent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/// <summary>
/// 辅助功能服务，负责在用户授权后分发手势执行任务。
/// 支持多步骤循环执行、暂停/继续/停止，以及点击与滑动坐标拾取。
/// 对应电脑端 ClickExecutionEngine 的移动端执行器。
/// </summary>
public final class ClickAssistantAccessibilityService extends AccessibilityService {
    private static volatile ClickAssistantAccessibilityService activeService;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View coordinatePickerOverlay;
    private TextView pickerGuideText;
    private PickerCursorView pickerCursorView;
    private Button pickerConfirmButton;
    private int pickerX;
    private int pickerY;
    private View executionControlOverlay;
    private TextView executionOverlayStatusText;
    private LinearLayout executionOverlayActions;
    private Button executionPauseButton;
    private Button executionCollapseButton;
    private boolean executionOverlayCollapsed;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile boolean stopRequested = false;

    private ClickTask currentTask;
    private int repeatIndex;
    private int stepIndex;

    // 坐标拾取状态
    private String pickTaskId;
    private String pickStepId;
    private TaskActionType pickMode = TaskActionType.TAP;
    private int pickPhase = 1;

    public static boolean isActive() {
        return activeService != null;
    }

    public static boolean startTask(ClickTask task) {
        ClickAssistantAccessibilityService service = activeService;
        if (service == null || service.running) {
            return false;
        }

        return service.start(task);
    }

    public static void requestStop() {
        ClickAssistantAccessibilityService service = activeService;
        if (service != null) {
            service.stop(ExecutionState.STOPPED.getDisplayName(), "已通过应用停止");
        }
    }

    public static void requestPause() {
        ClickAssistantAccessibilityService service = activeService;
        if (service != null) {
            service.pause();
        }
    }

    public static void requestResume() {
        ClickAssistantAccessibilityService service = activeService;
        if (service != null) {
            service.resume();
        }
    }

    public static boolean startCoordinatePick() {
        ClickAssistantAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }

        service.prepareCoordinatePick();
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;
        if (TaskStore.loadPickTarget(this) != null) {
            // 某些系统在应用退到后台后会重连辅助功能服务，需恢复尚未完成的取点请求。
            prepareCoordinatePick();
        } else {
            TaskStore.saveLastStatus(this, "辅助功能服务已连接");
        }
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // 原型阶段仅使用手势分发与聚焦节点读取（文本输入），不监听窗口内容变化。
    }

    @Override
    public void onInterrupt() {
        removeCoordinatePickerOverlay();
        stop(ExecutionState.STOPPED.getDisplayName(), "服务被系统中断");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        removeCoordinatePickerOverlay();
        stop(ExecutionState.STOPPED.getDisplayName(), "辅助功能服务已断开");
        activeService = null;
        return super.onUnbind(intent);
    }

    // ---------- 执行控制 ----------

    private boolean start(ClickTask task) {
        if (running) {
            return false;
        }

        ClickTask executionTask = task.snapshotForExecution();
        currentTask = executionTask;
        try {
            executionTask.validateForExecution();
        } catch (IllegalArgumentException ex) {
            failTask("执行失败：" + ex.getMessage());
            return false;
        }

        stopRequested = false;
        paused = false;
        running = true;
        repeatIndex = 0;
        stepIndex = 0;
        handler.removeCallbacksAndMessages(null);
        removeCoordinatePickerOverlay();
        showExecutionControlOverlay();

        logAndStatus(ExecutionState.PREPARING.getDisplayName(), "准备执行：" + executionTask.getName());
        handler.postDelayed(this::runLoop, Math.max(0, executionTask.getStartDelayMs()));
        return true;
    }

    private void pause() {
        if (!running || paused) {
            return;
        }

        paused = true;
        logAndStatus(ExecutionState.PAUSED.getDisplayName(), "已暂停，可在应用内继续");
    }

    private void resume() {
        if (!running || !paused) {
            return;
        }

        paused = false;
        logAndStatus(ExecutionState.RUNNING.getDisplayName(), "已继续：" + (currentTask == null ? "" : currentTask.getName()));
    }

    private void stop(String status, String message) {
        boolean hadActiveTask = running || paused || currentTask != null;
        stopRequested = true;
        running = false;
        paused = false;
        handler.removeCallbacksAndMessages(null);
        removeCoordinatePickerOverlay();
        if (hadActiveTask) {
            logAndStatus(status, message);
        } else {
            TaskStore.saveLastStatus(this, message);
        }
        removeExecutionControlOverlay();
        currentTask = null;
    }

    /// <summary>
    /// 统一完成任务并回收悬浮控制窗，避免残留可点击控件。
    /// </summary>
    private void completeTask(ClickTask task) {
        running = false;
        paused = false;
        stopRequested = false;
        logAndStatus(ExecutionState.COMPLETED.getDisplayName(), "已完成：" + task.getName());
        removeExecutionControlOverlay();
        currentTask = null;
    }

    /// <summary>
    /// 统一失败出口，保证失败后不会继续推进并最终误报完成。
    /// </summary>
    private void failTask(String message) {
        running = false;
        paused = false;
        stopRequested = true;
        handler.removeCallbacksAndMessages(null);
        logAndStatus(ExecutionState.FAILED.getDisplayName(), message);
        removeExecutionControlOverlay();
        currentTask = null;
    }

    /// <summary>
    /// 主执行循环：按“重复次数 × 步骤列表”推进，支持暂停轮询与停止中断。
    /// </summary>
    private void runLoop() {
        if (stopRequested || !running) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止");
            return;
        }

        if (paused) {
            handler.postDelayed(this::runLoop, 200);
            return;
        }

        ClickTask task = currentTask;
        if (task == null) {
            failTask("执行失败：任务状态已丢失");
            return;
        }

        List<TaskStep> steps = task.getSteps();
        if (repeatIndex >= task.getRepeatCount()) {
            completeTask(task);
            return;
        }

        if (stepIndex >= steps.size()) {
            repeatIndex++;
            stepIndex = 0;
            if (repeatIndex >= task.getRepeatCount()) {
                completeTask(task);
            } else {
                handler.post(this::runLoop);
            }
            return;
        }

        TaskStep step = steps.get(stepIndex);
        if (!step.isEnabled()) {
            stepIndex++;
            handler.post(this::runLoop);
            return;
        }

        long before = Math.max(0, step.getBeforeDelayMs());
        handler.postDelayed(() -> executeStep(step), before);
    }

    /// <summary>
    /// 根据步骤类型分发对应执行动作。
    /// </summary>
    private void executeStep(TaskStep step) {
        if (stopRequested || !running) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止");
            return;
        }

        if (paused) {
            handler.postDelayed(() -> executeStep(step), 200);
            return;
        }

        logAndStatus(ExecutionState.RUNNING.getDisplayName(), "执行步骤：" + step.getSummary());

        switch (step.getActionType()) {
            case TAP:
                dispatchTapSequence(step, 0);
                break;
            case SWIPE:
                dispatchSwipe(step);
                break;
            case TEXT_INPUT:
                dispatchText(step);
                break;
            default:
                finishStep(step);
                break;
        }
    }

    /// <summary>
    /// 点击序列：按 tapCount 重复点击同一坐标，步间使用 clickIntervalMs 间隔。
    /// </summary>
    private void dispatchTapSequence(TaskStep step, int tapIndex) {
        if (stopRequested || !running) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止");
            return;
        }

        if (paused) {
            handler.postDelayed(() -> dispatchTapSequence(step, tapIndex), 200);
            return;
        }

        if (tapIndex >= step.getTapCount()) {
            finishStep(step);
            return;
        }

        Path path = new Path();
        path.moveTo(step.getX(), step.getY());
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 80);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                int next = tapIndex + 1;
                if (next >= step.getTapCount()) {
                    finishStep(step);
                } else {
                    handler.postDelayed(
                            () -> dispatchTapSequence(step, next),
                            Math.max(0, step.getClickIntervalMs()));
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (!stopRequested && running) {
                    failTask("执行失败：点击手势被系统取消");
                }
            }
        }, null);

        if (!accepted) {
            failTask("执行失败：系统拒绝点击手势");
        }
    }

    /// <summary>
    /// 滑动手势：从起点到终点按 durationMs 完成一次滑动。
    /// </summary>
    private void dispatchSwipe(TaskStep step) {
        Path path = new Path();
        path.moveTo(step.getX(), step.getY());
        path.lineTo(step.getEndX(), step.getEndY());
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(1, step.getDurationMs()));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                finishStep(step);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (!stopRequested && running) {
                    failTask("执行失败：滑动手势被系统取消");
                }
            }
        }, null);

        if (!accepted) {
            failTask("执行失败：系统拒绝滑动手势");
        }
    }

    /// <summary>
    /// 文本输入：可自动点击目标位置获得焦点后，向输入框写入文本。
    /// </summary>
    private void dispatchText(TaskStep step) {
        if (step.isAutoFocusBeforeInput()) {
            // 先点击目标位置获得焦点
            Path clickPath = new Path();
            clickPath.moveTo(step.getX(), step.getY());
            GestureDescription.StrokeDescription clickStroke =
                    new GestureDescription.StrokeDescription(clickPath, 0, 80);
            GestureDescription clickGesture = new GestureDescription.Builder().addStroke(clickStroke).build();

            boolean accepted = dispatchGesture(clickGesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    handler.postDelayed(() -> doDispatchText(step), 300);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    doDispatchText(step);
                }
            }, null);

            if (!accepted) {
                doDispatchText(step);
            }
        } else {
            doDispatchText(step);
        }
    }

    private void doDispatchText(TaskStep step) {
        String text = step.getTextContent();
        if (text == null || text.isEmpty()) {
            finishStep(step);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo focus = null;
        if (root != null) {
            try {
                focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            } catch (Exception ignored) {
            }
        }

        if (focus == null || !focus.isEditable()) {
            failTask("执行失败：未找到可编辑且已聚焦的输入框");
            return;
        }

        int charInterval = Math.max(0, step.getCharIntervalMs());
        if (charInterval <= 0) {
            if (setNodeText(focus, text)) {
                finishStep(step);
            } else {
                failTask("执行失败：目标输入框拒绝写入文本");
            }
        } else {
            typeTextCharByChar(step, focus, 0, charInterval);
        }
    }

    private boolean setNodeText(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return false;
        }

        try {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        } catch (Exception ignored) {
            return false;
        }
    }

    /// <summary>
    /// 逐字输入：每次写入前缀子串，按字符间隔推进，直到完整文本写入。
    /// </summary>
    private void typeTextCharByChar(TaskStep step, AccessibilityNodeInfo node, int index, int charInterval) {
        if (stopRequested || !running) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止");
            return;
        }

        if (paused) {
            handler.postDelayed(() -> typeTextCharByChar(step, node, index, charInterval), 200);
            return;
        }

        String text = step.getTextContent();
        if (index >= text.length()) {
            finishStep(step);
            return;
        }

        if (!setNodeText(node, text.substring(0, index + 1))) {
            failTask("执行失败：逐字输入过程中目标输入框拒绝写入文本");
            return;
        }
        handler.postDelayed(() -> typeTextCharByChar(step, node, index + 1, charInterval), charInterval);
    }

    /// <summary>
    /// 步骤完成：推进步骤指针进入下一步。步骤间等待由下一步的 beforeDelayMs 控制。
    /// </summary>
    private void finishStep(TaskStep step) {
        if (stopRequested || !running) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止");
            return;
        }

        stepIndex++;
        handler.post(this::runLoop);
    }

    /// <summary>
    /// 记录状态到本地并写入执行日志。
    /// </summary>
    private void logAndStatus(String status, String message) {
        TaskStore.saveLastStatus(this, message);
        ExecutionLogStore.addEntry(this, new ExecutionLogEntry()
                .setTaskName(currentTask == null ? "" : currentTask.getName())
                .setStatus(status)
                .setMessage(message));
        updateExecutionControlOverlay(status, message);
    }

    // ---------- 执行悬浮控制 ----------

    /// <summary>
    /// 创建跨应用可见的执行控制窗，对应电脑端悬浮控制窗。
    /// 使用辅助功能覆盖层，不额外申请悬浮窗权限。
    /// </summary>
    private void showExecutionControlOverlay() {
        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            return;
        }

        removeExecutionControlOverlay();
        executionOverlayCollapsed = false;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.argb(230, 24, 24, 27));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Click Assistant");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        executionCollapseButton = new Button(this);
        executionCollapseButton.setText("收起");
        executionCollapseButton.setOnClickListener(view -> toggleExecutionOverlay());
        header.addView(executionCollapseButton);
        panel.addView(header);

        executionOverlayStatusText = new TextView(this);
        executionOverlayStatusText.setText("准备执行");
        executionOverlayStatusText.setTextColor(Color.WHITE);
        executionOverlayStatusText.setTextSize(13);
        executionOverlayStatusText.setPadding(0, dp(6), 0, dp(6));
        panel.addView(executionOverlayStatusText);

        executionOverlayActions = new LinearLayout(this);
        executionOverlayActions.setOrientation(LinearLayout.HORIZONTAL);

        Button appButton = new Button(this);
        appButton.setText("应用");
        appButton.setOnClickListener(view -> openMainActivity());

        executionPauseButton = new Button(this);
        executionPauseButton.setText("暂停");
        executionPauseButton.setOnClickListener(view -> {
            if (paused) {
                resume();
            } else {
                pause();
            }
        });

        Button stopButton = new Button(this);
        stopButton.setText("停止");
        stopButton.setOnClickListener(view -> stop(
                ExecutionState.STOPPED.getDisplayName(),
                "已通过悬浮控制窗停止"));

        executionOverlayActions.addView(appButton, executionActionParams());
        executionOverlayActions.addView(executionPauseButton, executionActionParams());
        executionOverlayActions.addView(stopButton, executionActionParams());
        panel.addView(executionOverlayActions);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12);
        params.y = dp(72);

        try {
            executionControlOverlay = panel;
            windowManager.addView(panel, params);
        } catch (RuntimeException ignored) {
            executionControlOverlay = null;
            executionOverlayStatusText = null;
            executionOverlayActions = null;
            executionPauseButton = null;
            executionCollapseButton = null;
        }
    }

    private void toggleExecutionOverlay() {
        executionOverlayCollapsed = !executionOverlayCollapsed;
        int visibility = executionOverlayCollapsed ? View.GONE : View.VISIBLE;
        if (executionOverlayStatusText != null) {
            executionOverlayStatusText.setVisibility(visibility);
        }
        if (executionOverlayActions != null) {
            executionOverlayActions.setVisibility(visibility);
        }
        if (executionCollapseButton != null) {
            executionCollapseButton.setText(executionOverlayCollapsed ? "展开" : "收起");
        }
    }

    private void updateExecutionControlOverlay(String status, String message) {
        if (executionOverlayStatusText != null) {
            executionOverlayStatusText.setText(status + "\n" + message);
        }
        if (executionPauseButton != null) {
            executionPauseButton.setText(paused ? "继续" : "暂停");
        }
    }

    private void removeExecutionControlOverlay() {
        if (executionControlOverlay == null) {
            return;
        }

        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager != null) {
            try {
                windowManager.removeView(executionControlOverlay);
            } catch (IllegalArgumentException ignored) {
                // 覆盖层可能已被系统回收，只需清空引用。
            }
        }

        executionControlOverlay = null;
        executionOverlayStatusText = null;
        executionOverlayActions = null;
        executionPauseButton = null;
        executionCollapseButton = null;
        executionOverlayCollapsed = false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams executionActionParams() {
        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1);
    }

    // ---------- 坐标拾取 ----------

    private void prepareCoordinatePick() {
        JSONObject target = TaskStore.loadPickTarget(this);
        if (target == null) {
            TaskStore.saveLastStatus(this, "取点失败：缺少取点目标");
            return;
        }

        long requestedAt = target.optLong("requestedAt", 0);
        long requestAgeMs = System.currentTimeMillis() - requestedAt;
        if (requestedAt <= 0 || requestAgeMs < 0 || requestAgeMs > 120000) {
            TaskStore.clearPickTarget(this);
            TaskStore.saveLastStatus(this, "取点请求已过期，请重新选择位置");
            return;
        }

        if (running || paused || currentTask != null) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止当前任务并进入坐标拾取");
        } else {
            stopRequested = true;
            handler.removeCallbacksAndMessages(null);
            removeCoordinatePickerOverlay();
            removeExecutionControlOverlay();
        }

        try {
            pickTaskId = target.getString("taskId");
            pickStepId = target.getString("stepId");
            pickMode = TaskActionType.fromName(target.optString("mode", "TAP"));
            pickPhase = 1;
            int delayMs = Math.max(0, target.optInt("delayMs", 5000));
            int seconds = Math.max(1, delayMs / 1000);
            TaskStore.saveLastStatus(this,
                    String.format(Locale.ROOT, "%d 秒后显示取点层，请切到目标界面", seconds));
            handler.postDelayed(this::showCoordinatePickerOverlay, delayMs);
        } catch (JSONException e) {
            TaskStore.saveLastStatus(this, "取点失败：取点目标格式错误");
        }
    }

    private void showCoordinatePickerOverlay() {
        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            TaskStore.saveLastStatus(this, "取点失败：无法创建屏幕取点层");
            return;
        }

        removeCoordinatePickerOverlay();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(48, 0, 0, 0));
        overlay.setOnTouchListener((view, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                updatePickerCursor(Math.round(event.getRawX()), Math.round(event.getRawY()));
            }

            return true;
        });

        TaskStep targetStep = loadPickStep();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (targetStep == null) {
            pickerX = screenWidth / 2;
            pickerY = screenHeight / 2;
        } else if (pickMode == TaskActionType.SWIPE && pickPhase == 2) {
            pickerX = targetStep.getEndX();
            pickerY = targetStep.getEndY();
        } else {
            pickerX = targetStep.getX();
            pickerY = targetStep.getY();
        }

        if (pickerX < 0 || pickerX > screenWidth) {
            pickerX = screenWidth / 2;
        }
        if (pickerY < 0 || pickerY > screenHeight) {
            pickerY = screenHeight / 2;
        }

        int cursorSize = dp(72);
        pickerCursorView = new PickerCursorView(this);
        // 显示目标步骤的序号
        if (targetStep != null) {
            pickerCursorView.setStepNumber(String.valueOf(targetStep.getOrder() + 1));
        }
        FrameLayout.LayoutParams cursorParams = new FrameLayout.LayoutParams(cursorSize, cursorSize);
        overlay.addView(pickerCursorView, cursorParams);

        pickerGuideText = new TextView(this);
        pickerGuideText.setText(pickGuideTextWithPosition());
        pickerGuideText.setTextColor(Color.WHITE);
        pickerGuideText.setTextSize(16);
        pickerGuideText.setGravity(Gravity.CENTER);
        pickerGuideText.setPadding(24, 24, 24, 24);
        pickerGuideText.setBackgroundColor(Color.argb(220, 0, 0, 0));
        overlay.addView(pickerGuideText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), dp(8), dp(12), dp(16));
        actions.setBackgroundColor(Color.argb(220, 0, 0, 0));

        Button cancelButton = new Button(this);
        cancelButton.setText("取消取点");
        cancelButton.setOnClickListener(view -> cancelPick());
        pickerConfirmButton = new Button(this);
        pickerConfirmButton.setText(pickConfirmText());
        pickerConfirmButton.setOnClickListener(view -> onPickerTouch(pickerX, pickerY));

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1);
        actions.addView(cancelButton, actionParams);
        actions.addView(pickerConfirmButton, new LinearLayout.LayoutParams(actionParams));

        FrameLayout.LayoutParams actionBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        overlay.addView(actions, actionBarParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            coordinatePickerOverlay = overlay;
            windowManager.addView(overlay, params);
        } catch (RuntimeException ex) {
            coordinatePickerOverlay = null;
            pickerGuideText = null;
            pickerCursorView = null;
            pickerConfirmButton = null;
            TaskStore.saveLastStatus(this,
                    "取点失败：无法显示取点层（" + ex.getClass().getSimpleName() + "）");
            openMainActivity();
            return;
        }
        overlay.post(() -> updatePickerCursor(pickerX, pickerY));
        TaskStore.saveLastStatus(this, "取点中：拖动光标后确认位置");
    }

    private String pickGuideText() {
        if (pickMode == TaskActionType.SWIPE) {
            return pickPhase == 1
                    ? "拖动蓝色光标到滑动起点，然后确认"
                    : "拖动蓝色光标到滑动终点，然后确认";
        }

        return "拖动蓝色光标到需要点击的位置，然后确认";
    }

    private String pickGuideTextWithPosition() {
        return String.format(Locale.ROOT, "%s\n当前坐标：%d, %d", pickGuideText(), pickerX, pickerY);
    }

    private String pickConfirmText() {
        if (pickMode == TaskActionType.SWIPE) {
            return pickPhase == 1 ? "确认起点" : "确认终点";
        }
        return "确认位置";
    }

    private void updatePickerCursor(int x, int y) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        pickerX = Math.max(0, Math.min(screenWidth, x));
        pickerY = Math.max(0, Math.min(screenHeight, y));

        if (pickerCursorView != null) {
            float half = pickerCursorView.getWidth() > 0
                    ? pickerCursorView.getWidth() / 2f
                    : dp(36);
            pickerCursorView.setX(pickerX - half);
            pickerCursorView.setY(pickerY - half);
        }
        if (pickerGuideText != null) {
            pickerGuideText.setText(pickGuideTextWithPosition());
        }
    }

    private TaskStep loadPickStep() {
        ClickTask task = TaskStore.getTask(this, pickTaskId);
        if (task == null) {
            return null;
        }
        for (TaskStep step : task.getSteps()) {
            if (step.getId().equals(pickStepId)) {
                return step;
            }
        }
        return null;
    }

    private void onPickerTouch(int x, int y) {
        ClickTask task = TaskStore.getTask(this, pickTaskId);
        if (task == null) {
            TaskStore.saveLastStatus(this, "取点失败：任务不存在");
            cancelPick();
            return;
        }

        TaskStep step = null;
        for (TaskStep candidate : task.getSteps()) {
            if (candidate.getId().equals(pickStepId)) {
                step = candidate;
                break;
            }
        }

        if (step == null) {
            TaskStore.saveLastStatus(this, "取点失败：步骤不存在");
            cancelPick();
            return;
        }

        if (pickMode == TaskActionType.SWIPE && pickPhase == 1) {
            step.setX(x);
            step.setY(y);
            pickPhase = 2;
            if (pickerGuideText != null) {
                pickerGuideText.setText(pickGuideTextWithPosition());
            }
            if (pickerConfirmButton != null) {
                pickerConfirmButton.setText(pickConfirmText());
            }

            pickerX = step.getEndX();
            pickerY = step.getEndY();
            updatePickerCursor(pickerX, pickerY);

            TaskStore.upsertTask(this, task);
            TaskStore.saveLastStatus(this,
                    String.format(Locale.ROOT, "已选起点：%d,%d，请点终点", x, y));
            return;
        }

        if (pickMode == TaskActionType.SWIPE) {
            step.setEndX(x);
            step.setEndY(y);
        } else {
            step.setX(x);
            step.setY(y);
        }

        TaskStore.upsertTask(this, task);
        TaskStore.saveLastStatus(this, String.format(Locale.ROOT, "已保存坐标：%d,%d", x, y));
        TaskStore.clearPickTarget(this);
        removeCoordinatePickerOverlay();
        openMainActivity();
    }

    private void cancelPick() {
        removeCoordinatePickerOverlay();
        TaskStore.clearPickTarget(this);
        TaskStore.saveLastStatus(this, "已取消坐标拾取");
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
                // 视图可能已被系统移除，清空引用即可。
            }
        }

        coordinatePickerOverlay = null;
        pickerGuideText = null;
        pickerCursorView = null;
        pickerConfirmButton = null;
    }

    /// <summary>
    /// 可视化取点光标，圆圈中央显示步骤序号，便于直接识别当前编辑的步骤。
    /// </summary>
    private final class PickerCursorView extends View {
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String stepNumber = "";

        PickerCursorView(android.content.Context context) {
            super(context);
            circlePaint.setColor(Color.rgb(54, 184, 255));
            circlePaint.setStrokeWidth(5f);
            circlePaint.setStyle(Paint.Style.STROKE);
            textPaint.setColor(Color.rgb(54, 184, 255));
            textPaint.setTextSize(dp(22));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            setClickable(false);
        }

        void setStepNumber(String number) {
            stepNumber = number;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.28f;
            canvas.drawCircle(centerX, centerY, radius, circlePaint);
            canvas.drawLine(centerX, 0, centerX, getHeight(), circlePaint);
            canvas.drawLine(0, centerY, getWidth(), centerY, circlePaint);

            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX, centerY, 7f, circlePaint);
            circlePaint.setStyle(Paint.Style.STROKE);

            if (!stepNumber.isEmpty()) {
                float textOffset = (textPaint.descent() + textPaint.ascent()) / 2f;
                canvas.drawText(stepNumber, centerX, centerY - textOffset + dp(10), textPaint);
            }
        }
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
