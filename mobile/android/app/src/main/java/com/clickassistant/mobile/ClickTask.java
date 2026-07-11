package com.clickassistant.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/// <summary>
/// 点击任务，聚合任务基础配置与步骤列表，对应电脑端 ClickTask。
/// 支持保存前校验、执行前校验、步骤排序与复制。
/// </summary>
public final class ClickTask {
    private String id = UUID.randomUUID().toString();
    private String name = "新建点击任务";
    private String description = "";
    private boolean enabled = true;
    private int repeatCount = 1;
    private int startDelayMs = 3000;
    private long createdAt = System.currentTimeMillis();
    private long updatedAt = System.currentTimeMillis();
    private List<TaskStep> steps = new ArrayList<>();

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public int getStartDelayMs() {
        return startDelayMs;
    }

    public void setStartDelayMs(int startDelayMs) {
        this.startDelayMs = startDelayMs;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TaskStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TaskStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    /// <summary>
    /// 按 order 排序并回填连续序号，保证步骤执行顺序稳定。
    /// </summary>
    public void normalizeStepOrders() {
        Collections.sort(steps, Comparator.comparingInt(TaskStep::getOrder));
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setOrder(i);
        }
    }

    /// <summary>
    /// 保存前校验任务基础配置与所有步骤。
    /// </summary>
    public void validateForSave() throws IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        if (repeatCount < 1) {
            throw new IllegalArgumentException("重复次数必须大于或等于 1");
        }

        if (startDelayMs < 0) {
            throw new IllegalArgumentException("开始延迟不能小于 0");
        }

        for (TaskStep step : steps) {
            step.validateForSave();
        }

        normalizeStepOrders();
    }

    /// <summary>
    /// 执行前校验，在保存校验基础上要求任务启用且至少存在一个启用步骤。
    /// </summary>
    public void validateForExecution() throws IllegalArgumentException {
        validateForSave();

        if (!enabled) {
            throw new IllegalArgumentException("任务已禁用，不能执行");
        }

        boolean hasEnabledStep = false;
        for (TaskStep step : steps) {
            if (step.isEnabled()) {
                hasEnabledStep = true;
                break;
            }
        }

        if (!hasEnabledStep) {
            throw new IllegalArgumentException("任务至少需要一个启用的步骤");
        }

        for (TaskStep step : steps) {
            if (step.isEnabled()) {
                step.validateForSave();
            }
        }
    }

    /// <summary>
    /// 复制任务并重置标识与时间，便于基于已有任务快速创建新任务。
    /// </summary>
    public ClickTask duplicate() {
        ClickTask copy = snapshotForExecution();
        copy.id = UUID.randomUUID().toString();
        copy.name = name + " 副本";
        copy.createdAt = System.currentTimeMillis();
        copy.updatedAt = copy.createdAt;

        for (TaskStep step : copy.steps) {
            step.setId(UUID.randomUUID().toString());
        }

        return copy;
    }

    /// <summary>
    /// 创建执行快照，运行期间即使用户回到应用修改任务，也不会改变本次执行内容。
    /// </summary>
    public ClickTask snapshotForExecution() {
        ClickTask snapshot = new ClickTask();
        snapshot.id = id;
        snapshot.name = name;
        snapshot.description = description;
        snapshot.enabled = enabled;
        snapshot.repeatCount = repeatCount;
        snapshot.startDelayMs = startDelayMs;
        snapshot.createdAt = createdAt;
        snapshot.updatedAt = updatedAt;
        snapshot.steps = new ArrayList<>();

        for (TaskStep source : steps) {
            TaskStep target = new TaskStep();
            target.setId(source.getId());
            target.setName(source.getName());
            target.setEnabled(source.isEnabled());
            target.setActionType(source.getActionType());
            target.setOrder(source.getOrder());
            target.setX(source.getX());
            target.setY(source.getY());
            target.setTapCount(source.getTapCount());
            target.setEndX(source.getEndX());
            target.setEndY(source.getEndY());
            target.setDurationMs(source.getDurationMs());
            target.setTextContent(source.getTextContent());
            target.setCharIntervalMs(source.getCharIntervalMs());
            target.setClickIntervalMs(source.getClickIntervalMs());
            target.setPressDurationMs(source.getPressDurationMs());
            target.setAutoFocusBeforeInput(source.isAutoFocusBeforeInput());
            target.setBeforeDelayMs(source.getBeforeDelayMs());
            snapshot.steps.add(target);
        }

        snapshot.normalizeStepOrders();
        return snapshot;
    }

    /// <summary>
    /// 序列化为 JSON，用于本地持久化。
    /// </summary>
    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("description", description);
        object.put("enabled", enabled);
        object.put("repeatCount", repeatCount);
        object.put("startDelayMs", startDelayMs);
        object.put("createdAt", createdAt);
        object.put("updatedAt", updatedAt);

        JSONArray stepArray = new JSONArray();
        for (TaskStep step : steps) {
            try {
                stepArray.put(step.toJson());
            } catch (JSONException ignored) {
                // 跳过无法序列化的步骤，避免影响整体保存。
            }
        }
        object.put("steps", stepArray);
        return object;
    }

    /// <summary>
    /// 从 JSON 还原任务，缺失字段使用安全默认值。
    /// </summary>
    public static ClickTask fromJson(JSONObject object) throws JSONException {
        ClickTask task = new ClickTask();
        task.id = object.optString("id", UUID.randomUUID().toString());
        task.name = object.optString("name", "新建点击任务");
        task.description = object.optString("description", "");
        task.enabled = object.optBoolean("enabled", true);
        task.repeatCount = object.optInt("repeatCount", 1);
        task.startDelayMs = object.optInt("startDelayMs", 3000);
        task.createdAt = object.optLong("createdAt", System.currentTimeMillis());
        task.updatedAt = object.optLong("updatedAt", System.currentTimeMillis());

        JSONArray stepArray = object.optJSONArray("steps");
        task.steps = new ArrayList<>();
        if (stepArray != null) {
            for (int i = 0; i < stepArray.length(); i++) {
                try {
                    task.steps.add(TaskStep.fromJson(stepArray.getJSONObject(i)));
                } catch (JSONException ignored) {
                    // 跳过损坏的步骤数据。
                }
            }
        }

        task.normalizeStepOrders();
        return task;
    }
}
