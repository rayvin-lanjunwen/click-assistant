package com.clickassistant.mobile;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// <summary>
/// 标记覆盖层管理器，管理独立的 TYPE_ACCESSIBILITY_OVERLAY Window。
/// 支持 addMarker/removeMarker/clearAll/show/hide，
/// 长按弹出操作菜单（移动/调整/删除），pulseMarker 执行动效。
/// </summary>
public final class MarkerOverlayManager {
    private final Context context;
    private final WindowManager windowManager;
    private FrameLayout overlayRoot;
    private boolean visible = false;
    private boolean addedToWindow = false;

    /// <summary>
    /// 标记条目：持有 TaskStep、View、当前坐标等信息。
    /// </summary>
    public static final class MarkerEntry {
        public final TaskStep step;
        public final View markerView;
        public final TextView numberText;
        public final TextView typeIconText;
        public int screenX;
        public int screenY;
        public int endScreenX;
        public int endScreenY;

        MarkerEntry(TaskStep step, View markerView, TextView numberText, TextView typeIconText,
                    int screenX, int screenY, int endScreenX, int endScreenY) {
            this.step = step;
            this.markerView = markerView;
            this.numberText = numberText;
            this.typeIconText = typeIconText;
            this.screenX = screenX;
            this.screenY = screenY;
            this.endScreenX = endScreenX;
            this.endScreenY = endScreenY;
        }
    }

    private final Map<String, MarkerEntry> markers = new LinkedHashMap<>();

    /// <summary>
    /// 长按菜单回调：move, edit, delete。
    /// </summary>
    public interface OnMarkerActionListener {
        void onMove(MarkerEntry entry);
        void onEdit(MarkerEntry entry);
        void onDelete(MarkerEntry entry);
    }

    private OnMarkerActionListener actionListener;

    public MarkerOverlayManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setOnMarkerActionListener(OnMarkerActionListener listener) {
        this.actionListener = listener;
    }

    /// <summary>
    /// 添加一个步骤标记到覆盖层。若已存在同 ID 标记则先移除旧标记。
    /// </summary>
    public void addMarker(TaskStep step) {
        removeMarker(step.getId());

        int color = step.getActionType().getColor();
        int cx = step.getX();
        int cy = step.getY();

        FrameLayout wrapper = new FrameLayout(context);
        int markerSize = dp(40);

        // 标记圆圈 View
        View circleView = new View(context) {
            private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                circlePaint.setColor(color);
                circlePaint.setStyle(Paint.Style.STROKE);
                circlePaint.setStrokeWidth(dp(3));
            }
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = Math.min(getWidth(), getHeight()) / 2f - dp(2);
                canvas.drawCircle(cx, cy, r, circlePaint);
            }
        };
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(markerSize, markerSize);
        wrapper.addView(circleView, circleParams);

        // 序号文字
        TextView numberText = new TextView(context);
        numberText.setText(String.valueOf(step.getOrder() + 1));
        numberText.setTextColor(color);
        numberText.setTextSize(16);
        numberText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(markerSize, markerSize);
        wrapper.addView(numberText, numberParams);

        // 类型图标小标签
        TextView typeIcon = new TextView(context);
        typeIcon.setText(getTypeIcon(step.getActionType()));
        typeIcon.setTextColor(Color.WHITE);
        typeIcon.setTextSize(9);
        typeIcon.setGravity(Gravity.CENTER);
        typeIcon.setBackgroundColor(color);
        int iconSize = dp(14);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.leftMargin = dp(26);
        wrapper.addView(typeIcon, iconParams);

        // 长按事件
        wrapper.setOnLongClickListener(v -> {
            if (actionListener != null) {
                MarkerEntry entry = markers.get(step.getId());
                if (entry != null) {
                    showPopupMenu(v, entry);
                }
            }
            return true;
        });

        MarkerEntry entry = new MarkerEntry(
                step, wrapper, numberText, typeIcon, cx, cy,
                step.getEndX(), step.getEndY());

        markers.put(step.getId(), entry);

        if (overlayRoot != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(markerSize, markerSize);
            params.leftMargin = cx - markerSize / 2;
            params.topMargin = cy - markerSize / 2;
            overlayRoot.addView(wrapper, params);
        }

        ensureOverlay();
    }

    /// <summary>
    /// 按步骤 ID 移除标记。
    /// </summary>
    public void removeMarker(String stepId) {
        MarkerEntry entry = markers.remove(stepId);
        if (entry != null && overlayRoot != null) {
            overlayRoot.removeView(entry.markerView);
        }
    }

    /// <summary>
    /// 按 TaskStep 移除标记。
    /// </summary>
    public void removeMarker(TaskStep step) {
        removeMarker(step.getId());
    }

    /// <summary>
    /// 清除所有标记。
    /// </summary>
    public void clearAll() {
        if (overlayRoot != null) {
            overlayRoot.removeAllViews();
        }
        markers.clear();
    }

    /// <summary>
    /// 显示覆盖层。
    /// </summary>
    public void show() {
        visible = true;
        ensureOverlay();
    }

    /// <summary>
    /// 隐藏覆盖层（不移除标记数据）。
    /// </summary>
    public void hide() {
        visible = false;
        removeOverlayFromWindow();
    }

    /// <summary>
    /// 获取所有当前标记列表。
    /// </summary>
    public List<MarkerEntry> getMarkers() {
        return new ArrayList<>(markers.values());
    }

    /// <summary>
    /// 按步骤 ID 获取标记条目。
    /// </summary>
    public MarkerEntry getMarker(String stepId) {
        return markers.get(stepId);
    }

    /// <summary>
    /// 更新标记位置（拖动后）。
    /// </summary>
    public void updateMarkerPosition(String stepId, int newX, int newY) {
        MarkerEntry entry = markers.get(stepId);
        if (entry == null || overlayRoot == null) return;
        entry.screenX = newX;
        entry.screenY = newY;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) entry.markerView.getLayoutParams();
        params.leftMargin = newX - dp(20);
        params.topMargin = newY - dp(20);
        entry.markerView.setLayoutParams(params);
    }

    /// <summary>
    /// 对指定步骤标记执行脉冲动效。
    /// </summary>
    public void pulseMarker(String stepId) {
        MarkerEntry entry = markers.get(stepId);
        if (entry == null) return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(entry.markerView, "scaleX", 1f, 1.4f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(entry.markerView, "scaleY", 1f, 1.4f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(entry.markerView, "alpha", 1f, 0.5f, 1f);

        scaleX.setDuration(400);
        scaleY.setDuration(400);
        alpha.setDuration(400);

        scaleX.start();
        scaleY.start();
        alpha.start();
    }

    /// <summary>
    /// 检查是否有标记。
    /// </summary>
    public boolean isEmpty() {
        return markers.isEmpty();
    }

    // ---------- 内部实现 ----------

    private void ensureOverlay() {
        if (!visible) return;
        if (addedToWindow) return;

        overlayRoot = new FrameLayout(context);
        overlayRoot.setClickable(false);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(overlayRoot, params);
            addedToWindow = true;

            // 将内存中的标记重新添加到新覆盖层
            for (MarkerEntry entry : markers.values()) {
                int size = dp(40);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                lp.leftMargin = entry.screenX - size / 2;
                lp.topMargin = entry.screenY - size / 2;
                overlayRoot.addView(entry.markerView, lp);
            }
        } catch (RuntimeException ex) {
            overlayRoot = null;
        }
    }

    private void removeOverlayFromWindow() {
        if (!addedToWindow || overlayRoot == null) return;
        try {
            windowManager.removeView(overlayRoot);
            addedToWindow = false;
            overlayRoot.removeAllViews();
            overlayRoot = null;
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void showPopupMenu(View anchor, MarkerEntry entry) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenu().add("移动");
        popup.getMenu().add("调整数据");
        popup.getMenu().add("删除");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "移动":
                    if (actionListener != null) actionListener.onMove(entry);
                    return true;
                case "调整数据":
                    if (actionListener != null) actionListener.onEdit(entry);
                    return true;
                case "删除":
                    if (actionListener != null) actionListener.onDelete(entry);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private static String getTypeIcon(TaskActionType type) {
        switch (type) {
            case TAP: return "\u26AC";       // ⚬
            case SWIPE: return "\u2192";    // →
            case TEXT_INPUT: return "\u2328"; // ⌨
            default: return "\u26AC";
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
