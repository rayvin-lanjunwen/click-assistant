package com.clickassistant.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/// <summary>
/// 主界面，对应电脑端任务中心：任务库、步骤编辑、执行前确认、执行日志与启停控制。
/// 通过辅助功能服务分发手势，支持点击、滑动、等待与文本输入。
/// </summary>
public final class MainActivity extends Activity {
    private enum Page {
        HOME,
        LIBRARY,
        EDITOR,
        LOGS
    }

    private static final int COLOR_BACKGROUND = Color.rgb(7, 17, 31);
    private static final int COLOR_GLASS = Color.rgb(20, 36, 59);
    private static final int COLOR_PRIMARY = Color.rgb(55, 104, 224);
    private static final int COLOR_BORDER = Color.rgb(80, 126, 220);

    private Page currentPage = Page.HOME;
    private TextView accessibilityStatusText;
    private TextView lastStatusText;
    private Spinner taskSpinner;
    private EditText taskNameInput;
    private EditText taskDescInput;
    private CheckBox taskEnabledBox;
    private EditText repeatCountInput;
    private EditText startDelayInput;

    private LinearLayout stepsContainer;
    private LinearLayout logContainer;

    private List<ClickTask> tasks = new ArrayList<>();
    private ClickTask selectedTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPage(Page.HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasks();
        refreshStatus();
        renderLog();
    }

    @Override
    public void onBackPressed() {
        if (currentPage == Page.EDITOR) {
            showPage(Page.LIBRARY);
        } else if (currentPage == Page.LIBRARY || currentPage == Page.LOGS) {
            showPage(Page.HOME);
        } else {
            super.onBackPressed();
        }
    }

    // ---------- 界面构建 ----------

    private View buildContentView() {
        switch (currentPage) {
            case LIBRARY:
                return buildTaskLibraryPage();
            case EDITOR:
                return buildTaskEditorPage();
            case LOGS:
                return buildExecutionLogPage();
            case HOME:
            default:
                return buildHomePage();
        }
    }

    private ScrollView buildHomePage() {
        LinearLayout root = createPageRoot();

        addPageTitle(root, "Click Assistant", "点击与输入助手");

        accessibilityStatusText = new TextView(this);
        accessibilityStatusText.setTextSize(16);
        accessibilityStatusText.setTextColor(Color.WHITE);
        accessibilityStatusText.setPadding(dp(16), dp(14), dp(16), dp(14));
        accessibilityStatusText.setBackground(glassBackground(false));
        root.addView(accessibilityStatusText, blockParams());

        Button permissionButton = createPageButton("打开辅助功能设置", false);
        permissionButton.setOnClickListener(view -> openAccessibilitySettings());
        root.addView(permissionButton, blockParams());

        addSectionTitle(root, "开始使用");
        Button newTaskButton = createPageButton("新建任务", true);
        newTaskButton.setOnClickListener(view -> showNewTaskTypeDialog());
        root.addView(newTaskButton, blockParams());

        Button libraryButton = createPageButton("任务库", false);
        libraryButton.setOnClickListener(view -> showPage(Page.LIBRARY));
        root.addView(libraryButton, blockParams());

        Button logButton = createPageButton("执行日志", false);
        logButton.setOnClickListener(view -> showPage(Page.LOGS));
        root.addView(logButton, blockParams());

        lastStatusText = new TextView(this);
        lastStatusText.setTextColor(Color.LTGRAY);
        lastStatusText.setTextSize(15);
        lastStatusText.setPadding(dp(4), dp(18), dp(4), dp(8));
        root.addView(lastStatusText);

        TextView safetyHint = new TextView(this);
        safetyHint.setText("只执行你主动配置的任务；首次测试请使用安全空白页面。");
        safetyHint.setTextColor(Color.LTGRAY);
        safetyHint.setTextSize(14);
        root.addView(safetyHint);

        return wrapInScrollView(root);
    }

    private ScrollView buildTaskLibraryPage() {
        LinearLayout root = createPageRoot();
        addBackButton(root, "返回首页", Page.HOME);
        addPageTitle(root, "任务库", "选择任务后进入编辑页");

        buildTaskLibraryRow(root);

        Button editButton = createPageButton("编辑选中任务", true);
        editButton.setOnClickListener(view -> {
            if (selectedTask == null) {
                Toast.makeText(this, "请先选择任务", Toast.LENGTH_SHORT).show();
                return;
            }
            showPage(Page.EDITOR);
        });
        root.addView(editButton, blockParams());

        lastStatusText = new TextView(this);
        lastStatusText.setTextColor(Color.LTGRAY);
        lastStatusText.setPadding(dp(4), dp(18), dp(4), dp(8));
        root.addView(lastStatusText);
        return wrapInScrollView(root);
    }

    private ScrollView buildTaskEditorPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        root.setBackgroundColor(COLOR_BACKGROUND);

        addBackButton(root, "返回任务库", Page.LIBRARY);
        addPageTitle(root, "编辑任务", "基础信息、步骤与执行控制");

        addSectionTitle(root, "基础信息");
        taskNameInput = addTextInput(root, "任务名称", InputType.TYPE_CLASS_TEXT);
        taskDescInput = addTextInput(root, "任务描述（可选）", InputType.TYPE_CLASS_TEXT);
        taskEnabledBox = new CheckBox(this);
        taskEnabledBox.setText("启用该任务");
        taskEnabledBox.setTextColor(Color.WHITE);
        root.addView(taskEnabledBox);
        repeatCountInput = addTextInput(root, "重复次数，例如 3", InputType.TYPE_CLASS_NUMBER);
        startDelayInput = addTextInput(root, "开始延迟，毫秒，1000=1秒", InputType.TYPE_CLASS_NUMBER);
        Button saveButton = new Button(this);
        saveButton.setText("保存任务");
        saveButton.setOnClickListener(view -> saveTaskFromInputs(true));
        styleButton(saveButton, true);
        root.addView(saveButton, blockParams());

        addSectionTitle(root, "步骤设置");
        stepsContainer = new LinearLayout(this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(stepsContainer);

        Button addStepButton = new Button(this);
        addStepButton.setText("添加步骤");
        addStepButton.setOnClickListener(view -> showAddStepDialog());
        styleButton(addStepButton, false);
        root.addView(addStepButton, blockParams());

        addSectionTitle(root, "执行控制");
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 8, 0, 8);
        Button startButton = new Button(this);
        startButton.setText("开始执行");
        startButton.setOnClickListener(view -> startTask());
        Button pauseButton = new Button(this);
        pauseButton.setText("暂停");
        pauseButton.setOnClickListener(view -> pauseTask());
        Button resumeButton = new Button(this);
        resumeButton.setText("继续");
        resumeButton.setOnClickListener(view -> resumeTask());
        Button stopButton = new Button(this);
        stopButton.setText("停止");
        stopButton.setOnClickListener(view -> stopTask());
        styleButton(startButton, true);
        styleButton(pauseButton, false);
        styleButton(resumeButton, false);
        styleButton(stopButton, false);
        actionRow.addView(startButton, weightParams());
        actionRow.addView(pauseButton, weightParams());
        actionRow.addView(resumeButton, weightParams());
        actionRow.addView(stopButton, weightParams());
        root.addView(actionRow);

        lastStatusText = new TextView(this);
        lastStatusText.setTextSize(16);
        lastStatusText.setTextColor(Color.WHITE);
        lastStatusText.setPadding(0, dp(12), 0, dp(6));
        root.addView(lastStatusText);

        TextView safetyHint = new TextView(this);
        safetyHint.setText("请先在安全界面验证坐标与步骤，避免误点登录、支付、权限或系统安全界面。");
        safetyHint.setPadding(0, 16, 0, 0);
        safetyHint.setTextColor(Color.LTGRAY);
        root.addView(safetyHint);

        return wrapInScrollView(root);
    }

    private ScrollView buildExecutionLogPage() {
        LinearLayout root = createPageRoot();
        addBackButton(root, "返回首页", Page.HOME);
        addPageTitle(root, "执行日志", "查看最近任务的执行结果");

        logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(logContainer);

        Button clearLogButton = new Button(this);
        clearLogButton.setText("清空执行日志");
        clearLogButton.setOnClickListener(view -> {
            ExecutionLogStore.clear(this);
            renderLog();
        });
        styleButton(clearLogButton, false);
        root.addView(clearLogButton, blockParams());

        return wrapInScrollView(root);
    }

    private void showPage(Page page) {
        currentPage = page;
        accessibilityStatusText = null;
        lastStatusText = null;
        taskSpinner = null;
        taskNameInput = null;
        taskDescInput = null;
        taskEnabledBox = null;
        repeatCountInput = null;
        startDelayInput = null;
        stepsContainer = null;
        logContainer = null;
        setContentView(buildContentView());
        loadTasks();
        refreshStatus();
        renderLog();
    }

    private LinearLayout createPageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        root.setBackgroundColor(COLOR_BACKGROUND);
        return root;
    }

    private ScrollView wrapInScrollView(LinearLayout root) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        scrollView.addView(root);
        return scrollView;
    }

    private void addPageTitle(LinearLayout root, String titleText, String subtitleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setPadding(0, dp(10), 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);
    }

    private void addBackButton(LinearLayout root, String text, Page target) {
        Button button = createPageButton(text, false);
        button.setOnClickListener(view -> showPage(target));
        root.addView(button, blockParams());
    }

    private Button createPageButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        styleButton(button, primary);
        button.setMinHeight(dp(52));
        return button;
    }

    private void styleButton(Button button, boolean primary) {
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(glassBackground(primary));
    }

    private GradientDrawable glassBackground(boolean primary) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? COLOR_PRIMARY : COLOR_GLASS);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), COLOR_BORDER);
        return background;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private void buildTaskLibraryRow(LinearLayout root) {
        taskSpinner = new Spinner(this);
        taskSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < tasks.size()) {
                    selectedTask = tasks.get(position);
                    populateTaskFields();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 保持当前选中任务不变。
            }
        });
        taskSpinner.setPadding(dp(12), dp(8), dp(12), dp(8));
        taskSpinner.setBackground(glassBackground(false));
        root.addView(taskSpinner, blockParams());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        Button newButton = new Button(this);
        newButton.setText("新建任务");
        newButton.setOnClickListener(view -> showNewTaskTypeDialog());
        Button duplicateButton = new Button(this);
        duplicateButton.setText("复制任务");
        duplicateButton.setOnClickListener(view -> duplicateSelectedTask());
        Button deleteButton = new Button(this);
        deleteButton.setText("删除任务");
        deleteButton.setOnClickListener(view -> deleteSelectedTask());
        styleButton(newButton, true);
        styleButton(duplicateButton, false);
        styleButton(deleteButton, false);
        row.addView(newButton, weightParams());
        row.addView(duplicateButton, weightParams());
        row.addView(deleteButton, weightParams());
        root.addView(row);
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, dp(24), 0, dp(8));
        root.addView(title);
    }

    private EditText addTextInput(LinearLayout root, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(glassBackground(false));
        root.addView(input, blockParams());
        return input;
    }

    // ---------- 数据加载与刷新 ----------

    private void loadTasks() {
        tasks = TaskStore.loadTasks(this);
        if (tasks.isEmpty()) {
            ClickTask task = new ClickTask();
            task.setName("新建点击任务");
            TaskStep step = new TaskStep();
            step.setActionType(TaskActionType.TAP);
            step.setName("点击步骤");
            step.setX(500);
            step.setY(900);
            step.setTapCount(1);
            step.setClickIntervalMs(100);
            task.setSteps(new ArrayList<>());
            task.getSteps().add(step);
            TaskStore.upsertTask(this, task);
            tasks = TaskStore.loadTasks(this);
        }

        if (selectedTask == null && !tasks.isEmpty()) {
            selectedTask = tasks.get(0);
        }

        int selectedIndex = 0;
        for (int i = 0; i < tasks.size(); i++) {
            if (selectedTask != null && tasks.get(i).getId().equals(selectedTask.getId())) {
                selectedIndex = i;
                break;
            }
        }

        // 始终切换到刚从存储加载的实例，确保坐标拾取等跨界面回写能立即显示。
        selectedTask = tasks.get(selectedIndex);

        if (taskSpinner != null) {
            List<String> names = new ArrayList<>();
            for (ClickTask task : tasks) {
                names.add(task.getName());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    this,
                    android.R.layout.simple_spinner_item,
                    names) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, convertView, parent);
                    view.setTextColor(Color.WHITE);
                    view.setPadding(dp(12), dp(10), dp(12), dp(10));
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                    view.setTextColor(Color.BLACK);
                    view.setPadding(dp(12), dp(12), dp(12), dp(12));
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            taskSpinner.setAdapter(adapter);
            taskSpinner.setSelection(selectedIndex);
        }
        populateTaskFields();
        renderSteps();
    }

    private void populateTaskFields() {
        if (selectedTask == null
                || taskNameInput == null
                || taskDescInput == null
                || taskEnabledBox == null
                || repeatCountInput == null
                || startDelayInput == null) {
            return;
        }

        taskNameInput.setText(selectedTask.getName());
        taskDescInput.setText(selectedTask.getDescription());
        taskEnabledBox.setChecked(selectedTask.isEnabled());
        repeatCountInput.setText(String.valueOf(selectedTask.getRepeatCount()));
        startDelayInput.setText(String.valueOf(selectedTask.getStartDelayMs()));
    }

    private void refreshStatus() {
        if (accessibilityStatusText != null) {
            accessibilityStatusText.setText("辅助功能：" + (isAccessibilityEnabled() ? "已启用" : "未启用"));
        }
        if (lastStatusText != null) {
            lastStatusText.setText("最近状态：" + TaskStore.loadLastStatus(this));
        }
    }

    private void renderSteps() {
        if (stepsContainer == null) {
            return;
        }
        stepsContainer.removeAllViews();
        if (selectedTask == null) {
            return;
        }

        List<TaskStep> steps = selectedTask.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            TaskStep step = steps.get(i);
            stepsContainer.addView(buildStepRow(step, i, steps.size()), blockParams());
        }

        if (steps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无步骤，点击“添加步骤”创建");
            empty.setTextColor(Color.LTGRAY);
            stepsContainer.addView(empty);
        }
    }

    private LinearLayout buildStepRow(TaskStep step, int index, int total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(glassBackground(false));

        // 步骤编号 + 名称 + 动作类型
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);

        // 序号圆圈
        TextView numberView = new TextView(this);
        int circleColor = getStepColor(step.getActionType());
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(circleColor);
        circleBg.setSize(dp(28), dp(28));
        numberView.setBackground(circleBg);
        numberView.setText(String.valueOf(index + 1));
        numberView.setTextColor(Color.WHITE);
        numberView.setTextSize(13);
        numberView.setGravity(Gravity.CENTER);
        numberView.setPadding(0, 0, 0, 0);
        headerRow.addView(numberView);

        TextView summary = new TextView(this);
        summary.setText(String.format(Locale.ROOT, "  %s（%s）%s",
                step.getName(),
                step.getActionType().getDisplayName(),
                step.isEnabled() ? "" : " [已禁用]"));
        summary.setTextSize(16);
        summary.setTextColor(Color.WHITE);
        headerRow.addView(summary);
        row.addView(headerRow);

        TextView detail = new TextView(this);
        detail.setText("  " + step.getSummary());
        detail.setTextColor(Color.LTGRAY);
        row.addView(detail);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button editButton = new Button(this);
        editButton.setText("编辑");
        editButton.setOnClickListener(view -> showStepEditorDialog(step));
        Button deleteButton = new Button(this);
        deleteButton.setText("删除");
        deleteButton.setOnClickListener(view -> deleteStep(step));
        styleButton(editButton, true);
        styleButton(deleteButton, false);
        buttons.addView(editButton, weightParams());
        buttons.addView(deleteButton, weightParams());
        row.addView(buttons);

        return row;
    }

    private int getStepColor(TaskActionType actionType) {
        switch (actionType) {
            case TAP:
                return COLOR_PRIMARY; // 蓝色
            case SWIPE:
                return Color.rgb(22, 160, 64); // 绿色
            case TEXT_INPUT:
                return Color.rgb(193, 127, 34); // 橙色
            default:
                return COLOR_PRIMARY;
        }
    }

    private void renderLog() {
        if (logContainer == null) {
            return;
        }
        logContainer.removeAllViews();
        List<ExecutionLogEntry> entries = ExecutionLogStore.loadLog(this);
        Collections.reverse(entries);

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无执行记录");
            empty.setTextColor(Color.LTGRAY);
            logContainer.addView(empty);
            return;
        }

        int limit = Math.min(entries.size(), 30);
        for (int i = 0; i < limit; i++) {
            ExecutionLogEntry entry = entries.get(i);
            TextView line = new TextView(this);
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);
            String time = format.format(new java.util.Date(entry.getTimestamp()));
            String taskName = entry.getTaskName().isEmpty() ? "未命名任务" : entry.getTaskName();
            line.setText(String.format(Locale.ROOT, "[%s] %s · %s\n%s",
                    time, taskName, entry.getStatus(), entry.getMessage()));
            line.setTextSize(14);
            line.setTextColor(Color.WHITE);
            line.setPadding(dp(14), dp(12), dp(14), dp(12));
            line.setBackground(glassBackground(false));
            logContainer.addView(line, blockParams());
        }
    }

    // ---------- 任务操作 ----------

    private void showNewTaskTypeDialog() {
        String[] types = {"点击任务", "文本输入任务", "点击 + 文本组合", "空白任务"};
        new AlertDialog.Builder(this)
                .setTitle("新建任务")
                .setItems(types, (dialog, which) -> createTaskTemplate(which))
                .setNegativeButton("取消", null)
                .show();
    }

    /// <summary>
    /// 创建常用任务模板。组合任务本质是按顺序执行点击与文本输入两个步骤。
    /// </summary>
    private void createTaskTemplate(int templateIndex) {
        ClickTask task = new ClickTask();
        task.setSteps(new ArrayList<>());

        switch (templateIndex) {
            case 0:
                task.setName("新建点击任务");
                task.getSteps().add(createDefaultStep(TaskActionType.TAP, 0));
                break;
            case 1:
                task.setName("新建文本输入任务");
                task.getSteps().add(createDefaultStep(TaskActionType.TEXT_INPUT, 0));
                break;
            case 2:
                task.setName("新建点击与文本组合任务");
                TaskStep tapStep = createDefaultStep(TaskActionType.TAP, 0);
                tapStep.setName("点击目标输入框");
                TaskStep textStep = createDefaultStep(TaskActionType.TEXT_INPUT, 1);
                textStep.setName("输入文本");
                task.getSteps().add(tapStep);
                task.getSteps().add(textStep);
                break;
            default:
                task.setName("新建空白任务");
                break;
        }

        TaskStore.upsertTask(this, task);
        selectedTask = task;
        showPage(Page.EDITOR);
        Toast.makeText(this, "已新建任务", Toast.LENGTH_SHORT).show();
    }

    /// <summary>
    /// 保存当前编辑内容后复制任务，复制品拥有独立标识和步骤，避免后续编辑互相影响。
    /// </summary>
    private void duplicateSelectedTask() {
        if (selectedTask == null) {
            return;
        }
        if (currentPage == Page.EDITOR && !saveTaskFromInputs(false)) {
            return;
        }

        ClickTask copy = selectedTask.duplicate();
        TaskStore.upsertTask(this, copy);
        selectedTask = copy;
        loadTasks();
        Toast.makeText(this, "已复制任务", Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedTask() {
        if (selectedTask == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("删除任务")
                .setMessage("确定删除任务“" + selectedTask.getName() + "”吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    TaskStore.deleteTask(this, selectedTask.getId());
                    selectedTask = null;
                    loadTasks();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean saveTaskFromInputs(boolean showToast) {
        if (selectedTask == null) {
            return false;
        }

        try {
            selectedTask.setName(taskNameInput.getText().toString().trim());
            selectedTask.setDescription(taskDescInput.getText().toString().trim());
            selectedTask.setEnabled(taskEnabledBox.isChecked());
            selectedTask.setRepeatCount(parseInt(repeatCountInput, "重复次数"));
            selectedTask.setStartDelayMs(parseInt(startDelayInput, "开始延迟"));
            selectedTask.validateForSave();
            TaskStore.upsertTask(this, selectedTask);
            TaskStore.saveLastStatus(this, ExecutionState.SAVED.getDisplayName());
            refreshStatus();
            if (showToast) {
                Toast.makeText(this, "任务已保存", Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (IllegalArgumentException ex) {
            TaskStore.saveLastStatus(this, "保存失败：" + ex.getMessage());
            refreshStatus();
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ---------- 步骤操作 ----------

    private void showAddStepDialog() {
        if (selectedTask == null) {
            return;
        }

        final TaskActionType[] types = TaskActionType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getDisplayName();
        }

        new AlertDialog.Builder(this)
                .setTitle("选择步骤类型")
                .setItems(names, (dialog, which) -> addStepOfType(types[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void addStepOfType(TaskActionType type) {
        TaskStep step = createDefaultStep(type, selectedTask.getSteps().size());
        selectedTask.getSteps().add(step);
        TaskStore.upsertTask(this, selectedTask);
        renderSteps();
    }

    private TaskStep createDefaultStep(TaskActionType type, int order) {
        TaskStep step = new TaskStep();
        step.setActionType(type);
        step.setOrder(order);

        switch (type) {
            case TAP:
                step.setName("点击步骤");
                step.setX(500);
                step.setY(900);
                step.setTapCount(1);
                step.setClickIntervalMs(100);
                break;
            case SWIPE:
                step.setName("滑动步骤");
                step.setX(200);
                step.setY(900);
                step.setEndX(800);
                step.setEndY(900);
                step.setDurationMs(300);
                break;
            case TEXT_INPUT:
                step.setName("文本输入步骤");
                step.setTextContent("示例文本");
                step.setCharIntervalMs(50);
                break;
            default:
                break;
        }

        return step;
    }

    private void moveStep(int index, int direction) {
        List<TaskStep> steps = selectedTask.getSteps();
        int target = index + direction;
        if (target < 0 || target >= steps.size()) {
            return;
        }

        Collections.swap(steps, index, target);
        selectedTask.normalizeStepOrders();
        TaskStore.upsertTask(this, selectedTask);
        renderSteps();
    }

    private void deleteStep(TaskStep step) {
        selectedTask.getSteps().removeIf(s -> s.getId().equals(step.getId()));
        selectedTask.normalizeStepOrders();
        TaskStore.upsertTask(this, selectedTask);
        renderSteps();
    }

    private void showStepEditorDialog(TaskStep step) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 16, 32, 16);
        scroll.addView(container);

        final EditText nameInput = addEditorInput(container, "步骤名称", InputType.TYPE_CLASS_TEXT, step.getName());
        final CheckBox enabledBox = new CheckBox(this);
        enabledBox.setText("启用该步骤");
        enabledBox.setChecked(step.isEnabled());
        container.addView(enabledBox);

        final EditText xInput;
        final EditText yInput;
        if (step.getActionType() == TaskActionType.TAP
                || step.getActionType() == TaskActionType.SWIPE) {
            xInput = addEditorInput(
                    container,
                    "X 坐标（点击/滑动起点）",
                    InputType.TYPE_CLASS_NUMBER,
                    String.valueOf(step.getX()));
            yInput = addEditorInput(
                    container,
                    "Y 坐标（点击/滑动起点）",
                    InputType.TYPE_CLASS_NUMBER,
                    String.valueOf(step.getY()));
        } else {
            xInput = null;
            yInput = null;
        }

        final EditText tapCountInput;
        final EditText endXInput;
        final EditText endYInput;
        final EditText durationInput;
        final EditText textInput;
        final EditText charIntervalInput;
        final EditText clickIntervalInput;
        final EditText pressDurationInput;
        final CheckBox autoFocusBox;

        if (step.getActionType() == TaskActionType.TAP) {
            tapCountInput = addEditorInput(container, "点击次数", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getTapCount()));
            clickIntervalInput = addEditorInput(container, "连点间隔，毫秒", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getClickIntervalMs()));
            pressDurationInput = addEditorInput(container, "按压时长，毫秒（0=默认）", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getPressDurationMs()));
            endXInput = null;
            endYInput = null;
            durationInput = null;
            textInput = null;
            charIntervalInput = null;
            autoFocusBox = null;
            addPickButton(container, step, TaskActionType.TAP);
        } else if (step.getActionType() == TaskActionType.SWIPE) {
            tapCountInput = null;
            clickIntervalInput = null;
            pressDurationInput = null;
            endXInput = addEditorInput(container, "终点 X 坐标", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getEndX()));
            endYInput = addEditorInput(container, "终点 Y 坐标", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getEndY()));
            durationInput = addEditorInput(container, "滑动持续时间，毫秒", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getDurationMs()));
            textInput = null;
            charIntervalInput = null;
            autoFocusBox = null;
            addPickButton(container, step, TaskActionType.SWIPE);
        } else {
            tapCountInput = null;
            clickIntervalInput = null;
            pressDurationInput = null;
            endXInput = null;
            endYInput = null;
            durationInput = null;
            textInput = addEditorInput(container, "文本内容", InputType.TYPE_CLASS_TEXT, step.getTextContent());
            charIntervalInput = addEditorInput(container, "逐字间隔，毫秒（0 表示整段写入）", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getCharIntervalMs()));
            autoFocusBox = new CheckBox(this);
            autoFocusBox.setText("输入前自动点击目标位置");
            autoFocusBox.setChecked(step.isAutoFocusBeforeInput());
            container.addView(autoFocusBox);
        }

        final EditText beforeDelayInput = addEditorInput(container, "步骤前等待，毫秒", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getBeforeDelayMs()));

        new AlertDialog.Builder(this)
                .setTitle("编辑步骤：" + step.getActionType().getDisplayName())
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        step.setName(nameInput.getText().toString().trim());
                        step.setEnabled(enabledBox.isChecked());
                        if (xInput != null) {
                            step.setX(parseInt(xInput, "X 坐标"));
                        }
                        if (yInput != null) {
                            step.setY(parseInt(yInput, "Y 坐标"));
                        }
                        step.setBeforeDelayMs(parseInt(beforeDelayInput, "步骤前等待"));

                        if (step.getActionType() == TaskActionType.TAP) {
                            if (tapCountInput != null) {
                                step.setTapCount(parseInt(tapCountInput, "点击次数"));
                            }
                            if (clickIntervalInput != null) {
                                step.setClickIntervalMs(parseInt(clickIntervalInput, "连点间隔"));
                            }
                            if (pressDurationInput != null) {
                                step.setPressDurationMs(parseInt(pressDurationInput, "按压时长"));
                            }
                        } else if (step.getActionType() == TaskActionType.SWIPE) {
                            if (endXInput != null) {
                                step.setEndX(parseInt(endXInput, "终点 X 坐标"));
                            }
                            if (endYInput != null) {
                                step.setEndY(parseInt(endYInput, "终点 Y 坐标"));
                            }
                            if (durationInput != null) {
                                step.setDurationMs(parseInt(durationInput, "滑动持续时间"));
                            }
                        } else if (step.getActionType() == TaskActionType.TEXT_INPUT) {
                            if (textInput != null) {
                                step.setTextContent(textInput.getText().toString());
                            }
                            if (charIntervalInput != null) {
                                step.setCharIntervalMs(parseInt(charIntervalInput, "逐字间隔"));
                            }
                            if (autoFocusBox != null) {
                                step.setAutoFocusBeforeInput(autoFocusBox.isChecked());
                            }
                        }

                        step.validateForSave();
                        TaskStore.upsertTask(this, selectedTask);
                        renderSteps();
                    } catch (IllegalArgumentException ex) {
                        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addPickButton(LinearLayout container, TaskStep step, TaskActionType mode) {
        Button pickButton = new Button(this);
        pickButton.setText(mode == TaskActionType.SWIPE ? "点选滑动起终点" : "点选点击位置");
        pickButton.setOnClickListener(view -> startCoordinatePickForStep(step, mode));
        container.addView(pickButton);
    }

    private EditText addEditorInput(LinearLayout container, String hint, int inputType, String initial) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        if (initial != null) {
            input.setText(initial);
        }
        container.addView(input);
        return input;
    }

    private void startCoordinatePickForStep(TaskStep step, TaskActionType mode) {
        if (selectedTask == null) {
            return;
        }

        // 先持久化当前任务，确保步骤已写入存储，便于取点回写。
        try {
            selectedTask.setName(taskNameInput.getText().toString().trim());
            TaskStore.upsertTask(this, selectedTask);
        } catch (IllegalArgumentException ignored) {
            // 名称非法时仍尝试保存步骤坐标。
        }

        JSONObject target = new JSONObject();
        try {
            target.put("taskId", selectedTask.getId());
            target.put("stepId", step.getId());
            target.put("mode", mode.name());
            target.put("delayMs", 5000);
            target.put("requestedAt", System.currentTimeMillis());
        } catch (JSONException e) {
            Toast.makeText(this, "取点目标创建失败", Toast.LENGTH_LONG).show();
            return;
        }

        if (!ClickAssistantAccessibilityService.isActive()) {
            Toast.makeText(this, "请先开启辅助功能服务，再点选位置", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        TaskStore.savePickTarget(this, target);
        if (!ClickAssistantAccessibilityService.startCoordinatePick()) {
            TaskStore.saveLastStatus(this, "取点失败：辅助功能服务未连接");
            refreshStatus();
            Toast.makeText(this, "辅助功能服务尚未连接，请返回设置确认已启用", Toast.LENGTH_LONG).show();
            return;
        }

        refreshStatus();
        Toast.makeText(this, "5 秒内切到目标界面，出现取点层后点击位置", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    // ---------- 执行控制 ----------

    private void startTask() {
        if (!isAccessibilityEnabled()) {
            TaskStore.saveLastStatus(this, "执行失败：辅助功能服务未启用");
            refreshStatus();
            Toast.makeText(this, "请先开启辅助功能服务", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        if (!saveTaskFromInputs(false) || selectedTask == null) {
            return;
        }

        try {
            selectedTask.validateForExecution();
        } catch (IllegalArgumentException ex) {
            TaskStore.saveLastStatus(this, "执行失败：" + ex.getMessage());
            refreshStatus();
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        showExecutionConfirmDialog(selectedTask);
    }

    private void showExecutionConfirmDialog(ClickTask task) {
        StringBuilder summary = new StringBuilder();
        summary.append("任务：").append(task.getName()).append("\n");
        summary.append("重复次数：").append(task.getRepeatCount()).append("\n");
        summary.append("开始延迟：").append(task.getStartDelayMs()).append(" 毫秒\n");
        summary.append("步骤：\n");
        int index = 0;
        for (TaskStep step : task.getSteps()) {
            if (!step.isEnabled()) {
                continue;
            }
            index++;
            summary.append(String.format(Locale.ROOT, "  %d. %s - %s\n", index, step.getActionType().getDisplayName(), step.getSummary()));
        }

        new AlertDialog.Builder(this)
                .setTitle("执行前确认")
                .setMessage(summary.toString())
                .setPositiveButton("确认执行", (dialog, which) -> {
                    if (!ClickAssistantAccessibilityService.startTask(task)) {
                        TaskStore.saveLastStatus(this, "执行失败：辅助功能服务未连接或已有任务正在运行");
                        refreshStatus();
                        Toast.makeText(this, "启动失败，请确认辅助功能服务已连接且没有其他任务运行", Toast.LENGTH_LONG).show();
                        return;
                    }
                    refreshStatus();
                    Toast.makeText(this, "已开始执行，可切到目标界面", Toast.LENGTH_SHORT).show();
                    moveTaskToBack(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pauseTask() {
        ClickAssistantAccessibilityService.requestPause();
        TaskStore.saveLastStatus(this, ExecutionState.PAUSED.getDisplayName());
        refreshStatus();
    }

    private void resumeTask() {
        ClickAssistantAccessibilityService.requestResume();
        refreshStatus();
    }

    private void stopTask() {
        ClickAssistantAccessibilityService.requestStop();
        TaskStore.saveLastStatus(this, "已发送停止请求");
        refreshStatus();
    }

    // ---------- 辅助方法 ----------

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
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
        String normalized = enabledServices.toLowerCase(Locale.ROOT);
        return normalized.contains(expectedComponent.flattenToString().toLowerCase(Locale.ROOT))
                || normalized.contains(expectedComponent.flattenToShortString().toLowerCase(Locale.ROOT));
    }
}
