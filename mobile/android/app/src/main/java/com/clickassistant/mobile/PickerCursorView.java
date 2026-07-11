package com.clickassistant.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/// <summary>
/// 可视化取点光标，绘制带十字线、中心点与步骤序号的彩色圆圈。
/// 按 TaskActionType 自动着色：点击=蓝 / 滑动=绿 / 文本输入=橙。
/// 从 ClickAssistantAccessibilityService 的内部类提升为独立文件。
/// </summary>
public final class PickerCursorView extends View {
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String stepNumber = "";
    private int actionColor = Color.parseColor("#3978F6"); // 默认使用品牌蓝
    private boolean highlighted = false; // 长按拖动定位时的高亮状态

    public PickerCursorView(Context context) {
        super(context);
        initPaints();
    }

    private void initPaints() {
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(5f);

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(2f);

        dotPaint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    /// <summary>
    /// 设置步骤序号，显示在圆圈下方。
    /// </summary>
    public void setStepNumber(String number) {
        stepNumber = number;
        invalidate();
    }

    /// <summary>
    /// 按 TaskActionType 更新颜色，通过枚举自动映射。
    /// </summary>
    public void setActionType(TaskActionType type) {
        actionColor = type.getColor();
        invalidate();
    }

    /// <summary>
    /// 直接设置颜色值。
    /// </summary>
    public void setActionColor(int color) {
        actionColor = color;
        invalidate();
    }

    /// <summary>
    /// 设置高亮状态：长按进入拖动定位时加粗边框并切换为高亮色。
    /// </summary>
    public void setHighlight(boolean highlighted) {
        this.highlighted = highlighted;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.28f;

        int drawColor = highlighted ? Color.parseColor("#F59E0B") : actionColor;
        circlePaint.setColor(drawColor);
        circlePaint.setStrokeWidth(highlighted ? 9f : 5f);
        crossPaint.setColor(drawColor);
        dotPaint.setColor(drawColor);

        // 外圆（高亮时加粗边框）
        canvas.drawCircle(cx, cy, radius, circlePaint);

        // 十字线
        canvas.drawLine(cx, 0, cx, getHeight(), crossPaint);
        canvas.drawLine(0, cy, getWidth(), cy, crossPaint);

        // 中心实心点
        canvas.drawCircle(cx, cy, 7f, dotPaint);

        // 序号文字
        if (!stepNumber.isEmpty()) {
            float textSize = dpToPx(22);
            textPaint.setTextSize(textSize);
            textPaint.setColor(actionColor);
            float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f + dpToPx(10);
            canvas.drawText(stepNumber, cx, textY, textPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
