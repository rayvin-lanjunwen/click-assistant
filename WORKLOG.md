# Click Assistant 工作记录

本文档用于记录每次工作完成的主要内容，方便后续追踪项目进展。

## 2026-07-12

- 2026-07-12 02:15 修复桌面端 SQLite Error 19：DatabaseMigrator v1.3.0 迁移 DROP COLUMN after_delay_ms；v1.0.0 初始建表移除该列；新增 TryParseAlterTableDropColumn 实现 DROP COLUMN 幂等（已存在则跳过）；25/25 测试通过；同步清理 DatabaseMigratorTests 测试 fixture；记录到 ERRORLOG.md #12。
- 2026-07-12 02:00 项目文件审查与清理：修复 CHANGELOG.md v0.15.0 与 v0.14.0 重复条目（移除 14 条重复记录）；更新 .gitignore 排除 .workbuddy/、generated-images/、test-results/、**/build-log.txt；更新 STYLEGUIDE.md 最后更新日期至 2026-07-12；README.md 补充 ERRORLOG.md 和 ASSETS_SPECIFICATION.md 引用、修正 Android 版本号 0.6.0→0.6.1；WORKLOG.md 补充本次清理记录。
- 2026-07-12 01:40 双端编译打包：WPF 发布为自包含 .exe（dist/ClickAssistant-win-x64/ + ZIP 包 77 MB）、Android 构建 release APK（dist/ClickAssistant-android-0.6.1.apk 17 MB，未签名）。修复编译错误：MainWindow.xaml.cs 中 ClickAssistant.Application 命名空间与 System.Windows.Application 冲突（改用全限定名称）、Android styles.xml 与 themes.xml 重复 AppTheme。
- 2026-07-12 01:30 液态玻璃美术升级 v2.0.0：AI 生成 14 张 PNG 图片素材（Logo/任务类型/导航/状态图标）并部署到双端；WPF 端新增深色主题（DarkTheme ResourceDictionary + 运行时切换 + 持久化）；Android 端新增深色主题（colors.xml + values-night + 动态颜色获取）；设计规范升级至 v2.0.0（色彩体系/字体排版/响应式断点/组件五态）；创建素材规格说明文档；WPF emoji 全面替换为 PNG 图标；版本提升至 v0.16.0。
- 2026-07-12 00:43 完成 Android 与 WPF 双端液态玻璃改版，统一设计令牌、页面与悬浮控件，修复桌面任务库重叠和空闲悬浮窗遮挡，并完成构建、测试和原生窗口视觉验收。
- 2026-07-12 00:30 Git 提交 + 推送到 GitHub：41 个文件变更 (+3652/-1769)，分支 feature/liquid-glass-redesign，提交信息 feat: v0.14.0 全面优化。
- 2026-07-12 00:20 移动端取点恢复 + 桌面端 D-E2/D-M3/F3：步骤添加四按钮→单"＋添加步骤"下拉 ContextMenu；坐标采集面板增加实时屏幕光标坐标（P/Invoke GetCursorPos）；取点返回后 onResume 自动恢复编辑器页面。
- 2026-07-12 00:10 桌面端 D-H1~H3/D-L2：首页重构为最近任务 ItemsControl + 三按钮快捷入口；任务库移除"执行确认"Tab，改为右栏底部固定操作栏（保存/执行/复制/删除）。
- 2026-07-12 00:00 移动端 E8+E6+E7+G3：新增 LONG_PRESS 独立类型；编辑器顶部工具栏保存按钮；空步骤大卡片；日志分页加载更多。

## 2026-07-11

- 2026-07-11 23:55 全面检查：双端编译通过、25 个测试全部通过（修复 2 个 D5 导致的测试失败）；README 版本号从 v0.13.0 更正为 v0.14.0。
- 2026-07-11 23:40 桌面端交互优化第一批（D-Nav1/D-Nav2/D-F1/D-L1/D-M2）：导航按钮 DataTrigger 活跃态高亮；版本号改为动态读取 Assembly Version；悬浮窗按钮按执行状态 Visibility 切换；任务库左栏新增快捷执行+复制任务按钮；鼠标专页执行按钮状态分组。
- 2026-07-11 23:25 移动端交互优化第一批（E1/E5/E3/H2/V2）：编辑器退出返回来源页（editorSourcePage）；执行按钮 Visibility 按状态切换（替代 enable/disable）；弹窗 CheckBox → SwitchCompat；Switch 方向区分+onResume 刷新；版本号硬编码修复。
- 2026-07-11 23:10 修复移动端辅助功能检测：isAccessibilityEnabled() 同时检查 flattenToString 和 flattenToShortString 双格式 + ResolveInfo 兜底，解决 Android 13+ 引导页不跳转问题。
- 2026-07-11 23:00 修复 WPF 启动 StaticResourceExtension 异常：App.xaml 从空变更为 ResourceDictionary（19 个 Brush + BoolToVisibility + StepSummaryConverter），删除 MainWindow.xaml 和 FloatingControlWindow.xaml 中重复资源。
- 2026-07-11 22:50 打包发布：Windows 自包含 .exe 构建成功（修复 1 处 RefreshSelectedStepListItem 残留引用 + 1 处 Validate 重复定义）；Android APK 构建成功（需启用 android.useAndroidX + buildConfig + 修复 totalSteps→total）。
- 2026-07-11 22:45 新建 ERRORLOG.md 记录 8 条典型错误，AGENTS.md 增加第 10 节"错误日志"规范，README.md 顶部增加 AGENTS.md 读取提示。
- 2026-07-11 22:35 RULE.md 重命名为 AGENTS.md，同步更新 README/STYLEGUIDE/WORKLOG 中所有引用。
- 2026-07-11 22:30 按 AGENTS.md 完成本日工作收尾：更新 CHANGELOG v0.14.0、WORKLOG 和 VERSION 文件，记录所有本日变更。
- 2026-07-11 22:20 领域层优化（D5/D6/D7）：ClickTask/ClickStep Id 改为 init-only、关键数值属性 setter 即时校验；Duplicate() 委托 ClickStep.Copy() 深拷贝消除手工逐字段赋值；ValidateForSave() if-chain 改为字典分派 ActionValidators。
- 2026-07-11 22:10 桌面端架构优化（P4-1~P4-4）：MainWindowViewModel 的 NotifyEditorDerivedProperties 拆分为 9 个按属性组分组的通知方法（26/38 调用点改为精确组通知）；ClickStep 实现 INotifyPropertyChanged（消除 RefreshSelectedStepListItem hack）；FormatMilliseconds 提取为共享 TimeFormattingHelper。
- 2026-07-11 21:50 移动端 UI 优化 Phase 2（P2-1~P2-6）：首页改为 2x2 网格布局 + 辅助功能 Switch 卡片；新建任务改为弹窗模式（单击/双击/长按/滑动）废弃 NEW_TASK 页面；悬浮按钮重写为左侧垂直抽屉（展开/收起动画）；CheckBox 全面替换为 SwitchCompat；版本号改用 BuildConfig.VERSION_NAME。
- 2026-07-11 21:35 基础设施优化 Phase 5（I1~I4）：EnsureMigrationsTableAsync 加事务保护；PRAGMA table_info 增加表名白名单防注入；统一 DateTime.Now→DateTime.UtcNow（6 处）；新增 DatabaseMigrator/SqliteExecutionLogRepository/SqliteAppSettingsRepository 测试（10 个新测试）；删除冗余 DatabaseInitializer.cs。
- 2026-07-11 21:25 桌面端修复 Phase 4 前置（D1~D3）：MainWindow.xaml.cs 两处 MessageBox.Show 改为 IDialogService.ShowError；Dispatcher.Invoke 改为 InvokeAsync；废弃 DatabaseInitializer 统一到 DatabaseMigrator 版本化迁移。
- 2026-07-11 21:15 移动端修复 N1~N6：CheckBox 点击区域限到 textCol（加 setOnTouchListener 消费触控）；步骤行增加 ↑↓ 排序按钮；dispatchSwipe 增加暂停/停止检查；执行按钮根据运行状态联动启用/禁用；编辑器页面 loadTasks 不再覆盖用户编辑字段；步骤行直接加"点选坐标"按钮避免弹窗被后台回收。
- 2026-07-11 21:00 移动端修复 N5（深入）：loadTasks() 编辑器页跳过 populateTaskFields 避免覆盖用户输入；buildTaskEditorPage 首次构建时填充字段；N6（深入）步骤行直接加"点选坐标"按钮独立于弹窗。
- 2026-07-11 20:40 移动端 5 个高优先级 bug 修复：辅助功能检测改用 AccessibilityManager API 兼容 Android 13+；引导页增加持久"已完成"标记防循环被困；标记覆盖层移除 FLAG_NOT_TOUCHABLE 修复长按菜单；AccessibilityNodeInfo 增加 recycle() 防泄漏；CheckBox 绑定阶段抑制 setChecked 触发。

- 2026-07-11 21:20 修复移动端 5 个高优先级问题（取点交互 24/25 经确认保留长按方式不改）：①开启辅助功能后返回 App 自动跳首页；②编辑器退出时未保存修改提醒（脏标记 + 保存/不保存/取消）；③编辑器新增复制任务、删除任务按钮；④清空执行日志加确认对话框；⑤筛选「已完成/已停止/运行中」改为按最近执行状态筛选而非启用开关（ExecutionLogEntry 新增 taskId）。涉及 MainActivity.java、ClickAssistantAccessibilityService.java、ExecutionLogEntry.java、ExecutionLogStore.java。
- 2026-07-11 20:55 修复移动端取点两大交互问题：①取点全屏覆盖层消费所有触摸且 z-order 高于悬浮按钮，导致「完成」按钮点不到；②覆盖层阻挡一切底层操作。改为长按 500ms 进入拖动定位、松手直接确认坐标并自动退出取点模式，不再需要「完成」按钮；enterPickMode 隐藏悬浮按钮，exitPickMode 恢复；新增左上角 ✕ 取消按钮；PickerCursorView 高亮反馈（边框加粗 5f→9f + 高亮橙色）；删除 showConfirmBubble 死代码与 PopupWindow 导入。
- 2026-07-11 19:20 移动端取点与执行优化：硬编码按压时长改为使用 pressDurationMs；取点倒计时 5→3 秒；取点覆盖层增加"测试点击"按钮；取点确认/取消后不强制切回 App（改用 Toast）；版本号 v0.12.0 → v0.13.0。
- 2026-07-11 19:15 修复"选择坐标"功能未实现问题：CoordinatePickerWindow 支持点击空白区域直接采集坐标；标记拖动后自动跟踪变更状态；关闭后正确重置 isCoordinateCapturePending；修复 MainWindow.xaml 底部版本号仍显示 v0.12.0 的问题。
- 2026-07-11 18:46 完成双端 UI 重设计与打包：桌面端 WPF 全面升级为蓝色主色左侧导航 Dashboard 布局；移动端底部导航扩展为 4 标签并优化执行日志页；Windows 自包含 .exe 发布到 dist/ 目录；Android debug APK 构建成功。
- 2026-07-11 17:54 完成手机端与电脑端操作点选及步骤编排交互全面优化，包括：移除 AfterDelayMs/WAIT 步骤、新增可视化步骤标记覆盖层、拖拽坐标调整、步骤序号显示、ClickIntervalMs/PressDurationMs/AutoFocusBeforeInput 字段、彩色序号圆圈和桌面端滑动步骤支持。
- 2026-07-11 17:50 完成工程治理收口：修复 Android allowBackup=false、缩小无障碍服务事件范围、添加 Android CI 工作流和单元测试、建立版本化数据库迁移框架、统一项目版本号（v0.12.0）、创建 GitHub Issue/PR 模板、更新发布脚本。
- 2026-07-11 18:13 按提供的 iOS 风格设计稿完成 Android 端 UI 重设计：浅色卡片风格、底部导航、权限引导页、首页卡片入口、新建任务类型选择页、任务库搜索筛选与悬浮按钮、个人中心。
- 2026-07-11 00:22 将 Android 单页界面拆分为简化多页面结构，新增点击与文本组合任务模板和可拖动光标坐标拾取，并准备 v0.4.0 真机验证。
- 2026-07-11 00:01 通过 USB 将 Android v0.3.0 debug APK 安装到真机，确认冷启动、版本号、辅助功能服务绑定、首页关键入口和无启动崩溃均正常。

## 2026-07-10

- 2026-07-10 23:47 按项目规则补齐 Android 任务复制、启用状态、执行快照、跨应用悬浮控制和文本输入失败处理，构建 v0.3.0 debug APK 并准备 USB 真机测试。
- 2026-07-10 19:24 扩展鼠标点击任务为多位置顺序执行，每个位置支持单独设置点击次数、点击间隔和点击方式，并补充构建与自动化测试验证。
- 2026-07-10 19:27 关闭锁定发布目录的旧版 Click Assistant 进程，重新发布包含多位置鼠标点击任务的 Windows exe。
- 2026-07-10 19:00 准备推送当前功能分支，补充忽略 Android Studio 本地配置目录并整理提交范围。
- 2026-07-10 18:58 重新发布 Windows 自包含 exe，覆盖生成包含全屏坐标选择层的桌面端版本。
- 2026-07-10 18:56 将鼠标坐标采集改为全屏透明选择层，支持虚拟箭头跟随鼠标、左键确认和 Esc 取消，验证 WPF 构建和测试通过。
- 2026-07-10 18:44 重新发布 Windows 自包含 exe，覆盖生成包含鼠标坐标捕获和键盘按键捕获修复的桌面端版本。
- 2026-07-10 18:43 修复鼠标坐标延迟捕获和键盘按键步骤捕获焦点问题，验证 WPF 构建和测试通过。
- 2026-07-10 18:35 使用 Windows 发布脚本生成当前桌面端自包含 exe，输出到 `dist/ClickAssistant-win-x64/ClickAssistant.App.exe`。
- 2026-07-10 18:25 调整桌面端首页为极简入口页，移除常驻左侧栏并为二级页面补充返回首页入口，验证 WPF 构建和测试通过。
- 2026-07-10 20:39 校验并解压已下载的 Microsoft Build of OpenJDK 17（tools/downloads 压缩包，SHA256 与附带校验文件一致），得到可用 JDK 主目录 `tools/downloads/jdk-17.0.19+10`；尝试用其构建 Android debug APK 时，因本机缺少 Gradle 9.4.1 发行版且联网下载超时而未能完成，源码已通过 android.jar 离线 javac 编译校验。
- 2026-07-10 20:24 将电脑端任务/步骤/执行引擎模型同步到手机端，扩展 Android 原型为多任务库、点击/滑动/等待/文本输入多步骤、执行前确认、执行日志与启停/暂停/继续控制，并支持点击与滑动坐标拾取；以 android.jar 离线编译校验通过，更新移动端 README、规划文档、CHANGELOG 与 WORKLOG。
- 2026-07-10 13:19 按 RULE/RELEASE 规范完成发布前检查（restore/build/test，13 项测试全部通过），重新发布 Windows 自包含 exe 并整体覆盖旧的发布目录，验证启动正常且本地数据库已创建。
- 2026-07-10 13:07 移除键盘按键步骤默认 A，改为由用户从键盘捕获目标按键并准备重新发布 Windows exe。
- 2026-07-10 13:00 优化任务库步骤摘要，补充已保存任务删除入口并准备重新发布 Windows exe。
- 2026-07-10 12:52 简化桌面端任务库为三段式标签页，增强键盘按键捕获反馈并准备重新发布 Windows exe。
- 2026-07-10 12:37 增强桌面端任务编辑、键盘按键捕获、点击位置提示和悬浮窗独立置顶行为，并重新发布 Windows exe。
- 2026-07-10 12:19 调整桌面端首页为三入口导航，新增执行日志页并重新发布 Windows exe。
- 2026-07-10 12:07 重新发布 Windows 桌面端 Release 版本，覆盖生成 `dist/ClickAssistant-win-x64/ClickAssistant.App.exe`。
- 2026-07-10 12:00 下载并校验 Microsoft Build of OpenJDK 17 Windows x64 ZIP，补充本地下载目录忽略规则。
- 2026-07-10 11:56 确认 Android 构建所需 JDK 环境，说明 Red Hat OpenJDK 不是必选项。
- 2026-07-10 01:29 完成桌面端多页面任务中心和基础悬浮控制窗，补充桌面界面改版设计和术语规范。
- 2026-07-10 00:55 增强 Android 原型坐标配置体验，支持点选屏幕位置并补充参数输入引导。
- 2026-07-10 00:37 安装 Android Studio、Android SDK、ADB 和 Gradle 环境，生成 Android 原型 debug APK。

## 2026-07-09

- 2026-07-09 18:01 补充 Android 需求草案和低保真原型，创建移动端轻量原型工程。
- 2026-07-09 17:53 补充移动端延伸规划，明确 Android-first 路线、iOS 边界和原型范围。
- 2026-07-09 13:26 建立 Windows 发布目录生成流程，新增发布脚本和发布上线说明文档。
- 2026-07-09 13:15 扩展组合键和文本输入能力，补充执行、存储、界面、测试和文档支持。
- 2026-07-09 13:04 建立 GitHub Actions 基础 CI，配置还原、构建和测试检查，并同步相关文档。
- 2026-07-09 12:58 新增测试专门文档，整理测试目标、测试类型、运行方式和后续补测清单。
- 2026-07-09 12:55 补充 README 测试说明，明确测试目标、测试类型和后续测试覆盖方向。
- 2026-07-09 12:44 初始化 Git 仓库和 `feature/quality-baseline` 分支，补充测试项目、执行前安全确认和停止快捷键自定义。
- 2026-07-09 01:18 扩展输入步骤能力，新增键盘单键连按和对应文档说明。
- 2026-07-09 01:00 创建 WPF 首版工程，实现任务配置、SQLite 存储、基础点击执行和全局停止快捷键。
- 2026-07-09 00:32 安装 .NET SDK 10.0.301，验证 WPF 模板可用，并配置用户级 PATH。
- 2026-07-09 00:25 排查 .NET SDK 命令不可用问题，并说明环境修复方法。
- 2026-07-09 00:22 编写编码实施计划，确认 WPF、轻量 SQL 和本机 .NET 版本策略。
- 2026-07-09 00:14 完成设计阶段文档，确定 C#、WPF、SQLite 和 Windows 首版方案。
- 2026-07-09 00:07 整理项目文档目录结构，将规划文档迁入 `docs/planning/`。
- 2026-07-09 00:03 完成点击助手规划与需求分析，新增需求规格说明和低保真原型蓝图。

## 2026-07-08

- 2026-07-08 23:59 完善项目执行规则，补充分支、提交、Issue、PR 和版本号规范。
- 2026-07-08 23:57 完善风格指南文档，补充提交、分支、格式和目录规范。
- 2026-07-08 23:50 补充工作日志和更新日志规范，并同步整理项目记录文件。
- 2026-07-08 23:50 初始化项目执行规则，创建工作记录文件，并记录规则文档变更。
