package com.clickassistant.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/// <summary>
/// 左侧垂直悬浮抽屉按钮。
/// 收起状态仅显示一个小箭头，展开后显示操作面板。
/// 通过回调接口通知 ClickAssistantAccessibilityService。
/// </summary>
public final class FloatingTriggerButton {

    /// <summary>
    /// 操作回调接口。
    /// </summary>
    public interface OnTriggerListener {
        /// 点击 ▶开始。
        void onStartPick();
        /// 点击完成（取点用）。
        void onFinishPick();
        /// 点击 📍添加点。
        void onAddPoint();
        /// 点击 🗑删除点。
        void onDeletePoint();
        /// 点击 📋查看。
        void onViewLog();
    }

    private final Context context;
    private final WindowManager windowManager;
    private OnTriggerListener listener;

    private LinearLayout drawerRoot;
    private LinearLayout expandedPanel;
    private TextView collapseArrow;
    private boolean expanded = false;
    private boolean addedToWindow = false;
    private boolean pickMode = false;

    // 颜色定义
    private static final int ACCENT = Color.parseColor("#3978F6");
    private static final int TEXT_PRIMARY = Color.parseColor("#17233A");
    private static final int TEXT_SECONDARY = Color.parseColor("#526176");

    public FloatingTriggerButton(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setOnTriggerListener(OnTriggerListener listener) {
        this.listener = listener;
    }

    public void show() {
        if (addedToWindow) return;
        createDrawerView();
        addToWindow();
    }

    public void hide() {
        if (!addedToWindow) return;
        try {
            windowManager.removeView(drawerRoot);
            addedToWindow = false;
            drawerRoot = null;
            expandedPanel = null;
            collapseArrow = null;
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void setPickMode(boolean pickMode) {
        this.pickMode = pickMode;
    }

    public boolean isPickMode() {
        return pickMode;
    }

    public boolean isShowing() {
        return addedToWindow;
    }

    // ---------- 内部实现 ----------

    private void createDrawerView() {
        drawerRoot = new LinearLayout(context);
        drawerRoot.setOrientation(LinearLayout.HORIZONTAL);
        drawerRoot.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#F2FFFFFF"), Color.parseColor("#D9EEF4F7")});
        bg.setCornerRadii(new float[]{0, dp(12), dp(12), 0, 0, 0, 0, 0});
        bg.setStroke(dp(1), Color.parseColor("#B8FFFFFF"));
        drawerRoot.setBackground(bg);
        drawerRoot.setElevation(dp(6));

        // 展开面板（初始隐藏）
        expandedPanel = new LinearLayout(context);
        expandedPanel.setOrientation(LinearLayout.VERTICAL);
        expandedPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        expandedPanel.setVisibility(View.GONE);
        expandedPanel.setMinimumWidth(dp(140));

        // ▶开始 / 完成
        addPanelButton(expandedPanel, "▶ 开始", ACCENT, true, () -> {
            if (listener != null) {
                if (pickMode) {
                    listener.onFinishPick();
                } else {
                    listener.onStartPick();
                }
            }
        });

        // 📍添加点
        addPanelButton(expandedPanel, "📍 添加点", Color.parseColor("#16A064"), false, () -> {
            if (listener != null) listener.onAddPoint();
        });

        // 🗑删除点
        addPanelButton(expandedPanel, "🗑 删除点", Color.parseColor("#EF4444"), false, () -> {
            if (listener != null) listener.onDeletePoint();
        });

        // 📋查看
        addPanelButton(expandedPanel, "📋 查看", TEXT_SECONDARY, false, () -> {
            if (listener != null) listener.onViewLog();
        });

        // «收起 分隔线
        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#80FFFFFF"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        expandedPanel.addView(divider, dividerParams);

        // «收起箭头
        TextView collapseLabel = new TextView(context);
        collapseLabel.setText("« 收起");
        collapseLabel.setTextSize(12);
        collapseLabel.setTextColor(TEXT_SECONDARY);
        collapseLabel.setPadding(dp(12), dp(6), dp(8), dp(4));
        collapseLabel.setGravity(Gravity.CENTER_VERTICAL);
        collapseLabel.setOnClickListener(v -> collapse());
        expandedPanel.addView(collapseLabel);

        drawerRoot.addView(expandedPanel);

        // 收起箭头
        collapseArrow = new TextView(context);
        collapseArrow.setText("▶");
        collapseArrow.setTextSize(16);
        collapseArrow.setTextColor(Color.WHITE);
        collapseArrow.setGravity(Gravity.CENTER);
        GradientDrawable arrowBackground = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#4C8DFF"), Color.parseColor("#5668D8")});
        arrowBackground.setCornerRadii(new float[]{0, dp(12), dp(12), 0, 0, 0, 0, 0});
        collapseArrow.setBackground(arrowBackground);
        collapseArrow.setPadding(dp(6), dp(18), dp(6), dp(18));
        collapseArrow.setOnClickListener(v -> {
            if (expanded) {
                collapse();
            } else {
                expand();
            }
        });
        drawerRoot.addView(collapseArrow);
    }

    private void addPanelButton(LinearLayout panel, String label, int color, boolean primary, Runnable action) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextSize(13);
        btn.setTextColor(color);
        btn.setPadding(dp(12), dp(8), dp(8), dp(8));
        btn.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(8));
        btnBg.setColor(primary ? Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)) : Color.TRANSPARENT);
        btn.setBackground(btnBg);

        btn.setOnClickListener(v -> {
            if (action != null) action.run();
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, dp(2), 0, dp(2));
        panel.addView(btn, btnParams);
    }

    private void expand() {
        if (expandedPanel == null || collapseArrow == null) return;
        expandedPanel.setVisibility(View.VISIBLE);
        expandedPanel.setAlpha(0f);
        expandedPanel.animate().alpha(1f).setDuration(200).start();
        collapseArrow.setText("◀");
        expanded = true;
    }

    private void collapse() {
        if (expandedPanel == null || collapseArrow == null) return;
        expandedPanel.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            expandedPanel.setVisibility(View.GONE);
        }).start();
        collapseArrow.setText("▶");
        expanded = false;
    }

    private void addToWindow() {
        if (drawerRoot == null) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(drawerRoot, params);
            addedToWindow = true;
        } catch (RuntimeException ignored) {
            drawerRoot = null;
            expandedPanel = null;
            collapseArrow = null;
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
