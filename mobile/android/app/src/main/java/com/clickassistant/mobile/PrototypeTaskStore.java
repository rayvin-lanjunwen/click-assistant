package com.clickassistant.mobile;

import android.content.Context;
import android.content.SharedPreferences;

public final class PrototypeTaskStore {
    private static final String PREFERENCES_NAME = "click_assistant_android_prototype";
    private static final String KEY_NAME = "task_name";
    private static final String KEY_X = "task_x";
    private static final String KEY_Y = "task_y";
    private static final String KEY_REPEAT_COUNT = "task_repeat_count";
    private static final String KEY_START_DELAY_MS = "task_start_delay_ms";
    private static final String KEY_INTERVAL_MS = "task_interval_ms";
    private static final String KEY_LAST_STATUS = "last_status";

    private PrototypeTaskStore() {
    }

    public static PrototypeTask loadTask(Context context) {
        SharedPreferences preferences = getPreferences(context);
        return new PrototypeTask(
            preferences.getString(KEY_NAME, "固定坐标点击测试任务"),
            preferences.getInt(KEY_X, 500),
            preferences.getInt(KEY_Y, 900),
            preferences.getInt(KEY_REPEAT_COUNT, 3),
            preferences.getInt(KEY_START_DELAY_MS, 1000),
            preferences.getInt(KEY_INTERVAL_MS, 800));
    }

    public static void saveTask(Context context, PrototypeTask task) {
        getPreferences(context)
            .edit()
            .putString(KEY_NAME, task.getName())
            .putInt(KEY_X, task.getX())
            .putInt(KEY_Y, task.getY())
            .putInt(KEY_REPEAT_COUNT, task.getRepeatCount())
            .putInt(KEY_START_DELAY_MS, task.getStartDelayMs())
            .putInt(KEY_INTERVAL_MS, task.getIntervalMs())
            .apply();
    }

    public static String loadLastStatus(Context context) {
        return getPreferences(context).getString(KEY_LAST_STATUS, ExecutionState.IDLE.getDisplayName());
    }

    public static void saveLastStatus(Context context, String status) {
        getPreferences(context)
            .edit()
            .putString(KEY_LAST_STATUS, status)
            .apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
