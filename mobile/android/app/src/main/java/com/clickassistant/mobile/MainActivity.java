package com.clickassistant.mobile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
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
        loadSavedTask();
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
        addHelperText(root, "先用点选位置抓取坐标，再按需微调数字。坐标以屏幕左上角为 (0,0)。");

        Button pickPositionButton = new Button(this);
        pickPositionButton.setText("点选点击位置");
        pickPositionButton.setOnClickListener(view -> pickClickPosition());
        root.addView(pickPositionButton);

        xInput = addTextInput(root, "X 坐标，向右增大", InputType.TYPE_CLASS_NUMBER);
        yInput = addTextInput(root, "Y 坐标，向下增大", InputType.TYPE_CLASS_NUMBER);
        addHelperText(root, "例如 X=500、Y=900 表示从屏幕左上角向右 500、向下 900 的位置。");

        repeatCountInput = addTextInput(root, "点击次数，例如 3", InputType.TYPE_CLASS_NUMBER);
        startDelayInput = addTextInput(root, "开始延迟，毫秒，1000=1秒", InputType.TYPE_CLASS_NUMBER);
        intervalInput = addTextInput(root, "点击间隔，毫秒，800=0.8秒", InputType.TYPE_CLASS_NUMBER);
        addHelperText(root, "开始延迟用于切到目标界面；点击间隔用于控制连续点击速度。");

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

    private void addHelperText(LinearLayout root, String text) {
        TextView helper = new TextView(this);
        helper.setText(text);
        helper.setTextColor(Color.DKGRAY);
        helper.setTextSize(14);
        helper.setPadding(0, 0, 0, 12);
        root.addView(helper);
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

    private void pickClickPosition() {
        if (!isAccessibilityEnabled()) {
            PrototypeTaskStore.saveLastStatus(this, "取点失败：辅助功能服务未启用");
            refreshStatus();
            Toast.makeText(this, "请先开启辅助功能服务，再点选位置", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        PrototypeTask task = buildTaskForCoordinatePicker();
        if (task == null) {
            return;
        }

        PrototypeTaskStore.saveTask(this, task);
        if (!ClickAssistantAccessibilityService.startCoordinatePick(5000)) {
            PrototypeTaskStore.saveLastStatus(this, "取点失败：辅助功能服务未连接");
            refreshStatus();
            Toast.makeText(this, "辅助功能服务尚未连接，请返回设置确认已启用", Toast.LENGTH_LONG).show();
            return;
        }

        refreshStatus();
        Toast.makeText(this, "5 秒内切到目标界面，出现取点层后点一下目标位置", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
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

    private PrototypeTask buildTaskForCoordinatePicker() {
        PrototypeTask current = PrototypeTaskStore.loadTask(this);
        try {
            String name = taskNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                name = current.getName();
            }

            PrototypeTask task = new PrototypeTask(
                name,
                parseOptionalInt(xInput, current.getX(), "点击坐标 X"),
                parseOptionalInt(yInput, current.getY(), "点击坐标 Y"),
                parseOptionalInt(repeatCountInput, current.getRepeatCount(), "重复次数"),
                parseOptionalInt(startDelayInput, current.getStartDelayMs(), "开始延迟"),
                parseOptionalInt(intervalInput, current.getIntervalMs(), "点击间隔"));

            if (!task.isValid()) {
                throw new IllegalArgumentException("任务参数无效");
            }

            return task;
        } catch (IllegalArgumentException ex) {
            PrototypeTaskStore.saveLastStatus(this, "取点失败：" + ex.getMessage());
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

    private int parseOptionalInt(EditText input, int defaultValue, String fieldName) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            return defaultValue;
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
