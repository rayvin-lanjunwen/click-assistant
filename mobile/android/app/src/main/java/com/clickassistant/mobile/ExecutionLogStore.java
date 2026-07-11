package com.clickassistant.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/// <summary>
/// 执行日志存储，以 JSON 数组保存最近的执行记录，对应电脑端执行日志。
/// 仅保留最近固定条数，避免无限制增长。
/// </summary>
public final class ExecutionLogStore {
    private static final String PREFERENCES_NAME = "click_assistant_android_log";
    private static final String KEY_LOG = "execution_log";
    private static final int MAX_ENTRIES = 100;

    private ExecutionLogStore() {
    }

    /// <summary>
    /// 加载执行日志，按时间升序返回。
    /// </summary>
    public static List<ExecutionLogEntry> loadLog(Context context) {
        List<ExecutionLogEntry> entries = new ArrayList<>();
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_LOG, null);
        if (raw == null) {
            return entries;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                try {
                    entries.add(ExecutionLogEntry.fromJson(array.getJSONObject(i)));
                } catch (JSONException ignored) {
                    // 跳过损坏的日志条目。
                }
            }
        } catch (JSONException ignored) {
            // 整体解析失败时返回空列表。
        }

        return entries;
    }

    /// <summary>
    /// 新增一条执行日志，超出上限时丢弃最旧记录。
    /// </summary>
    public static void addEntry(Context context, ExecutionLogEntry entry) {
        List<ExecutionLogEntry> entries = loadLog(context);
        entries.add(entry);

        if (entries.size() > MAX_ENTRIES) {
            Collections.sort(entries, Comparator.comparingLong(ExecutionLogEntry::getTimestamp));
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES, entries.size()));
        }

        JSONArray array = new JSONArray();
        for (ExecutionLogEntry item : entries) {
            try {
                array.put(item.toJson());
            } catch (JSONException ignored) {
                // 跳过无法序列化的条目。
            }
        }

        context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LOG, array.toString())
                .apply();
    }

    /// <summary>
    /// 查询指定任务最近一次执行的状态，找不到时返回 null。
    /// loadLog 按时间升序返回，倒序遍历即从最新记录开始匹配。
    /// </summary>
    public static String getLastStatusByTaskId(Context context, String taskId) {
        if (taskId == null || taskId.isEmpty()) return null;
        List<ExecutionLogEntry> entries = loadLog(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            ExecutionLogEntry entry = entries.get(i);
            if (taskId.equals(entry.getTaskId())) {
                return entry.getStatus();
            }
        }
        return null;
    }

    /// <summary>
    /// 清空执行日志。
    /// </summary>
    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOG)
                .apply();
    }
}
