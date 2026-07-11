package com.clickassistant.mobile;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/// <summary>
/// 任务步骤，表示任务中的一次触控或文本动作，对应电脑端 ClickStep。
/// 字段按移动端能力裁剪：点击、滑动、等待、文本输入，并保留步骤前置/后置延迟。
/// </summary>
public final class TaskStep {
    private String id = UUID.randomUUID().toString();
    private String name = "新步骤";
    private boolean enabled = true;
    private TaskActionType actionType = TaskActionType.TAP;
    private int order = 0;

    // 点击坐标与连点次数
    private int x = 0;
    private int y = 0;
    private int tapCount = 1;

    // 滑动起点/终点与持续时间
    private int endX = 0;
    private int endY = 0;
    private int durationMs = 300;

    // 文本输入内容与逐字间隔
    private String textContent = "";
    private int charIntervalMs = 50;

    // 连点间隔和按压时长
    private int clickIntervalMs = 100;
    private int pressDurationMs = 0;

    // 输入前是否自动点击目标位置
    private boolean autoFocusBeforeInput = false;

    // 步骤执行前等待（不再有独立等待步骤，也不再有步骤后等待）
    private int beforeDelayMs = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TaskActionType getActionType() {
        return actionType;
    }

    public void setActionType(TaskActionType actionType) {
        this.actionType = actionType;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getTapCount() {
        return tapCount;
    }

    public void setTapCount(int tapCount) {
        this.tapCount = tapCount;
    }

    public int getEndX() {
        return endX;
    }

    public void setEndX(int endX) {
        this.endX = endX;
    }

    public int getEndY() {
        return endY;
    }

    public void setEndY(int endY) {
        this.endY = endY;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public int getCharIntervalMs() {
        return charIntervalMs;
    }

    public void setCharIntervalMs(int charIntervalMs) {
        this.charIntervalMs = charIntervalMs;
    }

    public int getClickIntervalMs() {
        return clickIntervalMs;
    }

    public void setClickIntervalMs(int clickIntervalMs) {
        this.clickIntervalMs = clickIntervalMs;
    }

    public int getPressDurationMs() {
        return pressDurationMs;
    }

    public void setPressDurationMs(int pressDurationMs) {
        this.pressDurationMs = pressDurationMs;
    }

    public boolean isAutoFocusBeforeInput() {
        return autoFocusBeforeInput;
    }

    public void setAutoFocusBeforeInput(boolean autoFocusBeforeInput) {
        this.autoFocusBeforeInput = autoFocusBeforeInput;
    }

    public int getBeforeDelayMs() {
        return beforeDelayMs;
    }

    public void setBeforeDelayMs(int beforeDelayMs) {
        this.beforeDelayMs = beforeDelayMs;
    }

    /// <summary>
    /// 便捷方法：从屏幕坐标点设置步骤的点击位置。
    /// </summary>
    public void setFromScreenPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /// <summary>
    /// 序列化为 JSON，用于本地持久化。
    /// </summary>
    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("enabled", enabled);
        object.put("actionType", actionType.name());
        object.put("order", order);
        object.put("x", x);
        object.put("y", y);
        object.put("tapCount", tapCount);
        object.put("endX", endX);
        object.put("endY", endY);
        object.put("durationMs", durationMs);
        object.put("textContent", textContent);
        object.put("charIntervalMs", charIntervalMs);
        object.put("clickIntervalMs", clickIntervalMs);
        object.put("pressDurationMs", pressDurationMs);
        object.put("autoFocusBeforeInput", autoFocusBeforeInput);
        object.put("beforeDelayMs", beforeDelayMs);
        return object;
    }

    /// <summary>
    /// 从 JSON 还原步骤，缺失字段使用安全默认值。
    /// </summary>
    public static TaskStep fromJson(JSONObject object) throws JSONException {
        TaskStep step = new TaskStep();
        step.id = object.optString("id", UUID.randomUUID().toString());
        step.name = object.optString("name", "新步骤");
        step.enabled = object.optBoolean("enabled", true);
        step.actionType = TaskActionType.fromName(object.optString("actionType", "TAP"));
        step.order = object.optInt("order", 0);
        step.x = object.optInt("x", 0);
        step.y = object.optInt("y", 0);
        step.tapCount = object.optInt("tapCount", 1);
        step.endX = object.optInt("endX", 0);
        step.endY = object.optInt("endY", 0);
        step.durationMs = object.optInt("durationMs", 300);
        step.textContent = object.optString("textContent", "");
        step.charIntervalMs = object.optInt("charIntervalMs", 50);
        step.clickIntervalMs = object.optInt("clickIntervalMs", 100);
        step.pressDurationMs = object.optInt("pressDurationMs", 0);
        step.autoFocusBeforeInput = object.optBoolean("autoFocusBeforeInput", false);
        step.beforeDelayMs = object.optInt("beforeDelayMs", 0);
        return step;
    }

    /// <summary>
    /// 保存前校验，抛出中文异常信息以便界面提示。
    /// </summary>
    public void validateForSave() throws IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("步骤名称不能为空");
        }

        if (beforeDelayMs < 0) {
            throw new IllegalArgumentException("步骤前等待时间不能小于 0");
        }

        if (durationMs < 0 || clickIntervalMs < 0 || pressDurationMs < 0) {
            throw new IllegalArgumentException("间隔和持续时间不能小于 0");
        }

        if (charIntervalMs < 0) {
            throw new IllegalArgumentException("字符间隔不能小于 0");
        }

        switch (actionType) {
            case TAP:
                if (tapCount < 1) {
                    throw new IllegalArgumentException("点击次数必须大于或等于 1");
                }

                if (tapCount > 10000) {
                    throw new IllegalArgumentException("点击次数不能超过 10000");
                }

                if (x < 0 || y < 0) {
                    throw new IllegalArgumentException("点击坐标不能为负");
                }
                break;
            case SWIPE:
                if (durationMs < 1) {
                    throw new IllegalArgumentException("滑动持续时间必须大于 0");
                }

                if (x < 0 || y < 0 || endX < 0 || endY < 0) {
                    throw new IllegalArgumentException("滑动坐标不能为负");
                }
                break;
            case TEXT_INPUT:
                if (textContent == null || textContent.isEmpty()) {
                    throw new IllegalArgumentException("文本输入内容不能为空");
                }

                if (textContent.length() > 10000) {
                    throw new IllegalArgumentException("文本输入不能超过 10000 个字符");
                }
                break;
            default:
                break;
        }
    }

    /// <summary>
    /// 生成面向用户的步骤摘要，用于列表与执行确认展示。
    /// </summary>
    public String getSummary() {
        switch (actionType) {
            case TAP:
                return String.format(Locale.ROOT, "点击 (%d,%d) ×%d", x, y, tapCount);
            case SWIPE:
                return String.format(Locale.ROOT, "滑动 (%d,%d)→(%d,%d) %dms", x, y, endX, endY, durationMs);
            case TEXT_INPUT:
                return String.format(Locale.ROOT, "文本输入 %d 字", textContent.length());
            default:
                return name;
        }
    }
}
