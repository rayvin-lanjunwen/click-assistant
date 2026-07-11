package com.clickassistant.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
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
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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

    // 常驻悬浮按钮
    private FloatingTriggerButton floatingTriggerButton;

    // 标记覆盖层管理器（持久化标记圆圈 + 执行动效）
    private MarkerOverlayManager markerOverlayManager;

    // 取点覆盖层（手指跟踪 + 确认气泡）
    private View coordinatePickerOverlay;
    private PickerCursorView pickerCursorView;
    private int pickerX;
    private int pickerY;

    // 当前取点目标
    private String pickTaskId;
    private String pickStepId;
    private TaskActionType pickMode = TaskActionType.TAP;
    private int pickPhase = 1;

    // 执行控制窗
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

    /// <summary>
    /// 清除所有标记（供 MainActivity 保存任务后调用）。
    /// </summary>
    public static void clearMarkers() {
        ClickAssistantAccessibilityService service = activeService;
        if (service != null && service.markerOverlayManager != null) {
            service.markerOverlayManager.clearAll();
            service.markerOverlayManager.hide();
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;

        // 创建标记覆盖层
        markerOverlayManager = new MarkerOverlayManager(this);
        markerOverlayManager.setOnMarkerActionListener(new MarkerOverlayManager.OnMarkerActionListener() {
            @Override
            public void onMove(MarkerOverlayManager.MarkerEntry entry) {
                // 暂不实现拖动，后续迭代
                showToast("移动功能开发中");
            }

            @Override
            public void onEdit(MarkerOverlayManager.MarkerEntry entry) {
                openMainActivity();
                showToast("请回到 App 编辑步骤数据");
            }

            @Override
            public void onDelete(MarkerOverlayManager.MarkerEntry entry) {
                markerOverlayManager.removeMarker(entry.step.getId());
                // 同步删除步骤坐标（设为零）
                entry.step.setX(0);
                entry.step.setY(0);
                ClickTask task = TaskStore.getTask(ClickAssistantAccessibilityService.this,
                        TaskStore.loadActiveTaskId(ClickAssistantAccessibilityService.this));
                if (task != null) {
                    TaskStore.upsertTask(ClickAssistantAccessibilityService.this, task);
                }
                showToast("已删除标记");
            }
        });

        // 创建悬浮触发按钮
        floatingTriggerButton = new FloatingTriggerButton(this);
        floatingTriggerButton.setOnTriggerListener(new FloatingTriggerButton.OnTriggerListener() {
            @Override
            public void onStartPick() {
                enterPickMode();
            }

            @Override
            public void onFinishPick() {
                exitPickMode();
            }
        });
        floatingTriggerButton.show();

        if (TaskStore.loadPickTarget(this) != null) {
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
        if (floatingTriggerButton != null) {
            floatingTriggerButton.hide();
        }
        if (markerOverlayManager != null) {
            markerOverlayManager.clearAll();
            markerOverlayManager.hide();
        }
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

        // 显示所有步骤标记
        if (markerOverlayManager != null) {
            markerOverlayManager.clearAll();
            for (TaskStep step : executionTask.getSteps()) {
                if (step.isEnabled()) {
                    markerOverlayManager.addMarker(step);
                }
            }
            markerOverlayManager.show();
        }

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
        // 执行完毕后保留标记，不调用 clearAll()
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

        // 当前步骤标记脉冲动效
        if (markerOverlayManager != null) {
            markerOverlayManager.pulseMarker(step.getId());
        }

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
    /// 按压时长使用 pressDurationMs 控制，为 0 时使用默认 80ms。
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

        long pressDuration = step.getPressDurationMs() > 0
                ? step.getPressDurationMs()
                : 80;

        Path path = new Path();
        path.moveTo(step.getX(), step.getY());
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, pressDuration);
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

    /// <summary>
    /// 准备取点（兼容旧接口）：从 MainActivity 接收到取点目标后，
    /// 直接进入取点模式（不再有倒计时，用户通过悬浮按钮触发）。
    /// </summary>
    private void prepareCoordinatePick() {
        JSONObject target = TaskStore.loadPickTarget(this);
        if (target == null) {
            TaskStore.saveLastStatus(this, "取点失败：缺少取点目标");
            return;
        }

        try {
            pickTaskId = target.getString("taskId");
            pickStepId = target.getString("stepId");
            pickMode = TaskActionType.fromName(target.optString("mode", "TAP"));
            pickPhase = 1;
            TaskStore.clearPickTarget(this);
            // 自动进入取点模式
            enterPickMode();
        } catch (JSONException e) {
            TaskStore.saveLastStatus(this, "取点失败：取点目标格式错误");
        }
    }

    /// <summary>
    /// 进入取点模式：显示透明取点覆盖层 + 手指跟随光标 + 确认气泡。
    /// 读取 TaskStore 中的 activeTaskId/activeStepId 作为取点目标。
    /// </summary>
    private void enterPickMode() {
        // 检查是否有活动任务和步骤
        String activeTaskId = TaskStore.loadActiveTaskId(this);
        String activeStepId = TaskStore.loadActiveStepId(this);
        if (activeTaskId == null || activeStepId == null) {
            showToast("请先在 App 中打开任务编辑页");
            return;
        }

        ClickTask task = TaskStore.getTask(this, activeTaskId);
        if (task == null) {
            showToast("取点失败：任务不存在");
            return;
        }

        TaskStep targetStep = null;
        for (TaskStep s : task.getSteps()) {
            if (s.getId().equals(activeStepId)) {
                targetStep = s;
                break;
            }
        }
        if (targetStep == null) {
            showToast("取点失败：步骤不存在");
            return;
        }

        pickTaskId = activeTaskId;
        pickStepId = activeStepId;
        pickMode = targetStep.getActionType();
        pickPhase = 1;

        if (running || paused || currentTask != null) {
            stop(ExecutionState.STOPPED.getDisplayName(), "已停止当前任务并进入坐标拾取");
        }

        // 显示已存在的标记
        if (markerOverlayManager != null) {
            markerOverlayManager.clearAll();
            for (TaskStep step : task.getSteps()) {
                if (step.isEnabled() && step.getX() > 0 && step.getY() > 0) {
                    markerOverlayManager.addMarker(step);
                }
            }
            markerOverlayManager.show();
        }

        showNewPickerOverlay(targetStep);
        floatingTriggerButton.setPickMode(true);
    }

    /// <summary>
    /// 退出取点模式：移除取点覆盖层，保留标记圆圈和悬浮按钮。
    /// </summary>
    private void exitPickMode() {
        removeCoordinatePickerOverlay();
        floatingTriggerButton.setPickMode(false);
        TaskStore.saveLastStatus(this, "取点完成，标记已保留。回 App 保存任务后清除。");
    }

    /// <summary>
    /// 新版取点覆盖层：透明背景 + 手指跟随 + 松手弹出确认气泡。
    /// </summary>
    private void showNewPickerOverlay(TaskStep targetStep) {
        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            TaskStore.saveLastStatus(this, "取点失败：无法创建屏幕取点层");
            return;
        }

        removeCoordinatePickerOverlay();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(24, 0, 0, 0));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        pickerX = targetStep.getX() > 0 ? targetStep.getX() : screenWidth / 2;
        pickerY = targetStep.getY() > 0 ? targetStep.getY() : screenHeight / 2;

        int cursorSize = dp(72);
        pickerCursorView = new PickerCursorView(this);
        pickerCursorView.setStepNumber(String.valueOf(targetStep.getOrder() + 1));
        pickerCursorView.setActionType(targetStep.getActionType());
        FrameLayout.LayoutParams cursorParams = new FrameLayout.LayoutParams(cursorSize, cursorSize);
        overlay.addView(pickerCursorView, cursorParams);

        // 手指跟踪
        overlay.setOnTouchListener((view, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                updatePickerCursor(Math.round(event.getRawX()), Math.round(event.getRawY()));
            }
            if (action == MotionEvent.ACTION_UP) {
                // 松手时在光标旁边显示确认气泡
                updatePickerCursor(Math.round(event.getRawX()), Math.round(event.getRawY()));
                showConfirmBubble(overlay, pickerX, pickerY);
            }
            return true;
        });

        // 右上角提示文字
        TextView hintText = new TextView(this);
        hintText.setText("滑动手指定位，松手后点击 \u2713 确认");
        hintText.setTextColor(Color.WHITE);
        hintText.setTextSize(14);
        hintText.setPadding(dp(16), dp(8), dp(16), dp(8));
        hintText.setBackgroundColor(Color.argb(200, 0, 0, 0));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        hintParams.topMargin = dp(40);
        overlay.addView(hintText, hintParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            coordinatePickerOverlay = overlay;
            windowManager.addView(overlay, params);
        } catch (RuntimeException ex) {
            coordinatePickerOverlay = null;
            pickerCursorView = null;
            TaskStore.saveLastStatus(this, "取点失败：无法显示取点层");
            return;
        }
        overlay.post(() -> updatePickerCursor(pickerX, pickerY));
    }

    /// <summary>
    /// 在光标旁边显示确认气泡（PopupWindow）。
    /// </summary>
    private void showConfirmBubble(FrameLayout parent, int x, int y) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setBackgroundColor(Color.parseColor("#2563EB"));
        bubble.setPadding(dp(4), dp(2), dp(4), dp(2));
        bubble.setGravity(Gravity.CENTER);

        TextView checkText = new TextView(this);
        checkText.setText("\u2713");
        checkText.setTextColor(Color.WHITE);
        checkText.setTextSize(20);
        checkText.setPadding(dp(8), dp(4), dp(8), dp(4));
        bubble.addView(checkText);

        PopupWindow popup = new PopupWindow(bubble,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(null);

        bubble.setOnClickListener(v -> {
            popup.dismiss();
            onPickConfirm(pickerX, pickerY);
        });

        popup.showAtLocation(parent, Gravity.TOP | Gravity.START,
                x + dp(40), y - dp(14));
    }

    private void onPickConfirm(int x, int y) {
        ClickTask task = TaskStore.getTask(this, pickTaskId);
        if (task == null) {
            TaskStore.saveLastStatus(this, "取点失败：任务不存在");
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
            return;
        }

        // 支持滑动取点的两阶段确认
        if (pickMode == TaskActionType.SWIPE && pickPhase == 1) {
            step.setX(x);
            step.setY(y);
            pickPhase = 2;
            pickerX = step.getEndX();
            pickerY = step.getEndY();
            updatePickerCursor(pickerX, pickerY);
            if (pickerCursorView != null) {
                pickerCursorView.setStepNumber((step.getOrder() + 1) + "→");
            }
            TaskStore.upsertTask(this, task);
            TaskStore.saveLastStatus(this,
                    String.format(Locale.ROOT, "已选起点：%d,%d，请滑动到终点确认", x, y));
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

        // 将标记添加到覆盖层
        if (markerOverlayManager != null) {
            markerOverlayManager.addMarker(step);
        }

        showToast("坐标已保存：" + x + "," + y);
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
    }

    /// <summary>
    /// 移除取点覆盖层，保留标记和悬浮按钮。
    /// </summary>
    private void removeCoordinatePickerOverlay() {
        if (coordinatePickerOverlay == null) {
            return;
        }

        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager != null) {
            try {
                windowManager.removeView(coordinatePickerOverlay);
            } catch (IllegalArgumentException ignored) {
            }
        }

        coordinatePickerOverlay = null;
        pickerCursorView = null;
    }

    private void showToast(String message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
