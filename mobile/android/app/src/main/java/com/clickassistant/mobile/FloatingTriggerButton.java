package com.clickassistant.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

/// <summary>
/// 常驻悬浮触发按钮，辅助功能服务启动后显示在右下角。
/// 支持两种状态：IDLE（「取点」白色）→ PICKING（「完成」蓝色）。
/// 通过回调接口通知 ClickAssistantAccessibilityService。
/// </summary>
public final class FloatingTriggerButton {

    /// <summary>
    /// 点击回调接口。
    /// </summary>
    public interface OnTriggerListener {
        /// IDLE 状态被点击 — 进入取点模式。
        void onStartPick();
        /// PICKING 状态被点击 — 完成取点。
        void onFinishPick();
    }

    private final Context context;
    private final WindowManager windowManager;
    private OnTriggerListener listener;

    private LinearLayout buttonView;
    private Button actionButton;
    private boolean pickMode = false;
    private boolean addedToWindow = false;

    public FloatingTriggerButton(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setOnTriggerListener(OnTriggerListener listener) {
        this.listener = listener;
    }

    /// <summary>
    /// 显示悬浮按钮。
    /// </summary>
    public void show() {
        if (addedToWindow) return;
        createButtonView();
        addToWindow();
    }

    /// <summary>
    /// 隐藏悬浮按钮。
    /// </summary>
    public void hide() {
        if (!addedToWindow) return;
        try {
            windowManager.removeView(buttonView);
            addedToWindow = false;
            buttonView = null;
            actionButton = null;
        } catch (IllegalArgumentException ignored) {
        }
    }

    /// <summary>
    /// 切换到取点模式（按钮文字=完成，蓝色背景）。
    /// </summary>
    public void setPickMode(boolean pickMode) {
        this.pickMode = pickMode;
        if (actionButton != null) {
            actionButton.setText(pickMode ? "完成" : "取点");
            actionButton.setBackgroundColor(pickMode
                    ? Color.parseColor("#2563EB")
                    : Color.parseColor("#EEEEEE"));
            actionButton.setTextColor(pickMode ? Color.WHITE : Color.parseColor("#333333"));
        }
    }

    public boolean isPickMode() {
        return pickMode;
    }

    public boolean isShowing() {
        return addedToWindow;
    }

    // ---------- 内部实现 ----------

    private void createButtonView() {
        buttonView = new LinearLayout(context);
        buttonView.setOrientation(LinearLayout.HORIZONTAL);
        buttonView.setGravity(Gravity.CENTER);

        actionButton = new Button(context);
        actionButton.setText("取点");
        actionButton.setTextSize(14);
        actionButton.setPadding(dp(16), dp(10), dp(16), dp(10));
        actionButton.setBackgroundColor(Color.parseColor("#EEEEEE"));
        actionButton.setTextColor(Color.parseColor("#333333"));
        actionButton.setAllCaps(false);

        actionButton.setOnClickListener(v -> {
            if (listener == null) return;
            if (pickMode) {
                listener.onFinishPick();
            } else {
                listener.onStartPick();
            }
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonView.addView(actionButton, btnParams);
    }

    private void addToWindow() {
        if (buttonView == null) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = dp(12);
        params.y = dp(80);

        try {
            windowManager.addView(buttonView, params);
            addedToWindow = true;
        } catch (RuntimeException ignored) {
            buttonView = null;
            actionButton = null;
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
