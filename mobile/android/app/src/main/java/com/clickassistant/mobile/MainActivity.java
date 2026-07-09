package com.clickassistant.mobile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class MainActivity extends Activity {
    private TextView accessibilityStatusText;
    private TextView lastStatusText;
    private EditText taskNameInput;
    private EditText xInput;
    private EditText yInput;
    private EditText repeatCountInput;
    private EditText startDelayInput;
    private EditText intervalInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        loadSavedTask();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private ScrollView buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Click Assistant 移动原型");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        accessibilityStatusText = new TextView(this);
        accessibilityStatusText.setTextSize(16);
        root.addView(accessibilityStatusText);

        Button openAccessibilityButton = new Button(this);
        openAccessibilityButton.setText("打开辅助功能设置");
        openAccessibilityButton.setOnClickListener(view -> openAccessibilitySettings());
        root.addView(openAccessibilityButton);

        addSectionTitle(root, "任务配置");
        taskNameInput = addTextInput(root, "任务名称", InputType.TYPE_CLASS_TEXT);
        xInput = addTextInput(root, "点击坐标 X", InputType.TYPE_CLASS_NUMBER);
        yInput = addTextInput(root, "点击坐标 Y", InputType.TYPE_CLASS_NUMBER);
        repeatCountInput = addTextInput(root, "重复次数", InputType.TYPE_CLASS_NUMBER);
        startDelayInput = addTextInput(root, "开始延迟 ms", InputType.TYPE_CLASS_NUMBER);
        intervalInput = addTextInput(root, "点击间隔 ms", InputType.TYPE_CLASS_NUMBER);

        Button saveButton = new Button(this);
        saveButton.setText("保存任务");
        saveButton.setOnClickListener(view -> saveTaskFromInputs(true));
        root.addView(saveButton);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 24, 0, 16);

        Button startButton = new Button(this);
        startButton.setText("开始执行");
        startButton.setOnClickListener(view -> startTask());
        actionRow.addView(startButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button stopButton = new Button(this);
        stopButton.setText("停止");
        stopButton.setOnClickListener(view -> stopTask());
        actionRow.addView(stopButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actionRow);

        lastStatusText = new TextView(this);
        lastStatusText.setTextSize(16);
        root.addView(lastStatusText);

        TextView safetyHint = new TextView(this);
        safetyHint.setText("请先在安全界面中验证坐标，避免误点登录、支付、权限或系统安全界面。");
        safetyHint.setPadding(0, 24, 0, 0);
        root.addView(safetyHint);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        return scrollView;
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(18);
        title.setPadding(0, 28, 0, 8);
        root.addView(title);
    }

    private EditText addTextInput(LinearLayout root, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        root.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void loadSavedTask() {
        PrototypeTask task = PrototypeTaskStore.loadTask(this);
        taskNameInput.setText(task.getName());
        xInput.setText(String.valueOf(task.getX()));
        yInput.setText(String.valueOf(task.getY()));
        repeatCountInput.setText(String.valueOf(task.getRepeatCount()));
        startDelayInput.setText(String.valueOf(task.getStartDelayMs()));
        intervalInput.setText(String.valueOf(task.getIntervalMs()));
    }

    private void refreshStatus() {
        accessibilityStatusText.setText("辅助功能：" + (isAccessibilityEnabled() ? "已启用" : "未启用"));
        lastStatusText.setText("最近状态：" + PrototypeTaskStore.loadLastStatus(this));
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void startTask() {
        if (!isAccessibilityEnabled()) {
            PrototypeTaskStore.saveLastStatus(this, "执行失败：辅助功能服务未启用");
            refreshStatus();
            Toast.makeText(this, "请先开启辅助功能服务", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        PrototypeTask task = saveTaskFromInputs(false);
        if (task == null) {
            return;
        }

        if (!ClickAssistantAccessibilityService.startTask(task)) {
            PrototypeTaskStore.saveLastStatus(this, "执行失败：辅助功能服务未连接");
            refreshStatus();
            Toast.makeText(this, "辅助功能服务尚未连接，请返回设置确认已启用", Toast.LENGTH_LONG).show();
            return;
        }

        refreshStatus();
    }

    private void stopTask() {
        ClickAssistantAccessibilityService.requestStop();
        PrototypeTaskStore.saveLastStatus(this, ExecutionState.STOPPED.getDisplayName());
        refreshStatus();
    }

    private PrototypeTask saveTaskFromInputs(boolean showToast) {
        try {
            PrototypeTask task = new PrototypeTask(
                taskNameInput.getText().toString().trim(),
                parseInt(xInput, "点击坐标 X"),
                parseInt(yInput, "点击坐标 Y"),
                parseInt(repeatCountInput, "重复次数"),
                parseInt(startDelayInput, "开始延迟"),
                parseInt(intervalInput, "点击间隔"));

            if (!task.isValid()) {
                throw new IllegalArgumentException("任务参数无效");
            }

            PrototypeTaskStore.saveTask(this, task);
            PrototypeTaskStore.saveLastStatus(this, ExecutionState.SAVED.getDisplayName());
            refreshStatus();
            if (showToast) {
                Toast.makeText(this, "任务已保存", Toast.LENGTH_SHORT).show();
            }

            return task;
        } catch (IllegalArgumentException ex) {
            PrototypeTaskStore.saveLastStatus(this, "执行失败：" + ex.getMessage());
            refreshStatus();
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private int parseInt(EditText input, String fieldName) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s 不能为空", fieldName));
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s 必须是数字", fieldName), ex);
        }
    }

    private boolean isAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServices == null) {
            return false;
        }

        ComponentName expectedComponent = new ComponentName(this, ClickAssistantAccessibilityService.class);
        String normalizedServices = enabledServices.toLowerCase(Locale.ROOT);
        String fullName = expectedComponent.flattenToString().toLowerCase(Locale.ROOT);
        String shortName = expectedComponent.flattenToShortString().toLowerCase(Locale.ROOT);
        return normalizedServices.contains(fullName) || normalizedServices.contains(shortName);
    }
}
