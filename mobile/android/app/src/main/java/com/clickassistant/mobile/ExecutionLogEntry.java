package com.clickassistant.mobile;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/// <summary>
/// 执行日志条目，对应电脑端运行记录，用于本地保存每次执行的结果与原因。
/// </summary>
public final class ExecutionLogEntry {
    private String id = UUID.randomUUID().toString();
    private String taskName = "";
    private String status = "";
    private String message = "";
    private long timestamp = System.currentTimeMillis();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public ExecutionLogEntry setTaskName(String taskName) {
        this.taskName = taskName == null ? "" : taskName;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public ExecutionLogEntry setStatus(String status) {
        this.status = status == null ? "" : status;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public ExecutionLogEntry setMessage(String message) {
        this.message = message == null ? "" : message;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /// <summary>
    /// 序列化为 JSON，用于本地持久化执行日志。
    /// </summary>
    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("taskName", taskName);
        object.put("status", status);
        object.put("message", message);
        object.put("timestamp", timestamp);
        return object;
    }

    /// <summary>
    /// 从 JSON 还原执行日志条目。
    /// </summary>
    public static ExecutionLogEntry fromJson(JSONObject object) {
        ExecutionLogEntry entry = new ExecutionLogEntry();
        entry.id = object.optString("id", UUID.randomUUID().toString());
        entry.taskName = object.optString("taskName", "");
        entry.status = object.optString("status", "");
        entry.message = object.optString("message", "");
        entry.timestamp = object.optLong("timestamp", System.currentTimeMillis());
        return entry;
    }
}
