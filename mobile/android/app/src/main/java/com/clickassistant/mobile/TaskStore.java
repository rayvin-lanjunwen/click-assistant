package com.clickassistant.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/// <summary>
/// 任务存储，使用 SharedPreferences 以 JSON 数组保存多个任务，
/// 并兼容旧版单任务原型的本地数据迁移。同时负责取点目标与最近状态的读写。
/// </summary>
public final class TaskStore {
    private static final String PREFERENCES_NAME = "click_assistant_android";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_PICK_TARGET = "pick_target";

    private static final String LEGACY_PREFERENCES_NAME = "click_assistant_android_prototype";

    private TaskStore() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    /// <summary>
    /// 加载全部任务，首次访问时若检测到旧版数据会自动迁移。
    /// </summary>
    public static List<ClickTask> loadTasks(Context context) {
        migrateIfNeeded(context);
        List<ClickTask> tasks = new ArrayList<>();
        String raw = preferences(context).getString(KEY_TASKS, null);
        if (raw == null) {
            return tasks;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                try {
                    tasks.add(ClickTask.fromJson(array.getJSONObject(i)));
                } catch (JSONException ignored) {
                    // 跳过损坏的任务数据。
                }
            }
        } catch (JSONException ignored) {
            // 整体解析失败时返回空列表，避免崩溃。
        }

        return tasks;
    }

    /// <summary>
    /// 保存任务列表为 JSON 数组。
    /// </summary>
    public static void saveTasks(Context context, List<ClickTask> tasks) {
        JSONArray array = new JSONArray();
        for (ClickTask task : tasks) {
            try {
                array.put(task.toJson());
            } catch (JSONException ignored) {
                // 跳过无法序列化的任务。
            }
        }

        preferences(context).edit().putString(KEY_TASKS, array.toString()).apply();
    }

    /// <summary>
    /// 按标识查找任务。
    /// </summary>
    public static ClickTask getTask(Context context, String id) {
        for (ClickTask task : loadTasks(context)) {
            if (task.getId().equals(id)) {
                return task;
            }
        }

        return null;
    }

    /// <summary>
    /// 新增或更新任务，已存在相同标识则覆盖。
    /// </summary>
    public static void upsertTask(Context context, ClickTask task) {
        task.setUpdatedAt(System.currentTimeMillis());
        List<ClickTask> tasks = loadTasks(context);
        boolean replaced = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                tasks.set(i, task);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            tasks.add(task);
        }

        saveTasks(context, tasks);
    }

    /// <summary>
    /// 按标识删除任务。
    /// </summary>
    public static void deleteTask(Context context, String id) {
        List<ClickTask> tasks = loadTasks(context);
        tasks.removeIf(task -> task.getId().equals(id));
        saveTasks(context, tasks);
    }

    /// <summary>
    /// 从旧版单任务原型迁移到多任务结构，仅执行一次。
    /// </summary>
    private static void migrateIfNeeded(Context context) {
        SharedPreferences current = preferences(context);
        if (current.contains(KEY_TASKS)) {
            return;
        }

        SharedPreferences legacy = context.getApplicationContext()
                .getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!legacy.contains("task_name")) {
            return;
        }

        ClickTask task = new ClickTask();
        task.setName(legacy.getString("task_name", "固定坐标点击测试任务"));

        TaskStep step = new TaskStep();
        step.setActionType(TaskActionType.TAP);
        step.setName("点击步骤");
        step.setX(legacy.getInt("task_x", 500));
        step.setY(legacy.getInt("task_y", 900));
        step.setTapCount(Math.max(1, legacy.getInt("task_repeat_count", 3)));
        step.setAfterDelayMs(legacy.getInt("task_interval_ms", 800));
        task.setStartDelayMs(legacy.getInt("task_start_delay_ms", 1000));
        task.setRepeatCount(1);
        task.setSteps(new ArrayList<>());
        task.getSteps().add(step);

        List<ClickTask> tasks = new ArrayList<>();
        tasks.add(task);
        saveTasks(context, tasks);

        legacy.edit().clear().apply();
    }

    /// <summary>
    /// 读取最近一次执行状态。
    /// </summary>
    public static String loadLastStatus(Context context) {
        return preferences(context).getString(KEY_LAST_STATUS, ExecutionState.IDLE.getDisplayName());
    }

    /// <summary>
    /// 保存最近一次执行状态。
    /// </summary>
    public static void saveLastStatus(Context context, String status) {
        preferences(context).edit().putString(KEY_LAST_STATUS, status).apply();
    }

    /// <summary>
    /// 保存取点目标，描述需要采集坐标的任务、步骤与模式。
    /// </summary>
    public static void savePickTarget(Context context, JSONObject target) {
        preferences(context).edit().putString(KEY_PICK_TARGET, target.toString()).apply();
    }

    /// <summary>
    /// 读取取点目标，无目标时返回 null。
    /// </summary>
    public static JSONObject loadPickTarget(Context context) {
        String raw = preferences(context).getString(KEY_PICK_TARGET, null);
        if (raw == null) {
            return null;
        }

        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    /// <summary>
    /// 清除取点目标。
    /// </summary>
    public static void clearPickTarget(Context context) {
        preferences(context).edit().remove(KEY_PICK_TARGET).apply();
    }
}
