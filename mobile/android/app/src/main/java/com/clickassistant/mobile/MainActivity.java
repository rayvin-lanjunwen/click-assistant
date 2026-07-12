package com.clickassistant.mobile;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/// <summary>
/// 主界面，采用底部导航 + 跨端一致的液态玻璃 UI。
/// 包含权限引导页、首页、新建任务页、任务库、执行日志、个人中心和任务编辑器。
/// </summary>
public final class MainActivity extends Activity {
    private enum Page {
        ONBOARDING,   // 权限引导页
        HOME,         // 首页
        LIBRARY,      // 任务库
        LOGS,         // 执行日志
        PROFILE,      // 个人中心
        EDITOR        // 任务编辑
    }

    // 与 WPF 端同语义的液态玻璃配色
    private static final int BG = Color.parseColor("#EEF4F7");
    private static final int CARD_BG = Color.parseColor("#D9FFFFFF");
    private static final int PRIMARY = Color.parseColor("#3978F6");
    private static final int PRIMARY_LIGHT = Color.parseColor("#DDEAFF");
    private static final int TEXT_PRIMARY = Color.parseColor("#17233A");
    private static final int TEXT_SECONDARY = Color.parseColor("#526176");
    private static final int BORDER = Color.parseColor("#B8FFFFFF");
    private static final int SUCCESS = Color.parseColor("#169B72");
    private static final int SUCCESS_LIGHT = Color.parseColor("#D8F4E8");
    private static final int ORANGE = Color.parseColor("#E98732");
    private static final int ORANGE_LIGHT = Color.parseColor("#FFF0DB");
    private static final int PURPLE = Color.parseColor("#7067D8");
    private static final int PURPLE_LIGHT = Color.parseColor("#E9E7FA");
    private static final int CYAN = Color.parseColor("#168DA4");
    private static final int CYAN_LIGHT = Color.parseColor("#DDF3F6");
    private static final int RED = Color.parseColor("#D84D5F");
    private static final int RED_LIGHT = Color.parseColor("#FCE4E8");
    private static final int GRAY_BG = Color.parseColor("#A6FFFFFF");

    // 液态玻璃三阶阴影深度层级（模拟 Apple Light / Light Medium / Light Heavy）
    private static final int SHADOW_L1 = 3;   // 表层：小控件、Switch、Badge
    private static final int SHADOW_L2 = 6;   // 中景：普通卡片（当前默认）
    private static final int SHADOW_L3 = 14;  // 深层：导航栏、弹窗、FAB

    private Page currentPage = Page.ONBOARDING;
    private String currentFilter = "全部";

    private final EnumMap<Page, View> pageViewCache = new EnumMap<>(Page.class);
    private FrameLayout rootView;
    private View currentContentView;

    private LinearLayout bottomNav;
    private TextView homeNavItem;
    private TextView libraryNavItem;
    private TextView logsNavItem;
    private TextView profileNavItem;

    private EditText taskNameInput;
    private EditText taskDescInput;
    private SwitchCompat taskEnabledBox;
    private EditText repeatCountInput;
    private EditText startDelayInput;
    private LinearLayout stepsContainer;
    private LinearLayout logContainer;
    private int logDisplayCount = 30;
    private List<ExecutionLogEntry> allLogEntries = new ArrayList<>();
    private EditText librarySearchInput;
    private LinearLayout libraryTaskList;
    private LinearLayout filterContainer;

    private TextView accessibilityStatusText;
    private TextView lastStatusText;
    private SwitchCompat accessibilitySwitch;
    // 抑制 Switch 程序化 setChecked 时触发跳转
    private boolean suppressAccessibilitySwitch;

    private List<ClickTask> tasks = new ArrayList<>();
    private ClickTask selectedTask;

    // 编辑器脏标记：用于退出编辑器时判断是否有未保存修改
    private boolean editorDirty = false;
    // 填充字段时抑制脏标记置位，避免 setText 触发 TextWatcher 误判为已修改
    private boolean suppressDirty = false;
    // 编辑器来源页面，退出时返回
    private Page editorSourcePage = Page.LIBRARY;

    // 执行控制按钮（成员变量，用于根据执行状态联动启用/禁用）
    private Button editorStartButton;
    private Button editorPauseButton;
    private Button editorResumeButton;
    private Button editorStopButton;

    // 引导页持久标记：用户点击"暂不设置"或辅助功能已开启后不再展示引导页
    private static final String PREFS_NAME = "click_assistant_app";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 若引导已完成或辅助功能已开启，直接进入首页
        if (isOnboardingCompleted() || isAccessibilityEnabled()) {
            showPage(Page.HOME);
        } else {
            showPage(Page.ONBOARDING);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 用户从系统设置开启辅助功能后返回 App 时，自动从引导页跳转首页
        if (currentPage == Page.ONBOARDING && isAccessibilityEnabled()) {
            setOnboardingCompleted();
            showPage(Page.HOME);
            return;
        }
        // 取点流程返回后自动恢复编辑器（任务和步骤已通过 Service 更新）
        String activeTaskId = TaskStore.loadActiveTaskId(this);
        if (activeTaskId != null && !activeTaskId.isEmpty() && currentPage != Page.EDITOR) {
            loadTasks();
            for (ClickTask t : tasks) {
                if (activeTaskId.equals(t.getId())) {
                    selectedTask = t;
                    editorSourcePage = Page.HOME;
                    showPage(Page.EDITOR);
                    // 取点返回后步骤列表错位弹入
                    findViewById(android.R.id.content).post(() -> {
                        if (stepsContainer != null)
                            animateStaggeredPopIn(stepsContainer, 40);
                    });
                    return;
                }
            }
        }
        loadTasks();
        refreshPageContent();
    }

    @Override
    public void onBackPressed() {
        if (currentPage == Page.EDITOR) {
            exitEditor();
        } else if (currentPage == Page.LOGS || currentPage == Page.LIBRARY || currentPage == Page.PROFILE) {
            showPage(Page.HOME);
        } else if (currentPage != Page.HOME && currentPage != Page.ONBOARDING) {
            showPage(Page.HOME);
        } else {
            super.onBackPressed();
        }
    }

    // ---------- 页面切换与构建 ----------

    private void showPage(Page page) {
        currentPage = page;
        // 如果已开启辅助功能，跳过引导页并持久标记
        if (page == Page.ONBOARDING && isAccessibilityEnabled()) {
            currentPage = Page.HOME;
            setOnboardingCompleted();
        }

        // 首次调用时创建持久根容器和底部导航
        if (rootView == null) {
            rootView = new FrameLayout(this);
            rootView.setBackground(pageBackgroundDrawable());
            bottomNav = buildBottomNav();
            FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(64),
                    Gravity.BOTTOM);
            rootView.addView(bottomNav, navParams);
            setContentView(rootView);
        }

        // 隐藏当前页面内容
        if (currentContentView != null) {
            currentContentView.setVisibility(View.GONE);
        }

        // 获取或构建目标页面视图，添加到底部导航之下（z-order 最底层）
        View targetView = pageViewCache.get(currentPage);
        if (targetView == null) {
            targetView = buildPageContent(currentPage);
            pageViewCache.put(currentPage, targetView);
            rootView.addView(targetView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        targetView.setVisibility(View.VISIBLE);
        currentContentView = targetView;

        // 管理底部导航可见性
        boolean showBottomNav = currentPage != Page.EDITOR
                && currentPage != Page.ONBOARDING;
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);

        // 任务库页面内部已预留底部空间，其他页面为底部导航腾出空间
        if (showBottomNav && currentPage != Page.LIBRARY) {
            targetView.setPadding(0, 0, 0, dp(64));
        } else {
            targetView.setPadding(0, 0, 0, 0);
        }

        loadTasks();
        refreshPageContent();

        // 页面切换动画：从下方 24dp 弹入
        animateSlideUp(targetView);
    }

    private void refreshPageContent() {
        if (currentPage == Page.HOME || currentPage == Page.ONBOARDING) {
            refreshHomeStatus();
        }
        if (currentPage == Page.LIBRARY) {
            refreshLibrary();
        }
        if (currentPage == Page.LOGS) {
            renderLog();
        }
        if (currentPage == Page.EDITOR) {
            updateExecutionButtonStates();
        }
        updateBottomNav();
    }

    /// <summary>
    /// 根据执行状态联动显示编辑器中的执行控制按钮（未运行时仅显示"开始"）。
    /// </summary>
    private void updateExecutionButtonStates() {
        boolean svcRunning = ClickAssistantAccessibilityService.isRunning();
        boolean svcPaused = ClickAssistantAccessibilityService.isPaused();

        if (editorStartButton != null) {
            editorStartButton.setVisibility(svcRunning ? View.GONE : View.VISIBLE);
        }
        if (editorPauseButton != null) {
            editorPauseButton.setVisibility(svcRunning && !svcPaused ? View.VISIBLE : View.GONE);
        }
        if (editorResumeButton != null) {
            editorResumeButton.setVisibility(svcRunning && svcPaused ? View.VISIBLE : View.GONE);
        }
        if (editorStopButton != null) {
            editorStopButton.setVisibility(svcRunning ? View.VISIBLE : View.GONE);
        }
    }

    private View buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(pageBackgroundDrawable());

        View content = buildPageContent(currentPage);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 编辑器/新建任务页/引导页不显示底部导航
        if (currentPage != Page.EDITOR && currentPage != Page.ONBOARDING) {
            bottomNav = buildBottomNav();
            FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(64),
                    Gravity.BOTTOM);
            root.addView(bottomNav, navParams);
            // 任务库页面内部已预留底部空间，其他页面为底部导航腾出空间
            if (currentPage != Page.LIBRARY) {
                content.setPadding(0, 0, 0, dp(64));
            }
        }

        return root;
    }

    private View buildPageContent(Page page) {
        switch (page) {
            case ONBOARDING:
                return buildOnboardingPage();
            case HOME:
                return buildHomePage();
            case LIBRARY:
                return buildLibraryPage();
            case LOGS:
                return buildExecutionLogPage();
            case PROFILE:
                return buildProfilePage();
            case EDITOR:
            default:
                return buildTaskEditorPage();
        }
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackground(glassDrawable(dp(20)));
        nav.setElevation(dp(SHADOW_L3));
        nav.setPadding(dp(10), dp(6), dp(10), dp(8));

        homeNavItem = createNavItem("🏠", "首页", Page.HOME);
        libraryNavItem = createNavItem("📂", "任务库", Page.LIBRARY);
        logsNavItem = createNavItem("📋", "日志", Page.LOGS);
        profileNavItem = createNavItem("👤", "我的", Page.PROFILE);

        nav.addView(homeNavItem, weightParams());
        nav.addView(libraryNavItem, weightParams());
        nav.addView(logsNavItem, weightParams());
        nav.addView(profileNavItem, weightParams());

        updateBottomNav();
        return nav;
    }

    private TextView createNavItem(String icon, String label, Page targetPage) {
        TextView item = new TextView(this);
        item.setText(icon + "\n" + label);
        item.setTextSize(11);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(6), 0, dp(6));
        item.setMinHeight(dp(50));
        item.setOnClickListener(v -> showPage(targetPage));
        return item;
    }

    private void updateBottomNav() {
        if (homeNavItem == null || libraryNavItem == null
                || logsNavItem == null || profileNavItem == null) {
            return;
        }
        homeNavItem.setTextColor(currentPage == Page.HOME ? PRIMARY : TEXT_SECONDARY);
        libraryNavItem.setTextColor(currentPage == Page.LIBRARY ? PRIMARY : TEXT_SECONDARY);
        logsNavItem.setTextColor(currentPage == Page.LOGS ? PRIMARY : TEXT_SECONDARY);
        profileNavItem.setTextColor(currentPage == Page.PROFILE ? PRIMARY : TEXT_SECONDARY);
        homeNavItem.setBackground(currentPage == Page.HOME
                ? roundedDrawable(Color.parseColor("#AFFFFFFF"), dp(12)) : null);
        libraryNavItem.setBackground(currentPage == Page.LIBRARY
                ? roundedDrawable(Color.parseColor("#AFFFFFFF"), dp(12)) : null);
        logsNavItem.setBackground(currentPage == Page.LOGS
                ? roundedDrawable(Color.parseColor("#AFFFFFFF"), dp(12)) : null);
        profileNavItem.setBackground(currentPage == Page.PROFILE
                ? roundedDrawable(Color.parseColor("#AFFFFFFF"), dp(12)) : null);

        // 导航栏选中项轻弹反馈
        View activeItem = null;
        if (currentPage == Page.HOME) activeItem = homeNavItem;
        else if (currentPage == Page.LIBRARY) activeItem = libraryNavItem;
        else if (currentPage == Page.LOGS) activeItem = logsNavItem;
        else if (currentPage == Page.PROFILE) activeItem = profileNavItem;

        if (activeItem != null) {
            final View item = activeItem;
            item.animate()
                    .scaleX(1.05f).scaleY(1.05f)
                    .setDuration(80)
                    .withEndAction(() -> item.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(120)
                            .start())
                    .start();
        }
    }

    // ---------- 权限引导页 ----------

    private ScrollView buildOnboardingPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(pageBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(36), dp(28), dp(36));
        root.setGravity(Gravity.CENTER);

        // 顶部时间/状态占位
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(spacer);

        TextView title = new TextView(this);
        title.setText("开启辅助功能");
        title.setTextSize(24);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, blockParams());

        TextView subtitle = new TextView(this);
        subtitle.setText("为保障脚本的点击与输入功能正常运行，\n请在系统设置中开启【辅助功能】权限。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0, 1.4f);
        root.addView(subtitle, blockParams());

        // 盾牌图标占位
        FrameLayout shieldFrame = new FrameLayout(this);
        shieldFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(160), dp(160)));
        shieldFrame.setBackground(circleDrawable(PRIMARY_LIGHT, 0));
        TextView shieldIcon = new TextView(this);
        shieldIcon.setText("🛡️");
        shieldIcon.setTextSize(72);
        FrameLayout.LayoutParams shieldIconParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shieldIconParams.gravity = Gravity.CENTER;
        shieldFrame.addView(shieldIcon, shieldIconParams);
        LinearLayout.LayoutParams shieldParams = blockParams();
        shieldParams.setMargins(0, dp(32), 0, dp(32));
        shieldParams.gravity = Gravity.CENTER;
        root.addView(shieldFrame, shieldParams);

        // 安全特性列表
        addFeatureRow(root, "🛡️", "安全可靠", "仅在您授权后运行，不收集隐私数据");
        addFeatureRow(root, "🎮", "完全可控", "所有操作由您创建与启动，可随时停止");
        addFeatureRow(root, "👁️", "透明执行", "执行过程可查看日志，清晰可追溯");

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button settingsButton = new Button(this);
        settingsButton.setText("前往系统设置");
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setTextSize(16);
        settingsButton.setAllCaps(false);
        settingsButton.setBackground(roundedDrawable(PRIMARY, dp(12)));
        settingsButton.setPadding(dp(24), dp(16), dp(24), dp(16));
        settingsButton.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(settingsButton, blockParams());

        TextView skipText = new TextView(this);
        skipText.setText("暂不设置");
        skipText.setTextSize(14);
        skipText.setTextColor(TEXT_SECONDARY);
        skipText.setGravity(Gravity.CENTER);
        skipText.setPadding(0, dp(16), 0, dp(16));
        skipText.setOnClickListener(v -> {
            setOnboardingCompleted();
            showPage(Page.HOME);
        });
        root.addView(skipText, blockParams());

        scroll.addView(root);
        return scroll;
    }

    private void addFeatureRow(LinearLayout root, String icon, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(roundedDrawable(CARD_BG, dp(12)));
        LinearLayout.LayoutParams rowParams = blockParams();
        rowParams.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(rowParams);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        row.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(16), 0, 0, 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTypeface(null, Typeface.BOLD);
        textCol.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(13);
        descView.setTextColor(TEXT_SECONDARY);
        descView.setLineSpacing(0, 1.3f);
        textCol.addView(descView);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);
    }

    // ---------- 首页 ----------

    private ScrollView buildHomePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(pageBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        // 顶部标题区
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(16), 0, dp(12));
        root.addView(header);

        TextView title = new TextView(this);
        title.setText("Click Assistant");
        title.setTextSize(28);
        title.setTextColor(PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        // 品牌渐变填色 + 微弱发光阴影
        title.getPaint().setShader(new LinearGradient(0, 0, dp(320), 0,
                new int[]{Color.parseColor("#3978F6"), Color.parseColor("#7067D8")},
                null, Shader.TileMode.CLAMP));
        title.setShadowLayer(dp(4), 0, dp(2), Color.parseColor("#203978F6"));
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("点击与输入助手");
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_SECONDARY);
        header.addView(subtitle);

        // 辅助功能状态卡片（带 Switch 开关）
        LinearLayout statusCard = createCard(dp(16));
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        iconFrame.setBackground(circleDrawable(isAccessibilityEnabled() ? SUCCESS_LIGHT : RED_LIGHT, 0));
        TextView iconView = new TextView(this);
        iconView.setText(isAccessibilityEnabled() ? "✅" : "⚠️");
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconFrameParams1 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconFrameParams1.gravity = Gravity.CENTER;
        iconFrame.addView(iconView, iconFrameParams1);
        statusCard.addView(iconFrame);

        LinearLayout statusTextCol = new LinearLayout(this);
        statusTextCol.setOrientation(LinearLayout.VERTICAL);
        statusTextCol.setPadding(dp(16), 0, 0, 0);
        statusTextCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView statusTitle = new TextView(this);
        statusTitle.setText("辅助功能");
        statusTitle.setTextSize(14);
        statusTitle.setTextColor(TEXT_SECONDARY);
        statusTextCol.addView(statusTitle);

        accessibilityStatusText = new TextView(this);
        accessibilityStatusText.setText(isAccessibilityEnabled() ? "已开启" : "未开启");
        accessibilityStatusText.setTextSize(18);
        accessibilityStatusText.setTextColor(isAccessibilityEnabled() ? SUCCESS : RED);
        accessibilityStatusText.setTypeface(null, Typeface.BOLD);
        statusTextCol.addView(accessibilityStatusText);

        statusCard.addView(statusTextCol);

        accessibilitySwitch = new SwitchCompat(this);
        accessibilitySwitch.setChecked(isAccessibilityEnabled());
        accessibilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAccessibilitySwitch) return;
            // 拨向 ON 且已开启 → 不跳；拨向 OFF 或尚未开启 → 跳设置
            if (!isChecked || !isAccessibilityEnabled()) {
                openAccessibilitySettings();
            }
        });
        statusCard.addView(accessibilitySwitch);

        root.addView(statusCard, blockParams());

        // 2×2 网格快捷入口
        TextView quickTitle = new TextView(this);
        quickTitle.setText("快捷入口");
        quickTitle.setTextSize(18);
        quickTitle.setTextColor(TEXT_PRIMARY);
        quickTitle.setTypeface(null, Typeface.BOLD);
        quickTitle.setPadding(0, dp(24), 0, dp(12));
        root.addView(quickTitle);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid);

        addGridRow(grid,
                new GridItem("⚡", "最近任务", "快速执行上次\n执行过的任务", PRIMARY, PRIMARY_LIGHT, v -> executeRecentTask()),
                new GridItem("📂", "任务库", "管理与运行\n自动化任务", PURPLE, PURPLE_LIGHT, v -> showPage(Page.LIBRARY)));
        addGridRow(grid,
                new GridItem("📋", "执行日志", "查看任务\n执行记录", ORANGE, ORANGE_LIGHT, v -> showPage(Page.LOGS)),
                new GridItem("➕", "新建任务", "创建新的\n自动化流程", CYAN, CYAN_LIGHT, v -> showNewTaskDialog()));

        // 最近状态
        TextView recentTitle = new TextView(this);
        recentTitle.setText("最近状态");
        recentTitle.setTextSize(18);
        recentTitle.setTextColor(TEXT_PRIMARY);
        recentTitle.setTypeface(null, Typeface.BOLD);
        recentTitle.setPadding(0, dp(24), 0, dp(12));
        root.addView(recentTitle);

        LinearLayout recentCard = createCard(dp(12));
        recentCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        recentCard.setOnClickListener(v -> {
            animatePressFeedback(recentCard);
            showPage(Page.LOGS);
        });
        lastStatusText = new TextView(this);
        lastStatusText.setText("暂无执行记录");
        lastStatusText.setTextSize(14);
        lastStatusText.setTextColor(TEXT_SECONDARY);
        lastStatusText.setLineSpacing(0, 1.4f);
        recentCard.addView(lastStatusText);
        root.addView(recentCard, blockParams());

        scroll.addView(root);

        // 2×2 网格卡片错位弹入
        root.post(() -> animateStaggeredPopIn(grid, 60));

        return scroll;
    }

    private static class GridItem {
        final String icon;
        final String title;
        final String desc;
        final int color;
        final int bgColor;
        final View.OnClickListener listener;

        GridItem(String icon, String title, String desc, int color, int bgColor, View.OnClickListener listener) {
            this.icon = icon;
            this.title = title;
            this.desc = desc;
            this.color = color;
            this.bgColor = bgColor;
            this.listener = listener;
        }
    }

    private void addGridRow(LinearLayout root, GridItem left, GridItem right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(rowParams);

        row.addView(buildGridCard(left), new LinearLayout.LayoutParams(0, dp(120), 1));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(spacer);
        row.addView(buildGridCard(right), new LinearLayout.LayoutParams(0, dp(120), 1));

        root.addView(row);
    }

    private View buildGridCard(GridItem item) {
        LinearLayout card = createCard(dp(16));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setGravity(Gravity.CENTER);
        card.setOnClickListener(item.listener);
        // 点击按压反馈
        card.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                animatePressFeedback(v);
            }
            return false;
        });

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        iconFrame.setBackground(circleDrawable(item.bgColor, 0));
        TextView iconView = new TextView(this);
        iconView.setText(item.icon);
        iconView.setTextSize(22);
        iconView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconFp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconFp.gravity = Gravity.CENTER;
        iconFrame.addView(iconView, iconFp);
        card.addView(iconFrame);

        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setTextSize(15);
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(10), 0, dp(4));
        card.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText(item.desc);
        descView.setTextSize(12);
        descView.setTextColor(TEXT_SECONDARY);
        descView.setGravity(Gravity.CENTER);
        descView.setLineSpacing(0, 1.3f);
        card.addView(descView);

        return card;
    }

    /// <summary>
    /// 执行最近一次执行过的任务，若无记录则跳任务库。
    /// </summary>
    private void executeRecentTask() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启辅助功能服务", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }
        List<ExecutionLogEntry> logs = ExecutionLogStore.loadLog(this);
        String lastTaskId = null;
        for (ExecutionLogEntry e : logs) {
            if (e.getTaskId() != null && !e.getTaskId().isEmpty()) {
                lastTaskId = e.getTaskId();
                break;
            }
        }
        if (lastTaskId == null) {
            Toast.makeText(this, "暂无执行记录，请先创建任务", Toast.LENGTH_SHORT).show();
            return;
        }
        ClickTask target = null;
        for (ClickTask t : tasks) {
            if (lastTaskId.equals(t.getId())) { target = t; break; }
        }
        if (target == null) {
            Toast.makeText(this, "最近任务已删除，请选择其他任务", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedTask = target;
        startTask();
    }

    private void refreshStatus() {
        boolean enabled = isAccessibilityEnabled();
        if (accessibilityStatusText != null) {
            accessibilityStatusText.setText(enabled ? "已开启" : "未开启");
            accessibilityStatusText.setTextColor(enabled ? SUCCESS : RED);
        }
        if (lastStatusText != null) {
            lastStatusText.setText(TaskStore.loadLastStatus(this));
        }
        // 静默刷新 Switch 状态（抑制跳转）
        if (accessibilitySwitch != null) {
            suppressAccessibilitySwitch = true;
            accessibilitySwitch.setChecked(enabled);
            suppressAccessibilitySwitch = false;
        }
    }

    private void refreshHomeStatus() {
        refreshStatus();
    }

    // ---------- 新建任务弹窗 ----------

    /// <summary>
    /// 弹出"选择操作模式"对话框，选择后直接创建单步骤任务并进入编辑器。
    /// </summary>
    private void showNewTaskDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(8), dp(16), dp(8));

        String[][] modes = {
                {"🖱️ 单击", "在指定位置点击一次"},
                {"👆 双击", "在指定位置快速连击两次"},
                {"⏱️ 长按", "在指定位置长按"},
                {"↔️ 滑动", "从起点滑动到终点"}
        };

        for (int i = 0; i < modes.length; i++) {
            final int modeIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(14), dp(12), dp(14));
            row.setGravity(Gravity.CENTER_VERTICAL);
            int[] colors = {PRIMARY, PURPLE, ORANGE, CYAN};
            int[] lightColors = {PRIMARY_LIGHT, PURPLE_LIGHT, ORANGE_LIGHT, CYAN_LIGHT};
            row.setBackground(roundedDrawable(lightColors[i], dp(12)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(12));
            row.setLayoutParams(rowParams);

            TextView iconView = new TextView(this);
            iconView.setText(modes[i][0]);
            iconView.setTextSize(18);
            iconView.setTextColor(colors[i]);
            iconView.setTypeface(null, Typeface.BOLD);
            row.addView(iconView);

            TextView descView = new TextView(this);
            descView.setText(modes[i][1]);
            descView.setTextSize(13);
            descView.setTextColor(TEXT_SECONDARY);
            descView.setPadding(dp(12), 0, 0, 0);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            row.addView(descView, descParams);

            row.setOnClickListener(v -> {
                createTaskFromMode(modeIndex);
            });

            container.addView(row);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择操作模式")
                .setView(container)
                .setNegativeButton("取消", null)
                .show();
        animateDialogEntrance(dialog);
    }

    /// <summary>
    /// 根据操作模式创建单步骤任务：0=单击 1=双击 2=长按 3=滑动
    /// </summary>
    private void createTaskFromMode(int mode) {
        ClickTask task = new ClickTask();
        task.setSteps(new ArrayList<>());

        switch (mode) {
            case 0: // 单击
                task.setName("新建点击任务");
                TaskStep tap = createDefaultStep(TaskActionType.TAP, 0);
                tap.setTapCount(1);
                tap.setClickIntervalMs(0);
                task.getSteps().add(tap);
                break;
            case 1: // 双击
                task.setName("新建双击任务");
                TaskStep dbl = createDefaultStep(TaskActionType.TAP, 0);
                dbl.setTapCount(2);
                dbl.setClickIntervalMs(100);
                task.getSteps().add(dbl);
                break;
            case 2: // 长按 - 独立类型
                task.setName("新建长按任务");
                TaskStep lp = createDefaultStep(TaskActionType.LONG_PRESS, 0);
                lp.setTapCount(1);
                lp.setPressDurationMs(800);
                task.getSteps().add(lp);
                break;
            case 3: // 滑动
                task.setName("新建滑动任务");
                TaskStep sw = createDefaultStep(TaskActionType.SWIPE, 0);
                sw.setEndX(500);
                sw.setEndY(1200);
                sw.setDurationMs(300);
                task.getSteps().add(sw);
                break;
        }

        TaskStore.upsertTask(this, task);
        selectedTask = task;
        editorSourcePage = currentPage;
        showPage(Page.EDITOR);
        Toast.makeText(this, "已创建任务，请设置坐标和参数", Toast.LENGTH_SHORT).show();
    }

    // ---------- 任务库页 ----------

    private View buildLibraryPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(pageBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(84)); // 预留底部导航和 FAB 空间

        // 标题
        TextView title = new TextView(this);
        title.setText("任务库");
        title.setTextSize(28);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(16));
        root.addView(title);

        // 搜索框
        LinearLayout searchCard = createCard(dp(12));
        searchCard.setOrientation(LinearLayout.HORIZONTAL);
        searchCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchCard.setGravity(Gravity.CENTER_VERTICAL);

        TextView searchIcon = new TextView(this);
        searchIcon.setText("🔍");
        searchIcon.setTextSize(18);
        searchCard.addView(searchIcon);

        librarySearchInput = new EditText(this);
        librarySearchInput.setHint("搜索任务名称");
        librarySearchInput.setTextSize(14);
        librarySearchInput.setTextColor(TEXT_PRIMARY);
        librarySearchInput.setHintTextColor(TEXT_SECONDARY);
        librarySearchInput.setBackground(null);
        librarySearchInput.setPadding(dp(12), 0, 0, 0);
        librarySearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                refreshLibrary();
            }
        });
        searchCard.addView(librarySearchInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(searchCard, blockParams());

        // 筛选标签
        filterContainer = new LinearLayout(this);
        filterContainer.setOrientation(LinearLayout.HORIZONTAL);
        filterContainer.setPadding(0, dp(16), 0, dp(8));
        root.addView(filterContainer);

        refreshFilterTabs();

        // 任务列表
        libraryTaskList = new LinearLayout(this);
        libraryTaskList.setOrientation(LinearLayout.VERTICAL);
        root.addView(libraryTaskList);

        scroll.addView(root);

        // 悬浮新建按钮
        FrameLayout fab = new FrameLayout(this);
        fab.setLayoutParams(new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END));
        ((FrameLayout.MarginLayoutParams) fab.getLayoutParams()).setMargins(0, 0, dp(20), dp(84));
        fab.setBackground(circleDrawable(PRIMARY, 0));
        fab.setElevation(dp(SHADOW_L3));
        fab.setOnClickListener(v -> showNewTaskDialog());

        TextView fabIcon = new TextView(this);
        fabIcon.setText("+");
        fabIcon.setTextSize(32);
        fabIcon.setTextColor(Color.WHITE);
        fabIcon.setGravity(Gravity.CENTER);
        fab.addView(fabIcon);

        // 需要在 ScrollView 外包裹 FrameLayout 才能放置 FAB
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(scroll);
        wrapper.addView(fab);
        return wrapper;
    }

    private void refreshFilterTabs() {
        if (filterContainer == null) return;
        filterContainer.removeAllViews();
        String[] filters = {"全部", "进行中", "已完成", "已停止"};
        for (String filter : filters) {
            TextView tab = new TextView(this);
            tab.setText(filter);
            tab.setTextSize(14);
            tab.setPadding(dp(16), dp(8), dp(16), dp(8));
            boolean active = filter.equals(currentFilter);
            tab.setTextColor(active ? Color.WHITE : TEXT_SECONDARY);
            // 玻璃胶囊标签：选中时品牌色填充，未选中时半透白玻璃
            GradientDrawable capsuleBg = new GradientDrawable();
            capsuleBg.setShape(GradientDrawable.RECTANGLE);
            capsuleBg.setCornerRadius(dp(16));
            if (active) {
                capsuleBg.setColor(PRIMARY);
            } else {
                capsuleBg.setColor(GRAY_BG);
                capsuleBg.setStroke(dp(1), Color.parseColor("#B8FFFFFF"));
            }
            tab.setBackground(capsuleBg);
            tab.setOnClickListener(v -> {
                currentFilter = filter;
                refreshFilterTabs();
                refreshLibrary();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            filterContainer.addView(tab, params);
        }
    }

    private void refreshLibrary() {
        if (libraryTaskList == null) return;
        libraryTaskList.removeAllViews();

        String query = librarySearchInput != null
                ? librarySearchInput.getText().toString().toLowerCase(Locale.ROOT)
                : "";

        List<ClickTask> filtered = new ArrayList<>();
        for (ClickTask task : tasks) {
            if (!query.isEmpty() && !task.getName().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (matchesFilter(task)) {
                filtered.add(task);
            }
        }

        if (filtered.isEmpty()) {
            LinearLayout emptyCard = createCard(dp(16));
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setPadding(dp(40), dp(36), dp(40), dp(36));
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setOnClickListener(v -> showNewTaskDialog());

            TextView emptyIcon = new TextView(this);
            emptyIcon.setText("📋");
            emptyIcon.setTextSize(36);
            emptyIcon.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyIcon);

            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("暂无任务");
            emptyTitle.setTextSize(16);
            emptyTitle.setTextColor(TEXT_PRIMARY);
            emptyTitle.setTypeface(null, Typeface.BOLD);
            emptyTitle.setGravity(Gravity.CENTER);
            emptyTitle.setPadding(0, dp(12), 0, dp(4));
            emptyCard.addView(emptyTitle);

            TextView emptyHint = new TextView(this);
            emptyHint.setText("点击此处或右下角 + 创建第一个任务");
            emptyHint.setTextSize(13);
            emptyHint.setTextColor(TEXT_SECONDARY);
            emptyHint.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyHint);

            libraryTaskList.addView(emptyCard);
            return;
        }

        for (ClickTask task : filtered) {
            libraryTaskList.addView(buildLibraryTaskCard(task));
        }

        // 任务库卡片错位弹入
        libraryTaskList.post(() -> animateStaggeredPopIn(libraryTaskList, 50));
    }

    private boolean matchesFilter(ClickTask task) {
        String lastStatus = ExecutionLogStore.getLastStatusByTaskId(this, task.getId());
        switch (currentFilter) {
            case "运行中":
                return "运行中".equals(lastStatus)
                        || "已暂停".equals(lastStatus)
                        || "准备执行".equals(lastStatus);
            case "已完成":
                return "已完成".equals(lastStatus);
            case "已停止":
                return "已停止".equals(lastStatus);
            case "全部":
            default:
                return true;
        }
    }

    private View buildLibraryTaskCard(ClickTask task) {
        LinearLayout card = createCard(dp(12));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);

        // 任务类型图标
        String icon = getTaskTypeIcon(task);
        int iconColor = getTaskTypeColor(task);
        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(50), dp(50)));
        // 玻璃圆形容器：半透白底 + 颜色边框
        GradientDrawable glassIcon = new GradientDrawable();
        glassIcon.setShape(GradientDrawable.OVAL);
        glassIcon.setColor(Color.parseColor("#D9FFFFFF"));
        glassIcon.setStroke(dp(1), Color.argb(96, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
        iconFrame.setBackground(glassIcon);
        iconFrame.setElevation(dp(SHADOW_L1));
        iconFrame.setClipToOutline(true);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconFp3 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconFp3.gravity = Gravity.CENTER;
        iconFrame.addView(iconView, iconFp3);
        card.addView(iconFrame);

        // 文本信息（点击区域仅限此列，排除 CheckBox 区域）
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(16), 0, 0, 0);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        textCol.setOnClickListener(v -> {
            animatePressFeedback(card);
            selectedTask = task;
            editorSourcePage = currentPage;
            showPage(Page.EDITOR);
        });

        TextView title = new TextView(this);
        title.setText(task.getName());
        title.setTextSize(16);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        textCol.addView(title);

        TextView desc = new TextView(this);
        desc.setText(task.getDescription().isEmpty() ? "暂无描述" : task.getDescription());
        desc.setTextSize(13);
        desc.setTextColor(TEXT_SECONDARY);
        desc.setMaxLines(1);
        textCol.addView(desc);

        // 步骤类型图标行
        java.util.Set<String> typeSet = new java.util.HashSet<>();
        for (TaskStep s : task.getSteps()) {
            typeSet.add(s.getActionType().getDisplayName());
        }
        if (!typeSet.isEmpty()) {
            LinearLayout typeRow = new LinearLayout(this);
            typeRow.setOrientation(LinearLayout.HORIZONTAL);
            typeRow.setPadding(0, dp(6), 0, 0);
            for (String type : typeSet) {
                TextView typeBadge = new TextView(this);
                typeBadge.setText(type);
                typeBadge.setTextSize(10);
                typeBadge.setTextColor(Color.WHITE);
                typeBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
                typeBadge.setBackground(roundedDrawable(
                        Color.parseColor("#8090A4B8"), dp(6)));
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeParams.setMargins(0, 0, dp(6), 0);
                typeRow.addView(typeBadge, badgeParams);
            }
            textCol.addView(typeRow);
        }

        TextView meta = new TextView(this);
        int stepCount = task.getSteps().size();
        meta.setText(String.format(Locale.ROOT, "%d 个步骤 · 重复 %d 次", stepCount, task.getRepeatCount()));
        meta.setTextSize(12);
        meta.setTextColor(TEXT_SECONDARY);
        meta.setPadding(0, dp(4), 0, 0);
        textCol.addView(meta);

        card.addView(textCol);

        // 快捷执行按钮 —— 玻璃圆形
        TextView execBtn = new TextView(this);
        execBtn.setText("▶");
        execBtn.setTextSize(15);
        execBtn.setTextColor(SUCCESS);
        execBtn.setGravity(Gravity.CENTER);
        execBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        // 玻璃圆形背景
        GradientDrawable execGlass = new GradientDrawable();
        execGlass.setShape(GradientDrawable.OVAL);
        execGlass.setColor(Color.argb(30, Color.red(SUCCESS), Color.green(SUCCESS), Color.blue(SUCCESS)));
        execGlass.setStroke(dp(1), Color.argb(80, Color.red(SUCCESS), Color.green(SUCCESS), Color.blue(SUCCESS)));
        execBtn.setBackground(execGlass);
        execBtn.setElevation(dp(SHADOW_L1));
        execBtn.setClipToOutline(true);
        execBtn.setOnClickListener(v -> {
            animatePressFeedback(execBtn);
            selectedTask = task;
            startTask();
        });
        card.addView(execBtn);

        // 开关
        SwitchCompat toggle = new SwitchCompat(this);
        CompoundButton.OnCheckedChangeListener toggleListener = (buttonView, isChecked) -> {
            task.setEnabled(isChecked);
            TaskStore.upsertTask(this, task);
            // 仅更新状态文本，不触发全量 rebuild 避免卡片闪烁/重建
            refreshLibrary();
        };
        // 避免 setChecked 在绑定阶段误触发监听器导致连锁重建
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(task.isEnabled());
        toggle.setOnCheckedChangeListener(toggleListener);
        card.addView(toggle);

        return card;
    }

    private String getTaskTypeIcon(ClickTask task) {
        boolean hasTap = false, hasText = false, hasSwipe = false;
        for (TaskStep step : task.getSteps()) {
            switch (step.getActionType()) {
                case TAP:
                    hasTap = true;
                    break;
                case TEXT_INPUT:
                    hasText = true;
                    break;
                case SWIPE:
                    hasSwipe = true;
                    break;
            }
        }
        if (hasTap && hasText) return "📝";
        if (hasText) return "⌨️";
        if (hasSwipe) return "↔️";
        return "🖱️";
    }

    private int getTaskTypeColor(ClickTask task) {
        boolean hasTap = false, hasText = false;
        for (TaskStep step : task.getSteps()) {
            if (step.getActionType() == TaskActionType.TAP) hasTap = true;
            if (step.getActionType() == TaskActionType.TEXT_INPUT) hasText = true;
        }
        if (hasTap && hasText) return ORANGE;
        if (hasText) return PURPLE;
        return PRIMARY;
    }

    // ---------- 个人中心页 ----------

    private ScrollView buildProfilePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(pageBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView title = new TextView(this);
        title.setText("我的");
        title.setTextSize(28);
        title.setTextColor(PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        // 品牌渐变填色
        title.getPaint().setShader(new LinearGradient(0, 0, dp(200), 0,
                new int[]{Color.parseColor("#3978F6"), Color.parseColor("#7067D8")},
                null, Shader.TileMode.CLAMP));
        title.setPadding(0, dp(8), 0, dp(16));
        root.addView(title);

        // 辅助功能设置
        addProfileRow(root, "🔧", "辅助功能设置", "开启或管理无障碍服务权限", v -> openAccessibilitySettings());

        // 隐私说明
        addProfileRow(root, "🔒", "隐私说明", "了解应用如何保护你的数据", v -> showPrivacyDialog());

        // 权限说明
        addProfileRow(root, "📄", "权限说明", "查看无障碍服务权限使用范围", v -> showPermissionDialog());

        // 版本信息
        LinearLayout versionCard = createCard(dp(12));
        versionCard.setOrientation(LinearLayout.VERTICAL);
        versionCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView versionTitle = new TextView(this);
        versionTitle.setText("版本信息");
        versionTitle.setTextSize(16);
        versionTitle.setTextColor(TEXT_PRIMARY);
        versionTitle.setTypeface(null, Typeface.BOLD);
        versionCard.addView(versionTitle);

        TextView versionText = new TextView(this);
        versionText.setText("Click Assistant 移动端 v" + BuildConfig.VERSION_NAME + "\n项目版本 v" + BuildConfig.VERSION_NAME);
        versionText.setTextSize(13);
        versionText.setTextColor(TEXT_SECONDARY);
        versionText.setLineSpacing(0, 1.4f);
        versionText.setPadding(0, dp(8), 0, 0);
        versionCard.addView(versionText);

        root.addView(versionCard, blockParams());

        scroll.addView(root);
        return scroll;
    }

    private void addProfileRow(LinearLayout root, String icon, String title, String desc, View.OnClickListener listener) {
        LinearLayout row = createCard(dp(12));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOnClickListener(listener);
        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(params);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        row.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(16), 0, 0, 0);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTypeface(null, Typeface.BOLD);
        textCol.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(13);
        descView.setTextColor(TEXT_SECONDARY);
        textCol.addView(descView);

        row.addView(textCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(TEXT_SECONDARY);
        arrow.setGravity(Gravity.CENTER);
        // 玻璃圆形箭头
        GradientDrawable arrowBg = new GradientDrawable();
        arrowBg.setShape(GradientDrawable.OVAL);
        arrowBg.setColor(Color.parseColor("#B8FFFFFF"));
        arrowBg.setStroke(dp(1), Color.parseColor("#D0FFFFFF"));
        arrow.setBackground(arrowBg);
        arrow.setWidth(dp(32));
        arrow.setHeight(dp(32));
        row.addView(arrow);

        root.addView(row);
    }

    private void showPrivacyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("隐私说明")
                .setMessage("Click Assistant 仅在本地保存你主动创建的任务配置。\n\n"
                        + "辅助功能权限仅用于执行你主动启动的任务手势，不会读取或记录任何屏幕内容、密码或敏感信息。\n\n"
                        + "应用已关闭系统备份，任务数据不会进入云备份或设备迁移。")
                .setPositiveButton("我知道了", null)
                .show();
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("权限说明")
                .setMessage("Click Assistant 需要【辅助功能】权限才能：\n\n"
                        + "1. 在屏幕上执行你配置的点击和滑动手势\n"
                        + "2. 向已聚焦的输入框写入你指定的文本内容\n"
                        + "3. 显示任务执行时的悬浮控制入口\n\n"
                        + "本应用不会自动触发操作，也不会在后台持续监听屏幕内容。")
                .setPositiveButton("我知道了", null)
                .show();
    }

    // ---------- 执行日志页 ----------

    private ScrollView buildExecutionLogPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(pageBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(80)); // 底部留白给导航

        addPageHeader(root, "执行日志", "查看最近任务的执行结果");

        // 日志统计
        List<ExecutionLogEntry> allLogs = ExecutionLogStore.loadLog(this);
        int successCount = 0, failCount = 0, totalCount = allLogs.size();
        for (ExecutionLogEntry log : allLogs) {
            if ("已完成".equals(log.getStatus())) successCount++;
            else if ("执行失败".equals(log.getStatus())) failCount++;
        }

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(0, dp(8), 0, dp(8));

        addStatBadge(statsRow, "总计", totalCount, PRIMARY);
        addStatBadge(statsRow, "成功", successCount, SUCCESS);
        addStatBadge(statsRow, "失败", failCount, RED);
        root.addView(statsRow);

        logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(logContainer);

        Button clearButton = new Button(this);
        clearButton.setText("清空执行日志");
        clearButton.setTextColor(TEXT_PRIMARY);
        clearButton.setAllCaps(false);
        clearButton.setBackground(roundedDrawable(CARD_BG, dp(12)));
        clearButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        clearButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清空执行日志")
                    .setMessage("确定清空全部执行记录？此操作不可撤销")
                    .setPositiveButton("清空", (dialog, which) -> {
                        ExecutionLogStore.clear(this);
                        renderLog();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        LinearLayout.LayoutParams clearParams = blockParams();
        clearParams.setMargins(0, dp(16), 0, 0);
        root.addView(clearButton, clearParams);

        scroll.addView(root);
        return scroll;
    }

    private void addStatBadge(LinearLayout parent, String label, int count, int color) {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setPadding(dp(12), dp(8), dp(12), dp(8));
        // 玻璃统计徽章：半透白底 + 颜色边框
        GradientDrawable statGlass = new GradientDrawable();
        statGlass.setShape(GradientDrawable.RECTANGLE);
        statGlass.setCornerRadius(dp(10));
        statGlass.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        statGlass.setStroke(dp(1), Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)));
        badge.setBackground(statGlass);
        badge.setElevation(dp(1));
        badge.setClipToOutline(true);
        badge.setGravity(Gravity.CENTER);

        TextView countView = new TextView(this);
        countView.setText(String.valueOf(count));
        countView.setTextSize(20);
        countView.setTextColor(color);
        countView.setTypeface(null, Typeface.BOLD);
        badge.addView(countView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(TEXT_SECONDARY);
        badge.addView(labelView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, 0, dp(8), 0);
        parent.addView(badge, params);
    }

    private void addPageHeader(LinearLayout root, String titleText, String subtitleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(28);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);
    }

    // ---------- 任务编辑器 ----------

    private ScrollView buildTaskEditorPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(pageBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(32));

        // 返回按钮 —— 玻璃胶囊样式
        TextView backButton = new TextView(this);
        backButton.setText("‹ 返回");
        backButton.setTextSize(14);
        backButton.setTextColor(TEXT_SECONDARY);
        backButton.setPadding(dp(12), dp(6), dp(12), dp(6));
        backButton.setBackground(glassCapsuleDrawable(Color.parseColor("#A6FFFFFF")));
        backButton.setOnClickListener(v -> exitEditor());
        root.addView(backButton);

        // 顶部工具栏：返回 + 保存 并列
        TextView saveTopButton = new TextView(this);
        saveTopButton.setText("保存");
        saveTopButton.setTextSize(16);
        saveTopButton.setTextColor(Color.WHITE);
        saveTopButton.setBackground(roundedDrawable(PRIMARY, dp(8)));
        saveTopButton.setPadding(dp(12), dp(6), dp(12), dp(6));
        saveTopButton.setGravity(Gravity.CENTER);
        saveTopButton.setOnClickListener(v -> saveTaskFromInputs(true));
        FrameLayout toolbar = new FrameLayout(this);
        FrameLayout.LayoutParams saveParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        toolbar.addView(backButton);
        toolbar.addView(saveTopButton, saveParams);
        root.addView(toolbar);

        TextView title = new TextView(this);
        title.setText(selectedTask != null ? selectedTask.getName() : "编辑任务");
        title.setTextSize(24);
        title.setTextColor(PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        // 品牌渐变填色
        title.getPaint().setShader(new LinearGradient(0, 0, dp(280), 0,
                new int[]{Color.parseColor("#3978F6"), Color.parseColor("#7067D8")},
                null, Shader.TileMode.CLAMP));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("基础信息、步骤与执行控制");
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        // 基础信息卡片
        LinearLayout basicCard = createCard(dp(12));
        basicCard.setOrientation(LinearLayout.VERTICAL);
        basicCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        addSectionTitle(basicCard, "基础信息");
        taskNameInput = addTextInput(basicCard, "任务名称", InputType.TYPE_CLASS_TEXT);
        taskDescInput = addTextInput(basicCard, "任务描述（可选）", InputType.TYPE_CLASS_TEXT);
        taskEnabledBox = new SwitchCompat(this);
        taskEnabledBox.setText("启用该任务");
        taskEnabledBox.setTextColor(TEXT_PRIMARY);
        basicCard.addView(taskEnabledBox);
        repeatCountInput = addTextInput(basicCard, "重复次数", InputType.TYPE_CLASS_NUMBER);
        startDelayInput = addTextInput(basicCard, "开始延迟（毫秒）", InputType.TYPE_CLASS_NUMBER);
        // 为基础信息控件绑定变更监听，触发脏标记
        attachDirtyListeners();

        Button saveButton = new Button(this);
        saveButton.setText("保存任务");
        saveButton.setTextColor(Color.WHITE);
        saveButton.setAllCaps(false);
        saveButton.setBackground(roundedDrawable(PRIMARY, dp(12)));
        saveButton.setPadding(dp(16), dp(14), dp(16), dp(14));
        saveButton.setOnClickListener(v -> saveTaskFromInputs(true));
        basicCard.addView(saveButton, blockParams());

        root.addView(basicCard, blockParams());

        // 步骤列表卡片
        LinearLayout stepsCard = createCard(dp(12));
        stepsCard.setOrientation(LinearLayout.VERTICAL);
        stepsCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        addSectionTitle(stepsCard, "步骤设置");
        stepsContainer = new LinearLayout(this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        stepsCard.addView(stepsContainer);

        // 添加步骤 —— 虚线边框玻璃卡片
        LinearLayout addStepGlass = new LinearLayout(this);
        addStepGlass.setOrientation(LinearLayout.VERTICAL);
        addStepGlass.setGravity(Gravity.CENTER);
        addStepGlass.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable dashBg = new GradientDrawable();
        dashBg.setShape(GradientDrawable.RECTANGLE);
        dashBg.setCornerRadius(dp(12));
        dashBg.setColor(Color.argb(40, Color.red(PRIMARY), Color.green(PRIMARY), Color.blue(PRIMARY)));
        dashBg.setStroke(dp(1), Color.argb(80, Color.red(PRIMARY), Color.green(PRIMARY), Color.blue(PRIMARY)));
        addStepGlass.setBackground(dashBg);
        addStepGlass.setOnClickListener(v -> showAddStepDialog());

        TextView plusIcon = new TextView(this);
        plusIcon.setText("＋");
        plusIcon.setTextSize(22);
        plusIcon.setTextColor(PRIMARY);
        plusIcon.setGravity(Gravity.CENTER);
        addStepGlass.addView(plusIcon);

        TextView addHint = new TextView(this);
        addHint.setText("添加步骤");
        addHint.setTextSize(14);
        addHint.setTextColor(PRIMARY);
        addHint.setTypeface(null, Typeface.BOLD);
        addHint.setPadding(0, dp(4), 0, 0);
        addStepGlass.addView(addHint);

        stepsCard.addView(addStepGlass, blockParams());

        root.addView(stepsCard, blockParams());

        // 执行控制卡片
        LinearLayout controlCard = createCard(dp(12));
        controlCard.setOrientation(LinearLayout.VERTICAL);
        controlCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        addSectionTitle(controlCard, "执行控制");
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(8), 0, dp(8));

        editorStartButton = createActionButton("开始执行", PRIMARY, v -> startTask());
        editorPauseButton = createActionButton("暂停", TEXT_PRIMARY, v -> pauseTask());
        editorResumeButton = createActionButton("继续", TEXT_PRIMARY, v -> resumeTask());
        editorStopButton = createActionButton("停止", RED, v -> stopTask());

        actionRow.addView(editorStartButton, weightParams());
        actionRow.addView(editorPauseButton, weightParams());
        actionRow.addView(editorResumeButton, weightParams());
        actionRow.addView(editorStopButton, weightParams());
        updateExecutionButtonStates();
        controlCard.addView(actionRow);

        // 任务级操作：复制、删除
        LinearLayout taskActionRow = new LinearLayout(this);
        taskActionRow.setOrientation(LinearLayout.HORIZONTAL);
        taskActionRow.setPadding(0, dp(8), 0, dp(8));
        Button duplicateButton = createActionButton("复制任务", PRIMARY, v -> duplicateSelectedTask());
        Button deleteTaskButton = createActionButton("删除任务", RED, v -> deleteSelectedTask());
        taskActionRow.addView(duplicateButton, weightParams());
        taskActionRow.addView(deleteTaskButton, weightParams());
        controlCard.addView(taskActionRow);

        TextView safetyHint = new TextView(this);
        safetyHint.setText("请先在安全界面验证坐标与步骤，避免误点登录、支付、权限或系统安全界面。");
        safetyHint.setTextColor(TEXT_SECONDARY);
        safetyHint.setTextSize(13);
        safetyHint.setPadding(0, dp(12), 0, 0);
        safetyHint.setLineSpacing(0, 1.4f);
        controlCard.addView(safetyHint);

        root.addView(controlCard, blockParams());

        scroll.addView(root);

        // 首次构建编辑器时填充字段并渲染步骤，后续切回时由 loadTasks 处理步骤刷新
        if (selectedTask != null) {
            populateTaskFields();
            renderSteps();
        }

        return scroll;
    }

    private Button createActionButton(String text, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(color == RED ? Color.WHITE : (color == PRIMARY ? Color.WHITE : TEXT_PRIMARY));
        button.setAllCaps(false);
        button.setBackground(roundedDrawable(color == RED ? RED : (color == PRIMARY ? PRIMARY : GRAY_BG), dp(10)));
        button.setPadding(dp(8), dp(12), dp(8), dp(12));
        button.setOnClickListener(listener);
        return button;
    }

    /// <summary>
    /// 退出任务编辑器：若有未保存修改，弹确认对话框（保存/不保存/取消）。
    /// 返回进入编辑器时的来源页面（首页/任务库等）。
    /// </summary>
    private void exitEditor() {
        final Page backPage = editorSourcePage;
        if (!editorDirty) {
            showPage(backPage);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("未保存的修改")
                .setMessage("当前任务有未保存的修改，是否保存？")
                .setPositiveButton("保存", (dialog, which) -> {
                    if (saveTaskFromInputs(true)) {
                        showPage(backPage);
                    }
                })
                .setNegativeButton("不保存", (dialog, which) -> showPage(backPage))
                .setNeutralButton("取消", null)
                .show();
    }

    /// <summary>
    /// 为编辑器基础信息控件绑定变更监听，触发脏标记。
    /// </summary>
    private void attachDirtyListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressDirty) editorDirty = true;
            }
        };
        if (taskNameInput != null) taskNameInput.addTextChangedListener(watcher);
        if (taskDescInput != null) taskDescInput.addTextChangedListener(watcher);
        if (repeatCountInput != null) repeatCountInput.addTextChangedListener(watcher);
        if (startDelayInput != null) startDelayInput.addTextChangedListener(watcher);
        if (taskEnabledBox != null) {
            taskEnabledBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!suppressDirty) editorDirty = true;
            });
        }
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
        selectedTask = tasks.get(selectedIndex);

        // 编辑器页面跳过字段重填，避免用已保存数据覆盖用户正在编辑的内容；
        // 步骤列表仍刷新以反映取点等外部更新。
        if (currentPage != Page.EDITOR) {
            populateTaskFields();
        }
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
        suppressDirty = true;
        taskNameInput.setText(selectedTask.getName());
        taskDescInput.setText(selectedTask.getDescription());
        taskEnabledBox.setChecked(selectedTask.isEnabled());
        repeatCountInput.setText(String.valueOf(selectedTask.getRepeatCount()));
        startDelayInput.setText(String.valueOf(selectedTask.getStartDelayMs()));
        suppressDirty = false;
    }

    private void renderSteps() {
        if (stepsContainer == null) return;
        stepsContainer.removeAllViews();
        if (selectedTask == null) return;

        List<TaskStep> steps = selectedTask.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            stepsContainer.addView(buildStepRow(steps.get(i), i, steps.size()), blockParams());
        }

        if (steps.isEmpty()) {
            LinearLayout emptyCard = createCard(dp(16));
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setPadding(dp(32), dp(28), dp(32), dp(28));
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setOnClickListener(v -> showAddStepDialog());

            TextView emptyIcon = new TextView(this);
            emptyIcon.setText("➕");
            emptyIcon.setTextSize(40);
            emptyIcon.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyIcon);

            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("添加第一个步骤");
            emptyTitle.setTextSize(16);
            emptyTitle.setTextColor(TEXT_PRIMARY);
            emptyTitle.setTypeface(null, Typeface.BOLD);
            emptyTitle.setGravity(Gravity.CENTER);
            emptyTitle.setPadding(0, dp(12), 0, dp(4));
            emptyCard.addView(emptyTitle);

            TextView emptyDesc = new TextView(this);
            emptyDesc.setText("点击此处或下方按钮添加步骤");
            emptyDesc.setTextSize(13);
            emptyDesc.setTextColor(TEXT_SECONDARY);
            emptyDesc.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyDesc);

            stepsContainer.addView(emptyCard, blockParams());
            return;
        }

        // 步骤列表错位弹入
        stepsContainer.post(() -> animateStaggeredPopIn(stepsContainer, 40));
    }

    private void renderLog() {
        if (logContainer == null) return;
        logContainer.removeAllViews();
        allLogEntries = ExecutionLogStore.loadLog(this);
        Collections.reverse(allLogEntries);

        if (allLogEntries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无执行记录");
            empty.setTextColor(TEXT_SECONDARY);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            empty.setGravity(Gravity.CENTER);
            logContainer.addView(empty);
            logDisplayCount = 30;
            return;
        }

        int limit = Math.min(allLogEntries.size(), logDisplayCount);
        for (int i = 0; i < limit; i++) {
            ExecutionLogEntry entry = allLogEntries.get(i);
            LinearLayout card = createCard(dp(12));
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams params = blockParams();
            params.setMargins(0, 0, 0, dp(10));
            card.setLayoutParams(params);

            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM/dd HH:mm", Locale.ROOT);
            String time = format.format(new java.util.Date(entry.getTimestamp()));
            String taskName = entry.getTaskName().isEmpty() ? "未命名任务" : entry.getTaskName();

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView statusBadge = new TextView(this);
            statusBadge.setText(entry.getStatus());
            statusBadge.setTextSize(11);
            statusBadge.setTextColor(Color.WHITE);
            statusBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
            statusBadge.setBackground(roundedDrawable(getStatusColor(entry.getStatus()), dp(6)));
            header.addView(statusBadge);

            TextView timeView = new TextView(this);
            timeView.setText("  " + time);
            timeView.setTextSize(12);
            timeView.setTextColor(TEXT_SECONDARY);
            header.addView(timeView);
            card.addView(header);

            TextView taskLine = new TextView(this);
            taskLine.setText(taskName);
            taskLine.setTextSize(14);
            taskLine.setTextColor(TEXT_PRIMARY);
            taskLine.setTypeface(null, Typeface.BOLD);
            taskLine.setPadding(0, dp(8), 0, dp(2));
            card.addView(taskLine);

            TextView msgLine = new TextView(this);
            msgLine.setText(entry.getMessage());
            msgLine.setTextSize(13);
            msgLine.setTextColor(TEXT_SECONDARY);
            msgLine.setLineSpacing(0, 1.3f);
            card.addView(msgLine);

            card.setOnClickListener(v -> {
                String taskId = entry.getTaskId();
                if (taskId == null || taskId.isEmpty()) return;
                ClickTask target = null;
                for (ClickTask t : tasks) {
                    if (taskId.equals(t.getId())) { target = t; break; }
                }
                final ClickTask foundTask = target;
                new AlertDialog.Builder(this)
                        .setTitle(taskName)
                        .setItems(new String[]{"重新执行此任务", "查看任务详情"}, (dialog, which) -> {
                            if (which == 0 && foundTask != null) {
                                selectedTask = foundTask;
                                startTask();
                            } else if (which == 1 && foundTask != null) {
                                selectedTask = foundTask;
                                editorSourcePage = currentPage;
                                showPage(Page.EDITOR);
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            logContainer.addView(card);
        }

        // 加载更多
        if (allLogEntries.size() > logDisplayCount) {
            Button loadMore = new Button(this);
            loadMore.setText("加载更多（已显示 " + logDisplayCount + " / 共 " + allLogEntries.size() + " 条）");
            loadMore.setTextColor(PRIMARY);
            loadMore.setAllCaps(false);
            loadMore.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(8)));
            loadMore.setPadding(dp(16), dp(12), dp(16), dp(12));
            loadMore.setOnClickListener(v -> {
                logDisplayCount += 30;
                renderLog();
            });
            logContainer.addView(loadMore, blockParams());
        }

        // 日志条目错位弹入
        logContainer.post(() -> animateStaggeredPopIn(logContainer, 30));
    }

    private int getStatusColor(String status) {
        if (status == null) return TEXT_SECONDARY;
        switch (status) {
            case "已完成":
                return SUCCESS;
            case "运行中":
                return PRIMARY;
            case "已暂停":
                return ORANGE;
            case "执行失败":
            case "保存失败":
                return RED;
            case "已停止":
            default:
                return TEXT_SECONDARY;
        }
    }

    private LinearLayout buildStepRow(TaskStep step, int index, int total) {
        LinearLayout row = createCard(dp(12));
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView numberView = new TextView(this);
        int circleColor = getStepColor(step.getActionType());
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.argb(30, Color.red(circleColor), Color.green(circleColor), Color.blue(circleColor)));
        circleBg.setStroke(dp(1), Color.argb(80, Color.red(circleColor), Color.green(circleColor), Color.blue(circleColor)));
        circleBg.setSize(dp(30), dp(30));
        numberView.setBackground(circleBg);
        numberView.setText(String.valueOf(index + 1));
        numberView.setTextColor(circleColor);
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
        summary.setTextColor(TEXT_PRIMARY);
        summary.setTypeface(null, Typeface.BOLD);
        headerRow.addView(summary);
        row.addView(headerRow);

        TextView detail = new TextView(this);
        detail.setText("  " + step.getSummary());
        detail.setTextColor(TEXT_SECONDARY);
        detail.setTextSize(13);
        row.addView(detail);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(8), 0, 0);
        // 排序按钮：上移 / 下移
        Button upButton = new Button(this);
        upButton.setText("↑");
        upButton.setTextColor(PRIMARY);
        upButton.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(8)));
        upButton.setAllCaps(false);
        upButton.setEnabled(index > 0);
        upButton.setOnClickListener(view -> moveStep(index, -1));
        Button downButton = new Button(this);
        downButton.setText("↓");
        downButton.setTextColor(PRIMARY);
        downButton.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(8)));
        downButton.setAllCaps(false);
        downButton.setEnabled(index < total - 1);
        downButton.setOnClickListener(view -> moveStep(index, 1));
        buttons.addView(upButton, weightParams());
        buttons.addView(downButton, weightParams());
        // 点选坐标（不依赖弹窗，直接从编辑器行触发，避免弹窗被后台回收）
        if (step.getActionType() == TaskActionType.TAP || step.getActionType() == TaskActionType.SWIPE) {
            Button pickButton = new Button(this);
            pickButton.setText("点选坐标");
            pickButton.setTextColor(SUCCESS);
            pickButton.setBackground(roundedDrawable(SUCCESS_LIGHT, dp(8)));
            pickButton.setAllCaps(false);
            pickButton.setOnClickListener(view -> startCoordinatePickForStep(step, step.getActionType()));
            buttons.addView(pickButton, weightParams());
        }
        // 编辑 / 删除按钮
        Button editButton = new Button(this);
        editButton.setText("编辑");
        editButton.setOnClickListener(view -> showStepEditorDialog(step));
        Button deleteButton = new Button(this);
        deleteButton.setText("删除");
        deleteButton.setOnClickListener(view -> deleteStep(step));
        editButton.setTextColor(PRIMARY);
        editButton.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(8)));
        editButton.setAllCaps(false);
        deleteButton.setTextColor(RED);
        deleteButton.setBackground(roundedDrawable(RED_LIGHT, dp(8)));
        deleteButton.setAllCaps(false);
        buttons.addView(editButton, weightParams());
        buttons.addView(deleteButton, weightParams());
        row.addView(buttons);

        return row;
    }

    private int getStepColor(TaskActionType actionType) {
        switch (actionType) {
            case TAP:
                return PRIMARY;
            case LONG_PRESS:
                return ORANGE;
            case SWIPE:
                return Color.parseColor("#16A064");
            case TEXT_INPUT:
                return Color.parseColor("#8B5CF6");
            default:
                return PRIMARY;
        }
    }

    // ---------- 任务操作 ----------

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
        editorSourcePage = currentPage;
        showPage(Page.EDITOR);
        Toast.makeText(this, "已新建任务", Toast.LENGTH_SHORT).show();
    }

    private void duplicateSelectedTask() {
        if (selectedTask == null) return;
        if (currentPage == Page.EDITOR && !saveTaskFromInputs(false)) return;

        ClickTask copy = selectedTask.duplicate();
        TaskStore.upsertTask(this, copy);
        selectedTask = copy;
        loadTasks();
        Toast.makeText(this, "已复制任务", Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedTask() {
        if (selectedTask == null) return;
        new AlertDialog.Builder(this)
                .setTitle("删除任务")
                .setMessage("确定删除任务“" + selectedTask.getName() + "”吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    TaskStore.deleteTask(this, selectedTask.getId());
                    selectedTask = null;
                    loadTasks();
                    showPage(Page.LIBRARY);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean saveTaskFromInputs(boolean showToast) {
        if (selectedTask == null) return false;
        // 仅在编辑器页真正构建过控件时才读取输入；否则跳过"读取→写回"步骤，
        // 防止从任务库/最近任务等入口直接调用 startTask() 时因 EditText 为 null 闪退。
        if (taskNameInput == null || taskDescInput == null || taskEnabledBox == null
                || repeatCountInput == null || startDelayInput == null) {
            return true;
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

            // 保存完成后清除标记覆盖层
            ClickAssistantAccessibilityService.clearMarkers();

            refreshStatus();
            editorDirty = false;
            if (showToast) Toast.makeText(this, "任务已保存", Toast.LENGTH_SHORT).show();
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
        if (selectedTask == null) return;
        final TaskActionType[] types = {TaskActionType.TAP, TaskActionType.SWIPE, TaskActionType.TEXT_INPUT};
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
        editorDirty = true;
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
        if (target < 0 || target >= steps.size()) return;
        Collections.swap(steps, index, target);
        selectedTask.normalizeStepOrders();
        TaskStore.upsertTask(this, selectedTask);
        editorDirty = true;
        renderSteps();
    }

    private void deleteStep(TaskStep step) {
        selectedTask.getSteps().removeIf(s -> s.getId().equals(step.getId()));
        selectedTask.normalizeStepOrders();
        TaskStore.upsertTask(this, selectedTask);
        editorDirty = true;
        renderSteps();
    }

    private void showStepEditorDialog(TaskStep step) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(16));
        scroll.addView(container);

        final EditText nameInput = addEditorInput(container, "步骤名称", InputType.TYPE_CLASS_TEXT, step.getName());
        final SwitchCompat enabledBox = new SwitchCompat(this);
        enabledBox.setText("启用该步骤");
        enabledBox.setChecked(step.isEnabled());
        enabledBox.setTextColor(TEXT_PRIMARY);
        container.addView(enabledBox);

        final EditText xInput;
        final EditText yInput;
        if (step.getActionType() == TaskActionType.TAP || step.getActionType() == TaskActionType.SWIPE) {
            xInput = addEditorInput(container, "X 坐标（点击/滑动起点）", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getX()));
            yInput = addEditorInput(container, "Y 坐标（点击/滑动起点）", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getY()));
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
            autoFocusBox.setTextColor(TEXT_PRIMARY);
            container.addView(autoFocusBox);
        }

        final EditText beforeDelayInput = addEditorInput(container, "步骤前等待，毫秒", InputType.TYPE_CLASS_NUMBER, String.valueOf(step.getBeforeDelayMs()));

        // 取点按钮：先保存弹窗输入、关闭弹窗，再进入取点模式
        final Runnable beforePick = () -> {
            step.setName(nameInput.getText().toString().trim());
            step.setEnabled(enabledBox.isChecked());
            if (xInput != null) step.setX(parseInt(xInput, "X 坐标"));
            if (yInput != null) step.setY(parseInt(yInput, "Y 坐标"));
            step.setBeforeDelayMs(parseInt(beforeDelayInput, "步骤前等待"));
            if (step.getActionType() == TaskActionType.TAP) {
                if (tapCountInput != null) step.setTapCount(parseInt(tapCountInput, "点击次数"));
                if (clickIntervalInput != null) step.setClickIntervalMs(parseInt(clickIntervalInput, "连点间隔"));
                if (pressDurationInput != null) step.setPressDurationMs(parseInt(pressDurationInput, "按压时长"));
            } else if (step.getActionType() == TaskActionType.SWIPE) {
                if (endXInput != null) step.setEndX(parseInt(endXInput, "终点 X 坐标"));
                if (endYInput != null) step.setEndY(parseInt(endYInput, "终点 Y 坐标"));
                if (durationInput != null) step.setDurationMs(parseInt(durationInput, "滑动持续时间"));
            }
            step.validateForSave();
            TaskStore.upsertTask(this, selectedTask);
            editorDirty = true;
            renderSteps();
        };

        if (step.getActionType() == TaskActionType.TAP) {
            addPickButton(container, step, TaskActionType.TAP, beforePick);
        } else if (step.getActionType() == TaskActionType.SWIPE) {
            addPickButton(container, step, TaskActionType.SWIPE, beforePick);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑步骤：" + step.getActionType().getDisplayName())
                .setView(scroll)
                .setPositiveButton("保存", (d, which) -> {
                    try {
                        step.setName(nameInput.getText().toString().trim());
                        step.setEnabled(enabledBox.isChecked());
                        if (xInput != null) step.setX(parseInt(xInput, "X 坐标"));
                        if (yInput != null) step.setY(parseInt(yInput, "Y 坐标"));
                        step.setBeforeDelayMs(parseInt(beforeDelayInput, "步骤前等待"));

                        if (step.getActionType() == TaskActionType.TAP) {
                            if (tapCountInput != null) step.setTapCount(parseInt(tapCountInput, "点击次数"));
                            if (clickIntervalInput != null) step.setClickIntervalMs(parseInt(clickIntervalInput, "连点间隔"));
                            if (pressDurationInput != null) step.setPressDurationMs(parseInt(pressDurationInput, "按压时长"));
                        } else if (step.getActionType() == TaskActionType.SWIPE) {
                            if (endXInput != null) step.setEndX(parseInt(endXInput, "终点 X 坐标"));
                            if (endYInput != null) step.setEndY(parseInt(endYInput, "终点 Y 坐标"));
                            if (durationInput != null) step.setDurationMs(parseInt(durationInput, "滑动持续时间"));
                        } else if (step.getActionType() == TaskActionType.TEXT_INPUT) {
                            if (textInput != null) step.setTextContent(textInput.getText().toString());
                            if (charIntervalInput != null) step.setCharIntervalMs(parseInt(charIntervalInput, "逐字间隔"));
                            if (autoFocusBox != null) step.setAutoFocusBeforeInput(autoFocusBox.isChecked());
                        }

                        step.validateForSave();
                        TaskStore.upsertTask(this, selectedTask);
                        editorDirty = true;
                        renderSteps();
                    } catch (IllegalArgumentException ex) {
                        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        animateDialogEntrance(dialog);
    }

    private void addPickButton(LinearLayout container, TaskStep step, TaskActionType mode) {
        addPickButton(container, step, mode, null);
    }

    private void addPickButton(LinearLayout container, TaskStep step, TaskActionType mode, Runnable beforePick) {
        Button pickButton = new Button(this);
        pickButton.setText(mode == TaskActionType.SWIPE ? "点选滑动起终点" : "点选点击位置");
        pickButton.setTextColor(PRIMARY);
        pickButton.setAllCaps(false);
        pickButton.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(8)));
        pickButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        pickButton.setOnClickListener(view -> {
            try {
                if (beforePick != null) beforePick.run();
            } catch (Exception ex) {
                // 取点前数据校验失败则不进入取点流程
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            startCoordinatePickForStep(step, mode);
        });
        container.addView(pickButton, blockParams());
    }

    private EditText addEditorInput(LinearLayout container, String hint, int inputType, String initial) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        if (initial != null) input.setText(initial);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SECONDARY);
        input.setPadding(0, dp(12), 0, dp(12));
        input.setBackground(null);
        container.addView(input, blockParams());
        return input;
    }

    private void startCoordinatePickForStep(TaskStep step, TaskActionType mode) {
        if (selectedTask == null) return;
        if (!ClickAssistantAccessibilityService.isActive()) {
            Toast.makeText(this, "请先开启辅助功能服务，再点选位置", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        // 保存当前任务和步骤为"活动编辑目标"
        TaskStore.saveActiveTaskId(this, selectedTask.getId());
        TaskStore.saveActiveStepId(this, step.getId());

        // 先保存任务
        try {
            if (taskNameInput != null) {
                selectedTask.setName(taskNameInput.getText().toString().trim());
            }
            selectedTask.validateForSave();
            TaskStore.upsertTask(this, selectedTask);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }

        TaskStore.saveLastStatus(this, "已设置活动步骤，请切到目标界面后点击右下角「取点」按钮");
        refreshStatus();
        Toast.makeText(this, "已设置活动步骤，切到目标界面后点击右下角「取点」", Toast.LENGTH_LONG).show();
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
        if (!saveTaskFromInputs(false) || selectedTask == null) return;
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
        boolean skipConfirm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean("skip_execution_confirm", false);
        if (skipConfirm) {
            doStartTask(task);
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("任务：").append(task.getName()).append("\n");
        summary.append("重复次数：").append(task.getRepeatCount()).append("\n");
        summary.append("开始延迟：").append(task.getStartDelayMs()).append(" 毫秒\n");
        summary.append("步骤：\n");
        int index = 0;
        for (TaskStep step : task.getSteps()) {
            if (!step.isEnabled()) continue;
            index++;
            summary.append(String.format(Locale.ROOT, "  %d. %s - %s\n", index, step.getActionType().getDisplayName(), step.getSummary()));
        }

        final CheckBox rememberBox = new CheckBox(this);
        rememberBox.setText("记住选择，不再弹出确认");
        rememberBox.setTextColor(TEXT_PRIMARY);
        rememberBox.setPadding(0, dp(8), 0, 0);

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView msgView = new TextView(this);
        msgView.setText(summary.toString());
        msgView.setTextSize(14);
        msgView.setTextColor(TEXT_PRIMARY);
        dialogLayout.addView(msgView);
        dialogLayout.addView(rememberBox);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("执行前确认")
                .setView(dialogLayout)
                .setPositiveButton("确认执行", (d, which) -> {
                    if (rememberBox.isChecked()) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit().putBoolean("skip_execution_confirm", true).apply();
                    }
                    doStartTask(task);
                })
                .setNegativeButton("取消", null)
                .show();
        animateDialogEntrance(dialog);
    }

    private void doStartTask(ClickTask task) {
        if (!ClickAssistantAccessibilityService.startTask(task)) {
            TaskStore.saveLastStatus(this, "执行失败：辅助功能服务未连接或已有任务正在运行");
            refreshStatus();
            Toast.makeText(this, "启动失败，请确认辅助功能服务已连接且没有其他任务运行", Toast.LENGTH_LONG).show();
            return;
        }
        TaskStore.saveActiveTaskId(this, task.getId());
        refreshStatus();
        Toast.makeText(this, "已开始执行，可切到目标界面", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
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
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        ComponentName expected = new ComponentName(this, ClickAssistantAccessibilityService.class);
        String expectedLong = expected.flattenToString();
        String expectedShort = expected.flattenToShortString();
        // AccessibilityServiceInfo.getId() 可能返回长格式或短格式，都检查
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabledServices) {
            String id = info.getId();
            if (expectedLong.equals(id) || expectedShort.equals(id)) {
                return true;
            }
            // 兜底：通过 ServiceInfo 对比包名 + 类名
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                String pkg = info.getResolveInfo().serviceInfo.packageName;
                String cls = info.getResolveInfo().serviceInfo.name;
                if (getPackageName().equals(pkg) && ClickAssistantAccessibilityService.class.getName().equals(cls)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOnboardingCompleted() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    private void setOnboardingCompleted() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .apply();
    }

    // ---------- UI 样式工具 ----------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout createCard(int radius) {
        return createCard(radius, SHADOW_L2);
    }

    private LinearLayout createCard(int radius, int shadowLevel) {
        LinearLayout card = new LinearLayout(this);
        card.setBackground(glassDrawable(radius));
        card.setElevation(dp(shadowLevel));
        card.setClipToOutline(true);
        return card;
    }

    private GradientDrawable roundedDrawable(int color, int radius) {
        GradientDrawable drawable;
        if (color == PRIMARY) {
            drawable = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor("#4C8DFF"), PRIMARY, Color.parseColor("#5668D8")});
        } else {
            drawable = new GradientDrawable();
            drawable.setColor(color);
        }
        drawable.setCornerRadius(radius);
        return drawable;
    }

    /// <summary>
    /// 通过透明渐变、亮边和阴影模拟玻璃材质，Android 7+ 均可稳定显示。
    /// 返回 LayerDrawable：底层为玻璃渐变基底，上层为 Specular 高光层，
    /// 模拟玻璃表面左上角捕获环境光源的效果。
    /// 支持深色模式：通过 ContextCompat.getColor 动态获取颜色资源。
    /// </summary>
    private Drawable glassDrawable(int radius) {
        int topColor = getThemeColor(R.color.glass_card_top, Color.parseColor("#EEFFFFFF"));
        int bottomColor = getThemeColor(R.color.glass_card_bottom, Color.parseColor("#BFFFFFFF"));
        int bleedColor = getThemeColor(R.color.glass_bleed, Color.parseColor("#123978F6"));

        // ① 底层：TOP_BOTTOM 三停渐变（顶部玻璃 → 中部半透 → 底部品牌蓝光晕泄漏）
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, bottomColor, bleedColor});
        base.setCornerRadius(radius);
        base.setStroke(dp(1), BORDER);

        // ② 上层：Specular 高光层 — 左上角白色渐变至透明，模拟玻璃边缘捕获光源
        GradientDrawable specular = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#60FFFFFF"),  // 左上 38% 白
                        Color.parseColor("#20FFFFFF"),  // 中上 12% 白
                        Color.TRANSPARENT,              // 中下 全透
                        Color.TRANSPARENT               // 右下 全透
                });
        specular.setCornerRadius(radius);

        return new LayerDrawable(new Drawable[]{base, specular});
    }

    /// <summary>
    /// 页面背景：底层保持现有三色渐变，叠加两处径向光晕（右上暖光模拟环境光散射，
    /// 左下冷光模拟屏幕环境补光），营造空间深度感。
    /// 光晕颜色从 color 资源动态获取，自动适配深色模式。
    /// </summary>
    private Drawable pageBackgroundDrawable() {
        int startColor = getThemeColor(R.color.bg_page_start, Color.parseColor("#E8F2F7"));
        int midColor = getThemeColor(R.color.bg_page_mid, Color.parseColor("#F4F7FA"));
        int endColor = getThemeColor(R.color.bg_page_end, Color.parseColor("#EEEAF6"));
        int warmColor = getThemeColor(R.color.bg_glow_warm, Color.parseColor("#2DFFF0E0"));
        int coolColor = getThemeColor(R.color.bg_glow_cool, Color.parseColor("#18E0F0FF"));

        // ① 底层：现有三色线性渐变
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, midColor, endColor});

        // ② 右上角暖色光晕（模拟窗外暖色环境光散射）
        GradientDrawable warmGlow = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        warmColor,                      // 动态获取，适配深色模式
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                });
        warmGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        warmGlow.setGradientCenter(0.85f, 0.15f);
        warmGlow.setGradientRadius(dp(500));

        // ③ 左下角冷色补光（模拟环境中蓝色冷光）
        GradientDrawable coolGlow = new GradientDrawable(
                GradientDrawable.Orientation.BR_TL,
                new int[]{
                        coolColor,                      // 动态获取，适配深色模式
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                });
        coolGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        coolGlow.setGradientCenter(0.15f, 0.85f);
        coolGlow.setGradientRadius(dp(400));

        return new LayerDrawable(new Drawable[]{base, warmGlow, coolGlow});
    }

    /// <summary>
    /// 动态获取颜色资源，自动适配深色模式（values-night/colors.xml）。
    /// 如果资源未找到则回退到硬编码颜色。
    /// </summary>
    private int getThemeColor(int colorResId, int fallbackColor) {
        try {
            return ContextCompat.getColor(this, colorResId);
        } catch (Resources.NotFoundException e) {
            return fallbackColor;
        }
    }

    // ===== 液态玻璃动效系统 =====

    /// <summary>
    /// 通用弹入动画：缩放 + 淡入。适用于卡片、弹窗等。
    /// 使用 OvershootInterpolator 模拟弹簧过冲效果。
    /// </summary>
    private void animatePopIn(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.92f);
        view.setScaleY(0.92f);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(new OvershootInterpolator(0.4f))
                .start();
    }

    /// <summary>
    /// 通用弹入 + 平移：从下方 24dp 弹入。适用于页面内容。
    /// </summary>
    private void animateSlideUp(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(24));
        view.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .start();
    }

    /// <summary>
    /// 点击反馈：短暂缩小再恢复，模拟玻璃被按压。
    /// </summary>
    private void animatePressFeedback(View view) {
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(80)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new OvershootInterpolator(0.4f))
                        .start())
                .start();
    }

    /// <summary>
    /// 错位弹入：对 ViewGroup 的子元素依次执行弹入动画。
    /// staggerMs = 每个子元素之间的延迟毫秒数（推荐 40-60）。
    /// 每个子元素从起始透明度 0 和缩放 0.95 开始。
    /// </summary>
    private void animateStaggeredPopIn(ViewGroup container, int staggerMs) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            child.setAlpha(0f);
            child.setScaleX(0.95f);
            child.setScaleY(0.95f);
            child.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setStartDelay(i * staggerMs)
                    .setInterpolator(new OvershootInterpolator(0.35f))
                    .start();
        }
    }

    /// <summary>
    /// 弹窗进入动画：缩放+淡入，从 0.85 倍到 1.0 倍，带弹簧效果。
    /// 注意：将 dialog 引用改为局部变量接收后才能调用此方法。
    /// </summary>
    private void animateDialogEntrance(AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        decor.setAlpha(0f);
        decor.setScaleX(0.85f);
        decor.setScaleY(0.85f);
        decor.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(new OvershootInterpolator(0.5f))
                .start();
    }

    // ---------- 旧样式工具 ----------

    /// <summary>
    /// 玻璃胶囊背景：半透白底 + 白色边框。用于标签、小按钮等。
    /// </summary>
    private GradientDrawable glassCapsuleDrawable(int fillColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(8));
        gd.setColor(fillColor);
        gd.setStroke(dp(1), Color.parseColor("#B8FFFFFF"));
        return gd;
    }

    private GradientDrawable circleDrawable(int color, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, BORDER);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(18);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title);
    }

    private EditText addTextInput(LinearLayout root, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(Color.parseColor("#80526176"));
        // 玻璃内嵌输入框（内陷样式）
        GradientDrawable glassInputBg = new GradientDrawable();
        glassInputBg.setShape(GradientDrawable.RECTANGLE);
        glassInputBg.setCornerRadius(dp(10));
        glassInputBg.setColor(Color.parseColor("#A6FFFFFF"));
        glassInputBg.setStroke(dp(1), Color.parseColor("#D0FFFFFF"));
        input.setBackground(glassInputBg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(input, blockParams());
        return input;
    }
}
