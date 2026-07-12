# Click Assistant 更新日志

本文档用于记录项目中值得追踪的版本、功能、修复和重要变更，并与 GitHub Releases（GitHub 发布页面）保持同步。

## v0.16.0 - 2026-07-12 01:30

- 修复: 移动端 NullPointerException 闪退（"因应用自身空指针异常，造成闪退"）。`saveTaskFromInputs()` 在用户未打开编辑器页面时被 `executeRecentTask()` → `startTask()` 调用，触发 `taskNameInput.getText()` NPE。增加 EditText null 检查后从"最近任务"等入口执行不再闪退；`updateBottomNav` 也补全四个 nav item 联合 null 保护。
- 修复: 桌面端 SQLite Error 19 — `task_steps.after_delay_ms` NOT NULL 约束失败。新增 v1.3.0 数据库迁移移除废弃列；`DatabaseMigrator` 增加 `TryParseAlterTableDropColumn` 实现 DROP COLUMN 幂等（兼容新建库和老用户库）；25/25 测试通过。
- 新增: 全套 AI 生成图片素材（14 张 PNG），包括应用 Logo、5 个任务类型图标、4 个导航图标、4 个状态图标，统一蓝紫渐变玻璃质感几何风格，分别部署到 WPF `Resources/Images/` 和 Android `res/drawable/` 目录。
- 新增: WPF 端深色主题支持：App.xaml 增加 DarkTheme 资源字典（深色背景渐变、深色玻璃材质、深色文字色共 25+ Brush），MainWindow.xaml.cs 实现主题切换逻辑并持久化到 `app_settings` 表。
- 新增: Android 端深色主题支持：新建 `res/values/colors.xml`（35+ 颜色资源）和 `res/values-night/colors.xml`（自动跟随系统切换），新建 `themes.xml` 和 `values-night/themes.xml`，MainActivity.java 新增 `getThemeColor()` 动态颜色获取方法。
- 新增: 图片素材规格说明文档 `docs/design/ASSETS_SPECIFICATION.md`，记录每张图片的尺寸、色值、生成提示词和使用位置。
- 变更: 升级 `LIQUID_GLASS_DESIGN.md` 至 v2.0.0，补全色彩体系（深浅色令牌）、字体排版层级、响应式断点逻辑、组件五态规范（默认/悬停/按下/聚焦/禁用）和深浅色主题适配对比表。
- 变更: WPF 端 MainWindow.xaml 和 FloatingControlWindow.xaml 中所有主题敏感画笔从 StaticResource 改为 DynamicResource，支持运行时主题切换。
- 变更: WPF 端任务类型选择卡片和导航 Logo 从 emoji 文字替换为 PNG 图片素材。
- 变更: Android 端 styles.xml 更新为引用 `@color/` 资源方式，支持日夜模式跟随。
- 变更: Android 端 `glassDrawable()` 和 `pageBackgroundDrawable()` 改为通过 ContextCompat.getColor() 动态获取颜色。
- 变更: 项目版本提升至 v0.16.0，Android 端版本提升至 v0.6.1。
- docs: 新增 `docs/design/ASSETS_SPECIFICATION.md` 素材规格说明文档。

## v0.15.0 - 2026-07-12 01:00

- 变更: Android 与 WPF 统一采用液态玻璃视觉体系，覆盖背景、卡片、导航、按钮、输入控件、状态色、任务页、日志页与悬浮控制界面。
- 变更: Android 端版本提升至 `0.6.0`，系统栏、底部导航、取点标记和悬浮抽屉同步使用跨端设计令牌。
- 优化: 使用原生渐变、半透明亮边和平台阴影实现玻璃材质，保持 Android 7+ 兼容并避免静态背景图片在不同尺寸下失真。
- 修复: 桌面任务库底部操作栏因缺少 Grid 行定位而覆盖任务摘要，调整栅格结构并修复开始延迟提示与任务说明重叠。
- 修复: 桌面悬浮控制窗在空闲状态被 `Show()` 强制显示并遮挡主界面，改为仅在启动、运行或暂停期间显示。
- 修复: 桌面执行日志将英文枚举状态直接显示给用户，改为统一中文执行状态。
- 修复: Windows 发布脚本在 Windows PowerShell 5.1 下因 UTF-8 中文注释误解析而失败，改用兼容的 ASCII 注释。
- docs: 新增双端液态玻璃设计规范，并同步更新 README、版本信息与项目记录。

## v0.14.0 - 2026-07-11 22:30

### 移动端 Bug 修复

- 修复: 辅助功能检测在 Android 13+ 不工作，改用 AccessibilityManager.getEnabledAccessibilityServiceList() API 替代受限的 Settings.Secure 查询。
- 修复: 引导页循环陷阱导致用户永远被困在引导页，增加持久"引导已完成"标记；用户点击"暂不设置"或辅助功能已开启后永不再显示引导页。
- 修复: 标记覆盖层长按菜单永远弹不出，原因是 overlay Window 设置了 FLAG_NOT_TOUCHABLE 导致触摸事件不传递到 marker。
- 修复: AccessibilityNodeInfo 未回收导致系统资源泄漏，doDispatchText 和 typeTextCharByChar 中所有节点路径均增加 recycle() 调用，新增 recycleNode() 安全回收方法。
- 修复: 任务库卡片 CheckBox 点击冲突——setChecked 绑定阶段误触发监听器导致连锁重建，增加 null 监听器保护。

### 移动端 Phase 1 后续修复（N1~N6）

- 修复: CheckBox 点击冲突残留——Card 点击事件限到 textCol 列，CheckBox 加 setOnTouchListener 消费触控事件阻止冒泡。
- 新增: 步骤行增加 ↑↓ 排序按钮，调用已有 moveStep(index, direction) 方法，首/末步自动禁用。
- 修复: dispatchSwipe 缺少暂停/停止检查，增加 stopRequested/running/paused 检查和 onCompleted 回调的 stop 检查。
- 新增: 编辑器执行按钮根据 ClickAssistantAccessibilityService 运行状态联动启用/禁用，Service 新增 isRunning()/isPaused() 静态方法。
- 修复: editorDirty 切回编辑器时被重置——loadTasks() 编辑器页跳过 populateTaskFields()，不覆盖用户正在编辑的字段。
- 修复: 步骤编辑弹窗取点时被销毁——步骤行直接加"点选坐标"按钮（TAP/SWIPE 步骤），不依赖 AlertDialog 避免后台回收；addPickButton 新增 beforePick 回调参数。

### 移动端 UI 优化 Phase 2（P2-1~P2-6）

- 变更: 首页从纵向卡片列表改为 2×2 网格布局（辅助功能+Switch / 快速点击 / 任务库 / 执行日志），辅助功能状态卡片增加 Switch 开关直接跳系统设置。
- 变更: 新建任务从独立页面改为弹窗模式（单击/双击/长按/滑动），废弃 NEW_TASK 页面和相关枚举值。
- 变更: 悬浮按钮重写为左侧垂直抽屉，收起状态显示 ▶ 箭头，展开后显示 ▶开始/📍添加点/🗑删除点/📋查看/«收起，带展开/收起透明度动画。
- 变更: 任务库卡片和编辑器中 CheckBox 全面替换为 AndroidX SwitchCompat，增加 appcompat 依赖。
- 变更: 版本号从硬编码改为 BuildConfig.VERSION_NAME 动态读取。
- 新增: 首页"快速点击"功能——在预设位置一键执行单击，无需创建任务。

### 桌面端修复 Phase 4 前置（D1~D3）

- 修复: MainWindow.xaml.cs 两处直接 MessageBox.Show 改为通过 IDialogService.ShowError() 调用。
- 优化: Dispatcher.Invoke 同步阻塞改为 Dispatcher.InvokeAsync 异步调度。
- 移除: 废弃 DatabaseInitializer.cs，所有表创建和补列统一到 DatabaseMigrator 版本化迁移。

### 基础设施 Phase 5（I1~I4）

- 修复: DatabaseMigrator.EnsureMigrationsTableAsync 增加事务包裹（BeginTransactionAsync + CommitAsync），异常时 RollbackAsync。
- 安全: PRAGMA table_info 增加表名白名单校验（AllowedTableNames），仅允许访问已知表名，防止拼装 SQL 注入。
- 变更: 统一时间存储为 DateTime.UtcNow——ClickTask.cs(×3)、ClickTaskService.cs、ClickExecutionEngine.cs(×2)、SqliteAppSettingsRepository.cs 共 6 处；保留执行日志展示 HH:mm:ss 用 Now。
- test: 新增 DatabaseMigratorTests（3 测试：全新建表/幂等验证/旧库升级）、SqliteExecutionLogRepositoryTests（3 测试）、SqliteAppSettingsRepositoryTests（4 测试），共 10 个新测试。

### 桌面端架构优化 Phase 4（P4-1~P4-4）

- 重构: MainWindowViewModel 的 NotifyEditorDerivedProperties（曾一次触发 42 个属性通知）拆分为 9 个按属性组分组的通知方法，38 个调用点中 26 个改为精确组通知，剩余 12 个保留全量通知（SelectedTask 切换等正确场景）。
- 变更: ClickStep 实现 INotifyPropertyChanged，新增 RaisePropertyChanged() 方法；删除 RefreshSelectedStepListItem hack，WPF ItemsControl 增量刷新由 ClickStep.PropertyChanged 驱动。
- 重构: FormatMilliseconds 提取为共享 TimeFormattingHelper.FormatMilliseconds()，消除 MainWindowViewModel 和 StepSummaryConverter 两处重复实现。

### 领域层优化（D5~D7）

- 变更: ClickTask/ClickStep 的 Id 改为 init-only；RepeatCount/StartDelayMs/MouseClickCount/ClickIntervalMs/PressDurationMs/KeyPressCount/KeyIntervalMs/SwipeDurationMs/BeforeDelayMs 等 9 个数值属性 setter 即时校验不变量。
- 重构: ClickTask.Duplicate() 消除 25 行手工逐字段拷贝，步骤拷贝委托给 ClickStep.Copy(Guid newTaskId) 方法，新增属性时只需修改一处。
- 重构: ClickStep.ValidateForSave() 的连续 if-chain 改为字典分派 ActionValidators（Dictionary<InputActionType, Action<ClickStep>>），新增动作类型只需一行注册。

## v0.13.0 - 2026-07-11 19:40

- 修复: 移动端取点交互重构为长按拖动模式，解决取点与「完成」按钮点击冲突及取点覆盖层阻挡所有操作的问题。
  - 取点覆盖层由「滑动即跟踪 + 松手弹确认气泡」改为「长按 500ms 进入拖动定位 + 松手直接确认」
  - 移除「完成」按钮：进入取点模式时隐藏悬浮按钮，确认坐标后自动退出取点模式并恢复按钮
  - 新增取点覆盖层左上角 ✕ 取消按钮，避免误入取点后无法退出
  - PickerCursorView 新增高亮状态：长按进入拖动时圆圈边框加粗（5f→9f）并切换为高亮橙色
  - 删除不再使用的 showConfirmBubble 确认气泡逻辑及 PopupWindow 导入
- 重构: 移动端取点体验全面重构，引入常驻悬浮触发按钮 + 手指跟随取点光标 + 确认气泡 + 持久化标记覆盖层 + 执行脉冲动效。
  - 新增 FloatingTriggerButton.java：右下角常驻悬浮按钮（「取点」↔「完成」状态切换）
  - 新增 MarkerOverlayManager.java：独立 Window 管理标记圆圈，支持长按菜单（移动/调整/删除）和执行动效
  - 新增 PickerCursorView.java：独立取点光标类，按 TaskActionType 着色（蓝/绿/橙）
  - TaskActionType.java：新增 getColor() 颜色映射方法
  - TaskStep.java：新增 setFromScreenPoint() 便捷方法
  - TaskStore.java：新增 activeTaskId/activeStepId 的 SharedPreferences 读写方法
  - 重构 ClickAssistantAccessibilityService.java：移除一次性取点模式，改为常驻多步取点；执行时显示标记覆盖层 + 脉冲动效；completeTask/stop 不清除标记
  - MainActivity.java：取点流程改为"设定活动步骤"模式；保存任务后自动清除标记
- 修复: 移动端 dispatchTapSequence 使用 pressDurationMs 替代硬编码值，按压时长可配置。
- 优化: 移动端取点倒计时从 5 秒缩短为 3 秒，操作更快捷。
- 新增: 移动端取点覆盖层增加"测试点击"按钮，可验证当前光标位置。
- 优化: 移动端取点确认/取消后不再强制切回 App，改为 Toast 提示。
- 修复: 移动端个人中心页版本号从 v0.12.0 更新为 v0.13.0。

- 修复: “选择坐标”功能空白区域点击采集坐标并自动关闭，点击标记快速切换选中步骤，标记拖动实时更新坐标。
- 修复: CoordinatePickerWindow 关闭后正确重置坐标捕获状态，避免“选择坐标”按钮变为不可用。
- 修复: MainWindow.xaml 底部状态栏版本号仍显示 v0.12.0 的问题。
- 修复: Windows 端 .exe 启动时 SQLite 报错 schema_migrations 表缺少 name 列，DatabaseMigrator 自动兼容旧数据库结构。
- 修复: DatabaseMigrator 执行 ALTER TABLE ADD COLUMN 前自动跳过已存在的目标列，避免旧版初始化后的数据库升级失败。
- 变更: 桌面端 WPF 主界面全面重设计，采用左侧导航 + 蓝色主色（#2563EB）的现代 Dashboard 布局。
- 变更: 统一桌面端视觉资源：卡片样式、按钮模板、表单控件、状态徽章、导航按钮。
- 变更: 首页改为三列卡片入口，增加执行状态实时展示区。
- 变更: 任务类型选择页、任务库、执行日志页统一新风格白卡片 + 圆角设计。
- 变更: 鼠标点击编辑页优化为分组卡片布局，左右分栏更清晰。
- 变更: 悬浮控制窗升级为蓝色主色，圆角卡片设计，展开/收起流畅。
- 变更: 坐标选择器标记颜色统一为蓝色主色，区分步骤类型颜色。
- 变更: 移动端底部导航从 3 标签扩展为 4 标签（首页/任务库/日志/我的）。
- 新增: 移动端执行日志页增加统计徽章（总计/成功/失败）展示。
- 修复: 移动端 Java 文件中 FrameLayout.setGravity 编译错误和不正确的中文引号。
- 修复: 移动端缺失的 accessibilityStatusText 和 lastStatusText 字段声明。
- 变更: Windows 端自包含 .exe 发布到 dist/ClickAssistant-win-x64/ 目录。
- 变更: Android 端 debug APK 构建成功。

## v0.12.0 - 2026-07-11 18:13

- 新增: 移动端全新 UI 设计：浅色卡片风格、底部导航（首页/任务库/我的）、权限引导页、现代化首页入口、新建任务类型选择页、带搜索与筛选的任务库。
- 新增: 任务库支持开关快速启用/禁用任务、按状态筛选、右下角悬浮新建按钮。
- 新增: 个人中心页，聚合辅助功能设置、隐私说明、权限说明和版本信息。
- 新增: 步骤可视化标记覆盖层，支持在目标界面直接看到所有步骤序号圆点并拖动调整坐标。
- 新增: 步骤列表显示彩色序号圆圈，点击=蓝色、滑动=绿色、文本输入=橙色。
- 新增: 桌面端支持滑动步骤类型，可配置起点、终点坐标和滑动时长。
- 新增: 文本输入步骤支持"输入前自动点击目标位置获得焦点"。
- 新增: 版本化数据库迁移框架 `DatabaseMigrator`，按迁移版本号执行升级。
- 新增: Android CI 工作流（构建 + 单元测试 + APK 上传）。
- 新增: Android 基础单元测试，覆盖 TaskStep 校验和 JSON 往返序列化。
- 新增: GitHub Issue 模板（Bug/Feature）和 Pull Request 模板。
- 新增: 项目统一版本号文件 `VERSION`。
- 变更: 移除独立"等待"步骤类型，步骤间等待统一由下一步骤的 BeforeDelayMs 控制（Issue # 交互优化需求）。
- 变更: 移除 AfterDelayMs 字段，连点间隔改用 ClickIntervalMs，新增 PressDurationMs 按压时长。
- 变更: 移除步骤列表的"上移"/"下移"按钮，桌面端预留拖拽排序接口、移动端简化为纯序号显示。
- 变更: Android applyBackup 设为 false，缩小辅助功能事件类型范围（typeAllMask → typeWindowStateChanged|typeWindowContentChanged），
  accessibilityEventTypes notificationTimeout 从 100ms 调整为 500ms。
- 变更: 桌面端发布脚本支持读取 VERSION 文件并创建 ZIP 压缩包。
- 变更: 统一项目版本号：项目 v0.12.0、Android v0.5.0 (versionCode 5)、Windows v0.12.0。
- docs: 更新 CHANGELOG、WORKLOG 和 README，记录交互优化、工程治理与移动端 UI 重设计变更。
- docs: 补充 Android 无障碍服务权限说明注释。


## v0.11.0 - 2026-07-11 00:22

- 新增: Android 新建任务增加“点击任务 / 文本输入任务 / 点击与文本组合 / 空白任务”模板，组合模板按顺序创建点击目标输入框和输入文本步骤。
- 新增: Android 坐标拾取层显示可拖动的蓝色十字光标，用户移动后显式确认位置；滑动步骤支持依次确认起点和终点。
- 变更: Android 界面由单页长表单拆分为首页、任务库、任务编辑和执行日志四个简单页面，首页统一保留“新建任务 / 任务库 / 执行日志”入口。
- 优化: Android 任务编辑页只显示当前步骤类型需要的字段，等待和文本输入步骤不再展示无关坐标输入。
- 变更: Android 应用版本提升至 0.4.0，继续沿用现有多步骤执行、执行确认、日志和跨应用悬浮控制能力。
- docs: 更新项目说明、Android 使用说明和移动端规划，记录简化页面、组合任务与光标取点流程。

## v0.10.0 - 2026-07-10 23:47

- 新增: Android 任务库支持任务复制和启用/禁用，补齐桌面端可迁移的任务管理能力。
- 新增: Android 任务执行时显示跨应用辅助功能悬浮控制窗，支持展开/收起、返回应用、暂停、继续和停止。
- 新增: Android 执行引擎在启动时创建任务快照，运行期间修改配置不会影响本次执行。
- 变更: Android 端明确不提供键盘按键和组合键注入，保留点击与文本输入，并继续支持移动端滑动和等待动作。
- 修复: 文本输入找不到聚焦输入框或目标拒绝写入时立即停止并记录失败，不再继续推进后误报完成。
- 修复: 坐标拾取返回应用后强制使用最新持久化任务实例，避免界面仍显示旧坐标。
- 变更: Android 应用版本提升至 0.3.0，并生成用于 USB 真机测试的 debug APK。
- docs: 更新项目说明、Android 使用说明与移动端规划，记录迁移范围、构建结果和真机测试方向。

## v0.9.10 - 2026-07-10 20:24

- 新增: 移动端 Android 原型从单固定坐标点击扩展为多任务库，支持任务的创建、保存、删除与切换。
- 新增: 移动端步骤支持点击、滑动、等待与文本输入四种动作类型，可配置坐标、连点次数、滑动起终点、持续时长、逐字间隔与前后延迟。
- 新增: 移动端执行前确认弹窗，展示任务摘要、重复次数与步骤列表。
- 新增: 移动端执行日志本地保存，记录每次执行的状态、原因与时间。
- 新增: 移动端执行控制支持启动、暂停、继续与停止，对应桌面端执行引擎状态机。
- 新增: 移动端坐标拾取支持点击位置与滑动起终点，使用透明取点层回写步骤坐标。
- 变更: 移动端本地存储由单任务 SharedPreferences 升级为多任务 JSON 持久化，并兼容旧版单任务数据迁移。
- 变更: 移动端版本号提升至 0.2.0，对应任务模型与执行能力的完善。
- docs: 更新移动端 README 与 Android 规划文档，标注原型已实现的能力与后续产品化方向。

## v0.9.9 - 2026-07-10 19:24

- 新增: 鼠标点击任务支持多个点击位置按列表顺序执行，每个位置可单独配置坐标、点击次数、点击间隔和点击方式。
- 变更: 鼠标点击编辑页改为“位置列表 + 当前选中位置参数 + 坐标采集”的极简三栏结构，并将任务级重复次数表述为“执行轮数”。
- 变更: 扩展 SQLite `task_steps` 表结构，新增 `mouse_click_count` 字段，并支持旧数据库启动时自动补列。
- test: 补充鼠标点击次数校验、SQLite 持久化和执行引擎多次点击测试。

## v0.9.8 - 2026-07-10 18:56

- 变更: 鼠标坐标采集改为全屏透明选择层，虚拟箭头和十字线跟随真实鼠标，左键确认坐标，`Esc` 取消。
- docs: 更新 README，说明新的坐标选择层交互方式。

## v0.9.7 - 2026-07-10 18:43

- 修复: 鼠标坐标采集改为点击按钮后延迟 2 秒自动捕获，避免直接读取到捕获按钮本身的位置。
- 修复: 新建键盘按键任务后自动进入“步骤设置”并选中键盘步骤，按键捕获框点击后可直接监听键盘输入。

## v0.9.6 - 2026-07-10 18:25

- 变更: 桌面端首页改为极简入口页，仅展示 `CLICK ASSISTANT` 标识、“点击与输入助手”说明和“新建任务 / 任务库 / 执行日志”三个入口。
- 变更: 移除主窗口常驻左侧栏，改为在新建任务、任务库、执行日志和鼠标点击编辑页提供返回首页入口。

## v0.9.5 - 2026-07-10 13:07

- 变更: 新建键盘按键步骤不再默认填入 `A`，而是显示“未设置”，要求用户通过按键捕获框自行按键确定目标。
- 变更: 任务保存允许键盘按键步骤暂时未设置按键，执行启用步骤前仍会校验并阻止未设置按键的任务启动。
- docs: 更新 README 和桌面端界面改版设计，说明键盘按键步骤不再使用默认 `A`。

## v0.9.4 - 2026-07-10 13:00

- 优化: 任务库步骤列表改为显示步骤摘要，键盘按键步骤会直接显示目标按键、连按次数和间隔。
- 变更: 任务库左侧增加“删除选中任务”入口，让删除已保存任务功能在任务列表旁直接可见。
- docs: 更新 README 和桌面端界面改版设计，说明步骤摘要和已保存任务删除入口。

## v0.9.3 - 2026-07-10 12:52

- 变更: 任务库右侧改为“基础信息 / 步骤设置 / 执行确认”三段式标签页，降低已保存任务编辑时的同屏复杂度。
- 优化: 键盘按键步骤改为按键捕获面板，显示当前按键和正在监听、已捕获、暂不支持等反馈。
- 变更: 执行确认摘要改为面向全部步骤类型，展示开始延迟、启用步骤数量和重复次数。
- docs: 更新 README 和桌面端界面改版设计，说明任务库标签页和键盘按键捕获反馈。

## v0.9.2 - 2026-07-10 12:33

- 新增: 任务库增加通用步骤配置区，已保存任务可直接编辑步骤类型、等待时间、鼠标坐标、键盘按键、组合键和文本内容。
- 新增: 键盘按键步骤支持点击按键输入框后直接按下键盘按键，用于设置连按目标键。
- 新增: 鼠标点击执行前显示置顶虚拟鼠标箭头和扩散动效，帮助确认真实点击位置。
- 变更: 基础悬浮控制窗取消主窗口 Owner 绑定，主窗口最小化后仍保持独立置顶悬浮。
- docs: 补充开始延迟、点击间隔、键盘按键捕获和点击位置提示说明。

## v0.9.1 - 2026-07-10 12:17

- 变更: 桌面端启动后默认显示三入口首页，仅保留“新建任务”“任务库”和“执行日志”入口，点击后在主内容区切换到对应页面。
- 新增: 增加独立执行日志页，集中展示最近执行结果和本次运行记录。
- docs: 更新 README、桌面端界面改版设计和术语规范，统一首页入口与执行日志命名。

## v0.9.0 - 2026-07-10 01:29

- 新增: 桌面端改为多页面任务中心，包含首页、新建任务类型选择页、任务库页和鼠标点击任务编辑页。
- 新增: 鼠标点击任务编辑页支持任务名称、说明、坐标、重复次数、开始延迟、点击间隔、点击方式、坐标捕获和测试点击。
- 新增: 任务执行时显示基础悬浮控制窗，支持运行状态展示、展开/收起、暂停、继续和停止。
- 变更: 暂停后允许编辑并保存任务配置，同时提示本次运行仍以启动时配置为准，需要停止后重新执行才会生效。
- 修复: 执行引擎停止请求与取消流程发生竞态时，可能未发布 `Stopped` 状态的问题。
- docs: 新增 `docs/design/DESKTOP_UI_REDESIGN.md`，并在 `STYLEGUIDE.md` 中补充桌面端界面术语规范。

## v0.8.0 - 2026-07-10 00:55

- 新增: Android 原型支持通过辅助功能覆盖层点选屏幕位置，自动保存点击坐标并回到应用。
- 变更: Android 原型任务配置补充坐标、点击次数、开始延迟和点击间隔的输入引导，降低手动填写成本。

## v0.7.0 - 2026-07-09 18:01

- 新增: 创建 `mobile/android/` Android 可行性原型工程，源码层面覆盖辅助功能授权入口、固定坐标点击、停止入口和本地任务保存。
- 新增: 补充 `docs/planning/ANDROID_REQUIREMENTS.md`，记录 Android 原型目标、功能需求、安全边界和验收标准。
- 新增: 补充 `docs/planning/ANDROID_PROTOTYPE.md`，记录 Android 首屏、授权引导、任务保存、执行、停止和最近状态的低保真流程。
- 变更: 扩展 `.gitignore`，忽略 Android 本地构建缓存、SDK 路径配置和安装包产物。
- docs: 更新 `README.md`、移动端延伸规划和编码实施计划，记录 Android 原型工程、当前工具链缺口和后续真机验证步骤。

## v0.6.2 - 2026-07-09 17:53

- docs: 新增 `docs/planning/MOBILE_EXTENSION_PLAN.md`，记录移动端 Android-first 路线、iOS 边界、Android 原型范围、工程结构建议和安全合规要求。
- docs: 更新 `README.md`、需求规格和编码实施计划，将下一阶段调整为 Windows 发布完善与 Android 移动端可行性原型双线推进。

## v0.6.1 - 2026-07-09 13:26

- 新增: 添加 `tools/publish-windows.ps1` 发布脚本，支持生成 Windows `win-x64` 发布目录和可执行程序。
- docs: 新增 `docs/development/RELEASE.md`，记录部署与上线思路、发布前检查、发布目录生成方式、数据迁移说明和后续发布计划。
- docs: 更新 `README.md`、测试说明和编码实施计划，记录发布脚本、发布文档入口和下一阶段安装包/GitHub Releases 方向。

## v0.6.0 - 2026-07-09 13:15

- 新增: 扩展输入动作类型，新增组合键步骤和文本输入步骤，支持在任务中混合配置鼠标、单键、组合键和文本输入。
- 新增: Windows 键盘输入服务改用 `SendInput`，支持单键连按、组合键触发和 Unicode 文本输入。
- 新增: 扩展 WPF 步骤配置表格，支持添加组合键步骤、文本输入步骤，并配置组合键内容和文本内容。
- 变更: 扩展 SQLite `task_steps` 表结构，新增 `shortcut_keys` 和 `text_content` 字段，并支持旧数据库启动时自动补列。
- test: 补充领域模型校验、执行引擎键盘动作分发和 SQLite 仓储测试，覆盖组合键与文本输入。
- docs: 更新 README、需求规格、架构设计、详细设计、原型、UI/UX、测试说明和编码实施计划，记录组合键与文本输入能力。

## v0.5.2 - 2026-07-09 13:04

- 新增: 建立 GitHub Actions 基础 CI，使用 Windows runner 执行 `dotnet restore`、`dotnet build` 和 `dotnet test`。
- docs: 更新 `README.md`、测试说明和编码实施计划，记录 CI 工作流路径、触发条件和检查项。

## v0.5.1 - 2026-07-09 12:58

- docs: 新增 `docs/development/TESTING.md`，集中记录测试目标、测试类型、当前覆盖范围、运行方式、测试命名规范和后续补测清单。
- docs: 更新 `README.md` 和编码实施计划，将测试说明收敛为入口说明并指向专门测试文档。

## v0.5.0 - 2026-07-09 12:44

- 新增: 初始化本地 Git 仓库，并创建 `feature/quality-baseline` 开发分支，恢复正常版本管理基线。
- 新增: 创建 `tests/ClickAssistant.Tests/` xUnit 测试工程，覆盖领域模型校验、执行引擎启动/暂停/继续/停止状态流转，以及 SQLite 仓储保存、读取和删除。
- 新增: 启动任务前显示执行前安全确认，提示重复次数、启用步骤、开始延迟和停止方式，降低真实点击或键盘输入误操作风险。
- 新增: 支持在执行面板中自定义全局停止快捷键，并将设置保存到 SQLite `app_settings` 表。
- 变更: 任务启动中、运行中和暂停时锁定任务配置区，并展示更醒目的执行安全提示。
- 变更: SQLite 连接工厂支持传入自定义数据库路径，便于测试隔离和后续扩展。
- docs: 更新 `README.md` 和编码实施计划，记录测试命令、安全确认、快捷键自定义和后续工作方向。

## v0.4.0 - 2026-07-09 01:18

- 新增: 扩展步骤模型，新增输入动作类型，支持在同一任务中混合配置鼠标点击步骤和键盘按键步骤。
- 新增: 实现 Windows 键盘输入服务，支持常用单键连按，包括字母、数字、功能键、回车、空格、方向键、删除和退格等。
- 新增: 扩展 WPF 步骤表格，支持添加键盘步骤，并配置按键名称、连按次数和连按间隔。
- 变更: 扩展 SQLite `task_steps` 表结构，新增动作类型和键盘参数字段，并支持旧数据库启动时自动补列。
- docs: 更新 `README.md`、需求规格、架构设计、详细设计和编码实施计划，记录键盘连按能力和后续组合键/文本输入方向。

## v0.3.0 - 2026-07-09 01:00

- 新增: 创建 `ClickAssistant.slnx` 和 C# 分层工程，包含 WPF 界面层、领域层、应用服务层和基础设施层。
- 新增: 实现点击任务管理、步骤配置、鼠标坐标捕获、启动、暂停、继续、停止和执行日志展示的首版界面。
- 新增: 接入 SQLite 本地存储，支持保存任务、步骤和执行日志，数据库默认位于 `%LOCALAPPDATA%\ClickAssistant\clickassistant.db`。
- 新增: 实现 Windows 鼠标坐标读取和点击执行适配，首版使用屏幕绝对坐标。
- 新增: 接入全局停止快捷键 `Ctrl + Alt + S`，支持窗口失焦时请求立即停止当前任务。
- 安全: 显式升级 `SQLitePCLRaw.bundle_e_sqlite3` 至 `3.0.3`，消除 `SQLitePCLRaw.lib.e_sqlite3 2.1.11` 高严重性漏洞构建告警。
- 变更: 新增 `.gitignore` 和 `.editorconfig`，规范构建输出忽略规则与代码格式。
- docs: 更新 `README.md` 和 `docs/development/IMPLEMENTATION_PLAN.md`，记录当前实现阶段、工程结构、构建命令、运行命令和后续工作。

## v0.2.7 - 2026-07-09 00:32

- docs: 更新 `docs/development/IMPLEMENTATION_PLAN.md`，记录 .NET SDK `10.0.301` 已安装、WPF 模板已验证，以及当前终端需要重启以刷新 PATH。

## v0.2.6 - 2026-07-09 00:22

- docs: 新增 `docs/development/IMPLEMENTATION_PLAN.md`，记录编码阶段实施顺序、工程初始化、测试策略和 CI/CD 计划。
- docs: 更新 `docs/architecture/ARCHITECTURE.md`，明确使用本机稳定 .NET SDK、WPF 和轻量手写 SQL，不使用 EF Core。
- docs: 更新 `STYLEGUIDE.md` 和 `README.md`，补充 `docs/development/` 目录用途和编码实施计划入口。

## v0.2.5 - 2026-07-09 00:14

- docs: 新增 `docs/architecture/ARCHITECTURE.md`，记录 Windows 首版、C#、WPF、SQLite、系统分层、模块职责和接口方向。
- docs: 新增 `docs/design/DETAILED_DESIGN.md`，记录领域模型、执行引擎、坐标采集、快捷键、数据库表和异常处理设计。
- docs: 新增 `docs/design/UI_UX_DESIGN.md`，记录优雅简洁的透明毛玻璃视觉方向、页面布局、状态设计和交互约束。
- docs: 更新 `docs/planning/REQUIREMENTS.md`，确认首版只支持 Windows、屏幕绝对坐标、全局快捷键、SQLite、不支持多显示器和导入导出。
- docs: 更新 `README.md`，补充设计阶段文档清单和下一步工程初始化建议。

## v0.2.4 - 2026-07-09 00:07

- docs: 调整项目文档目录结构，将需求规格说明和低保真原型蓝图迁入 `docs/planning/`。
- docs: 更新 `STYLEGUIDE.md` 的目录结构规范，明确根目录只保留入口文档和治理文档。
- docs: 更新 `README.md` 中的文档路径和目录结构说明。

## v0.2.3 - 2026-07-09 00:03

- docs: 新增 `docs/planning/REQUIREMENTS.md`，记录 Click Assistant 的项目目标、范围、功能需求、非功能需求和 MVP 验收标准。
- docs: 新增 `docs/planning/PROTOTYPE.md`，记录 Click Assistant 的低保真页面蓝图和核心交互流程。
- docs: 更新 `README.md`，补充当前项目阶段、文档清单和下一步工作建议。

## v0.2.2 - 2026-07-08 23:59

- docs: 完善 `RULE.md`，新增文档版本信息、规则修改记录要求、分支与提交规范、Issue 与 Pull Request 规范、版本号规范和规则更新要求。
- docs: 明确 `RULE.md` 对 `STYLEGUIDE.md`、`WORKLOG.md` 和 `CHANGELOG.md` 的引用关系与执行要求。

## v0.2.1 - 2026-07-08 23:57

- docs: 完善 `STYLEGUIDE.md`，新增文档版本信息、提交规范、分支命名规范、代码格式规范和目录结构规范。
- docs: 明确 `WORKLOG.md` 的时间含义和提交前同步要求，以及 `CHANGELOG.md` 的发布前同步要求。

## v0.2.0 - 2026-07-08 23:50

- docs: 在 `STYLEGUIDE.md` 中新增工作日志和更新日志规范。
- docs: 将 `WORKLOG.md` 调整为精确到分钟的倒序记录格式。
- docs: 将 `CHANGELOG.md` 调整为语义化版本记录格式。

## v0.1.0 - 2026-07-08 23:50

- docs: 初始化 `RULE.md`，明确项目执行规则、风格规范、项目理解、工作记录和更新日志要求。
- docs: 新增 `WORKLOG.md`，用于记录每次工作完成的内容。
