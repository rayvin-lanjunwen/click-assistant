package com.clickassistant.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.List;
import java.util.Locale;

/// <summary>
/// 主界面，采用底部导航 + 现代浅色卡片式 UI。
/// 包含权限引导页、首页、新建任务页、任务库、执行日志、个人中心和任务编辑器。
/// </summary>
public final class MainActivity extends Activity {
    private enum Page {
        ONBOARDING,   // 权限引导页
        HOME,         // 首页
        NEW_TASK,     // 新建任务类型选择
        LIBRARY,      // 任务库
        LOGS,         // 执行日志
        PROFILE,      // 个人中心
        EDITOR        // 任务编辑
    }

    // iOS 风格浅色配色
    private static final int BG = Color.parseColor("#F5F7FA");
    private static final int CARD_BG = Color.WHITE;
    private static final int PRIMARY = Color.parseColor("#2563EB");
    private static final int PRIMARY_LIGHT = Color.parseColor("#DBEAFE");
    private static final int TEXT_PRIMARY = Color.parseColor("#1F2937");
    private static final int TEXT_SECONDARY = Color.parseColor("#6B7280");
    private static final int BORDER = Color.parseColor("#E5E7EB");
    private static final int SUCCESS = Color.parseColor("#22C55E");
    private static final int SUCCESS_LIGHT = Color.parseColor("#DCFCE7");
    private static final int ORANGE = Color.parseColor("#F97316");
    private static final int ORANGE_LIGHT = Color.parseColor("#FFEDD5");
    private static final int PURPLE = Color.parseColor("#8B5CF6");
    private static final int PURPLE_LIGHT = Color.parseColor("#F3E8FF");
    private static final int CYAN = Color.parseColor("#06B6D4");
    private static final int CYAN_LIGHT = Color.parseColor("#CFFAFE");
    private static final int RED = Color.parseColor("#EF4444");
    private static final int RED_LIGHT = Color.parseColor("#FEE2E2");
    private static final int GRAY_BG = Color.parseColor("#F3F4F6");

    private Page currentPage = Page.ONBOARDING;
    private String currentFilter = "全部";

    private LinearLayout bottomNav;
    private TextView homeNavItem;
    private TextView libraryNavItem;
    private TextView logsNavItem;
    private TextView profileNavItem;

    private EditText taskNameInput;
    private EditText taskDescInput;
    private CheckBox taskEnabledBox;
    private EditText repeatCountInput;
    private EditText startDelayInput;
    private LinearLayout stepsContainer;
    private LinearLayout logContainer;
    private EditText librarySearchInput;
    private LinearLayout libraryTaskList;
    private LinearLayout filterContainer;

    private TextView accessibilityStatusText;
    private TextView lastStatusText;

    private List<ClickTask> tasks = new ArrayList<>();
    private ClickTask selectedTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPage(Page.ONBOARDING);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasks();
        refreshPageContent();
    }

    @Override
    public void onBackPressed() {
        if (currentPage == Page.EDITOR) {
            showPage(Page.LIBRARY);
        } else if (currentPage == Page.NEW_TASK) {
            showPage(Page.HOME);
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
        // 如果已开启辅助功能，跳过引导页
        if (page == Page.ONBOARDING && isAccessibilityEnabled()) {
            currentPage = Page.HOME;
        }
        setContentView(buildContentView());
        loadTasks();
        refreshPageContent();
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
        updateBottomNav();
    }

    private View buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        View content = buildPageContent(currentPage);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 编辑器/新建任务页/引导页不显示底部导航
        if (currentPage != Page.EDITOR && currentPage != Page.NEW_TASK && currentPage != Page.ONBOARDING) {
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
            case NEW_TASK:
                return buildNewTaskPage();
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
        nav.setBackgroundColor(CARD_BG);
        nav.setElevation(dp(8));
        nav.setPadding(dp(8), dp(4), dp(8), dp(4));

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
        item.setOnClickListener(v -> showPage(targetPage));
        return item;
    }

    private void updateBottomNav() {
        if (homeNavItem == null) return;
        homeNavItem.setTextColor(currentPage == Page.HOME ? PRIMARY : TEXT_SECONDARY);
        libraryNavItem.setTextColor(currentPage == Page.LIBRARY ? PRIMARY : TEXT_SECONDARY);
        logsNavItem.setTextColor(currentPage == Page.LOGS ? PRIMARY : TEXT_SECONDARY);
        profileNavItem.setTextColor(currentPage == Page.PROFILE ? PRIMARY : TEXT_SECONDARY);
    }

    // ---------- 权限引导页 ----------

    private ScrollView buildOnboardingPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

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
        skipText.setOnClickListener(v -> showPage(Page.HOME));
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
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        // 顶部标题区
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(16), 0, dp(8));
        root.addView(header);

        TextView title = new TextView(this);
        title.setText("Click Assistant");
        title.setTextSize(28);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("点击与输入助手");
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_SECONDARY);
        header.addView(subtitle);

        // 辅助功能状态卡片
        LinearLayout statusCard = createCard(dp(16));
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setOnClickListener(v -> showPage(Page.PROFILE));

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        iconFrame.setBackground(circleDrawable(SUCCESS_LIGHT, 0));
        TextView iconView = new TextView(this);
        iconView.setText("✅");
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
        accessibilityStatusText.setText("已开启");
        accessibilityStatusText.setTextSize(18);
        accessibilityStatusText.setTextColor(SUCCESS);
        accessibilityStatusText.setTypeface(null, Typeface.BOLD);
        statusTextCol.addView(accessibilityStatusText);

        statusCard.addView(statusTextCol);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(TEXT_SECONDARY);
        statusCard.addView(arrow);

        root.addView(statusCard, blockParams());

        // 快捷入口卡片
        TextView quickTitle = new TextView(this);
        quickTitle.setText("快捷入口");
        quickTitle.setTextSize(18);
        quickTitle.setTextColor(TEXT_PRIMARY);
        quickTitle.setTypeface(null, Typeface.BOLD);
        quickTitle.setPadding(0, dp(24), 0, dp(12));
        root.addView(quickTitle);

        addHomeCard(root, "➕", "新建任务", "创建自动化任务", PRIMARY, PRIMARY_LIGHT, v -> showPage(Page.NEW_TASK));
        addHomeCard(root, "📂", "任务库", "管理与运行任务", PURPLE, PURPLE_LIGHT, v -> showPage(Page.LIBRARY));
        addHomeCard(root, "📋", "执行日志", "查看任务执行记录", ORANGE, ORANGE_LIGHT, v -> showPage(Page.LOGS));

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
        lastStatusText = new TextView(this);
        lastStatusText.setText("暂无执行记录");
        lastStatusText.setTextSize(14);
        lastStatusText.setTextColor(TEXT_SECONDARY);
        lastStatusText.setLineSpacing(0, 1.4f);
        recentCard.addView(lastStatusText);
        root.addView(recentCard, blockParams());

        scroll.addView(root);
        return scroll;
    }

    private void addHomeCard(LinearLayout root, String icon, String title, String desc, int color, int bgColor, View.OnClickListener listener) {
        LinearLayout card = createCard(dp(12));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOnClickListener(listener);
        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        iconFrame.setBackground(circleDrawable(bgColor, 0));
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconFp1 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconFp1.gravity = Gravity.CENTER;
        iconFrame.addView(iconView, iconFp1);
        card.addView(iconFrame);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(16), 0, 0, 0);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTypeface(null, Typeface.BOLD);
        textCol.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(13);
        descView.setTextColor(TEXT_SECONDARY);
        descView.setLineSpacing(0, 1.3f);
        textCol.addView(descView);

        card.addView(textCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(TEXT_SECONDARY);
        card.addView(arrow);

        root.addView(card);
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
    }

    private void refreshHomeStatus() {
        refreshStatus();
    }

    // ---------- 新建任务页 ----------

    private ScrollView buildNewTaskPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        addPageHeader(root, "新建任务", "选择任务类型开始创建");

        addTemplateCard(root, "🖱️", "点击任务", "在指定位置执行\n点击操作", PRIMARY, PRIMARY_LIGHT, 0);
        addTemplateCard(root, "⌨️", "文本输入任务", "在指定位置输入\n文本内容", PURPLE, PURPLE_LIGHT, 1);
        addTemplateCard(root, "📝", "点击与文本组合", "组合点击与文本输入\n的自动化操作", ORANGE, ORANGE_LIGHT, 2);
        addTemplateCard(root, "📄", "空白任务", "从空白开始创建\n自定义任务流程", CYAN, CYAN_LIGHT, 3);

        scroll.addView(root);
        return scroll;
    }

    private void addTemplateCard(LinearLayout root, String icon, String title, String desc, int color, int bgColor, int templateIndex) {
        LinearLayout card = createCard(dp(16));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(130));
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.setOnClickListener(v -> {
            createTaskTemplate(templateIndex);
        });

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        iconFrame.setBackground(circleDrawable(bgColor, 0));
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(28);
        iconView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconFp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconFp2.gravity = Gravity.CENTER;
        iconFrame.addView(iconView, iconFp2);
        card.addView(iconFrame);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(12), 0, dp(4));
        card.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(13);
        descView.setTextColor(TEXT_SECONDARY);
        descView.setGravity(Gravity.CENTER);
        descView.setLineSpacing(0, 1.3f);
        card.addView(descView);

        root.addView(card);
    }

    // ---------- 任务库页 ----------

    private View buildLibraryPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);

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
        fab.setElevation(dp(6));
        fab.setOnClickListener(v -> showPage(Page.NEW_TASK));

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
        String[] filters = {"全部", "运行中", "已完成", "已停止"};
        for (String filter : filters) {
            TextView tab = new TextView(this);
            tab.setText(filter);
            tab.setTextSize(14);
            tab.setPadding(dp(16), dp(8), dp(16), dp(8));
            boolean active = filter.equals(currentFilter);
            tab.setTextColor(active ? Color.WHITE : TEXT_SECONDARY);
            tab.setBackground(active ? roundedDrawable(PRIMARY, dp(16)) : roundedDrawable(GRAY_BG, dp(16)));
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
            TextView empty = new TextView(this);
            empty.setText("暂无任务，点击右下角 + 创建");
            empty.setTextColor(TEXT_SECONDARY);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            libraryTaskList.addView(empty);
            return;
        }

        for (ClickTask task : filtered) {
            libraryTaskList.addView(buildLibraryTaskCard(task));
        }
    }

    private boolean matchesFilter(ClickTask task) {
        switch (currentFilter) {
            case "运行中":
                return task.isEnabled();
            case "已完成":
            case "已停止":
                return !task.isEnabled();
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
        card.setOnClickListener(v -> {
            selectedTask = task;
            showPage(Page.EDITOR);
        });

        // 任务类型图标
        String icon = getTaskTypeIcon(task);
        int iconColor = getTaskTypeColor(task);
        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        iconFrame.setBackground(circleDrawable(Color.argb(20, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)), 0));
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconFp3 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconFp3.gravity = Gravity.CENTER;
        iconFrame.addView(iconView, iconFp3);
        card.addView(iconFrame);

        // 文本信息
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(16), 0, 0, 0);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

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

        TextView meta = new TextView(this);
        int stepCount = task.getSteps().size();
        meta.setText(String.format(Locale.ROOT, "%d 个步骤 · 重复 %d 次", stepCount, task.getRepeatCount()));
        meta.setTextSize(12);
        meta.setTextColor(TEXT_SECONDARY);
        meta.setPadding(0, dp(4), 0, 0);
        textCol.addView(meta);

        card.addView(textCol);

        // 开关
        CheckBox toggle = new CheckBox(this);
        toggle.setChecked(task.isEnabled());
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            task.setEnabled(isChecked);
            TaskStore.upsertTask(this, task);
            refreshLibrary();
        });
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
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView title = new TextView(this);
        title.setText("我的");
        title.setTextSize(28);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
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
        versionText.setText("Click Assistant 移动端 v0.5.0\n项目版本 v0.13.0");
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
        arrow.setTextSize(28);
        arrow.setTextColor(TEXT_SECONDARY);
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
        scroll.setBackgroundColor(BG);

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
            ExecutionLogStore.clear(this);
            renderLog();
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
        badge.setBackground(roundedDrawable(Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)), dp(10)));
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
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(32));

        // 返回按钮
        TextView backButton = new TextView(this);
        backButton.setText("‹ 返回任务库");
        backButton.setTextSize(16);
        backButton.setTextColor(PRIMARY);
        backButton.setPadding(0, dp(8), 0, dp(8));
        backButton.setOnClickListener(v -> showPage(Page.LIBRARY));
        root.addView(backButton);

        TextView title = new TextView(this);
        title.setText(selectedTask != null ? selectedTask.getName() : "编辑任务");
        title.setTextSize(24);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
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
        taskEnabledBox = new CheckBox(this);
        taskEnabledBox.setText("启用该任务");
        taskEnabledBox.setTextColor(TEXT_PRIMARY);
        basicCard.addView(taskEnabledBox);
        repeatCountInput = addTextInput(basicCard, "重复次数", InputType.TYPE_CLASS_NUMBER);
        startDelayInput = addTextInput(basicCard, "开始延迟（毫秒）", InputType.TYPE_CLASS_NUMBER);

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

        Button addStepButton = new Button(this);
        addStepButton.setText("添加步骤");
        addStepButton.setTextColor(PRIMARY);
        addStepButton.setAllCaps(false);
        addStepButton.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(12)));
        addStepButton.setPadding(dp(16), dp(14), dp(16), dp(14));
        addStepButton.setOnClickListener(v -> showAddStepDialog());
        stepsCard.addView(addStepButton, blockParams());

        root.addView(stepsCard, blockParams());

        // 执行控制卡片
        LinearLayout controlCard = createCard(dp(12));
        controlCard.setOrientation(LinearLayout.VERTICAL);
        controlCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        addSectionTitle(controlCard, "执行控制");
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(8), 0, dp(8));

        Button startButton = createActionButton("开始执行", PRIMARY, v -> startTask());
        Button pauseButton = createActionButton("暂停", TEXT_PRIMARY, v -> pauseTask());
        Button resumeButton = createActionButton("继续", TEXT_PRIMARY, v -> resumeTask());
        Button stopButton = createActionButton("停止", RED, v -> stopTask());

        actionRow.addView(startButton, weightParams());
        actionRow.addView(pauseButton, weightParams());
        actionRow.addView(resumeButton, weightParams());
        actionRow.addView(stopButton, weightParams());
        controlCard.addView(actionRow);

        TextView safetyHint = new TextView(this);
        safetyHint.setText("请先在安全界面验证坐标与步骤，避免误点登录、支付、权限或系统安全界面。");
        safetyHint.setTextColor(TEXT_SECONDARY);
        safetyHint.setTextSize(13);
        safetyHint.setPadding(0, dp(12), 0, 0);
        safetyHint.setLineSpacing(0, 1.4f);
        controlCard.addView(safetyHint);

        root.addView(controlCard, blockParams());

        scroll.addView(root);
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

    private void renderSteps() {
        if (stepsContainer == null) return;
        stepsContainer.removeAllViews();
        if (selectedTask == null) return;

        List<TaskStep> steps = selectedTask.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            stepsContainer.addView(buildStepRow(steps.get(i), i, steps.size()), blockParams());
        }

        if (steps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无步骤，点击【添加步骤】创建");
            empty.setTextColor(TEXT_SECONDARY);
            empty.setPadding(0, dp(8), 0, dp(8));
            stepsContainer.addView(empty);
        }
    }

    private void renderLog() {
        if (logContainer == null) return;
        logContainer.removeAllViews();
        List<ExecutionLogEntry> entries = ExecutionLogStore.loadLog(this);
        Collections.reverse(entries);

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无执行记录");
            empty.setTextColor(TEXT_SECONDARY);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            empty.setGravity(Gravity.CENTER);
            logContainer.addView(empty);
            return;
        }

        int limit = Math.min(entries.size(), 30);
        for (int i = 0; i < limit; i++) {
            ExecutionLogEntry entry = entries.get(i);
            LinearLayout card = createCard(dp(12));
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams params = blockParams();
            params.setMargins(0, 0, 0, dp(10));
            card.setLayoutParams(params);

            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);
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

            logContainer.addView(card);
        }
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
            case SWIPE:
                return Color.parseColor("#16A064");
            case TEXT_INPUT:
                return ORANGE;
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
        container.setPadding(dp(24), dp(16), dp(24), dp(16));
        scroll.addView(container);

        final EditText nameInput = addEditorInput(container, "步骤名称", InputType.TYPE_CLASS_TEXT, step.getName());
        final CheckBox enabledBox = new CheckBox(this);
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
            autoFocusBox.setTextColor(TEXT_PRIMARY);
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
        pickButton.setTextColor(PRIMARY);
        pickButton.setAllCaps(false);
        pickButton.setBackground(roundedDrawable(PRIMARY_LIGHT, dp(8)));
        pickButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        pickButton.setOnClickListener(view -> startCoordinatePickForStep(step, mode));
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
            selectedTask.setName(taskNameInput.getText().toString().trim());
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
                    TaskStore.saveActiveTaskId(this, task.getId());
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
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        ComponentName expectedComponent = new ComponentName(this, ClickAssistantAccessibilityService.class);
        String normalized = enabledServices.toLowerCase(Locale.ROOT);
        return normalized.contains(expectedComponent.flattenToString().toLowerCase(Locale.ROOT))
                || normalized.contains(expectedComponent.flattenToShortString().toLowerCase(Locale.ROOT));
    }

    // ---------- UI 样式工具 ----------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout createCard(int radius) {
        LinearLayout card = new LinearLayout(this);
        card.setBackground(roundedDrawable(CARD_BG, radius));
        card.setElevation(dp(2));
        return card;
    }

    private GradientDrawable roundedDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
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
        input.setHintTextColor(TEXT_SECONDARY);
        input.setPadding(0, dp(14), 0, dp(14));
        input.setBackground(null);
        root.addView(input, blockParams());
        return input;
    }
}
