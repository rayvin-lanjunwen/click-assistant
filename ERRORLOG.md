# Click Assistant 错误日志

本文档记录开发过程中遇到的典型编译错误、运行时错误及其修复方式，供后续开发和 AI 辅助时参考。

## 2026-07-12

### 运行时错误

#### 12. SQLite Error 19 — task_steps.after_delay_ms NOT NULL constraint failed
- **现象**：桌面端"操作失败"对话框，详情 `SQLite Error 19: 'NOT NULL constraint failed: task_steps.after_delay_ms'`，在新建/复制/修改任务时弹出。
- **根本原因**：`DatabaseMigrator.cs` v1.0.0 初始建表 SQL 仍定义 `after_delay_ms INTEGER NOT NULL DEFAULT 0`。v1.2.0 迁移在注释里写"取消 AfterDelayMs"但**实际没有 DROP COLUMN**。仓库层 `SqliteClickTaskRepository.InsertStepAsync` 早已不写 `after_delay_ms` 字段，但老用户数据库中这列仍是 NOT NULL，因此任何不带 `after_delay_ms` 的 INSERT 都会触发约束失败。
- **修复**：
  1. v1.0.0 初始建表 SQL 移除 `after_delay_ms` 列。
  2. 新增 v1.3.0 迁移：`ALTER TABLE task_steps DROP COLUMN after_delay_ms;`，对老用户数据库执行。
  3. `DatabaseMigrator.ApplyMissingMigrationsAsync` 增加 `TryParseAlterTableDropColumn` 解析：列已不存在则跳过（兼容新建库）。
  4. 同步清理测试 fixture `CreateLegacySchemaAsync`。
- **测试验证**：25/25 测试全部通过。

#### 13. Android NullPointerException — taskNameInput 等 EditText 未初始化
- **现象**：用户从首页点击"最近任务"快速执行、或从执行日志中点击"重新执行此任务"时，移动端闪退。系统显示"因应用自身空指针异常，造成闪退"，logcat 显示 `NullPointerException at MainActivity.saveTaskFromInputs` 附近。
- **根本原因**：`saveTaskFromInputs()` 直接调用 `taskNameInput.getText()` 等成员变量，但这些 EditText 控件仅在 `buildTaskEditorPage()` 中初始化。`executeRecentTask()` → `startTask()` → `saveTaskFromInputs(false)` 调用链在用户**从未打开编辑器**时，控件全部为 null，立即 NPE。
- **修复**：
  1. `saveTaskFromInputs()` 入口增加所有 EditText 的 null 检查；若未初始化则视为"无需保存输入"，直接返回 true。
  2. `startCoordinatePickForStep()` 的同名调用增加 `taskNameInput != null` 保护。
  3. `updateBottomNav()` 改为四个 nav item 联合 null 检查。
- **验证**：重新编译 APK 成功。

### 编译错误

#### 10. CS0234 — ClickAssistant.Application 命名空间与 System.Windows.Application 冲突
- **现象**：WPF 编译报 `命名空间"ClickAssistant.Application"中不存在类型或命名空间名"Current"`，共 3 处（MainWindow.xaml.cs:120/143/161）。
- **原因**：文件所在命名空间为 `ClickAssistant.App`，同时导入了 `ClickAssistant.Application.Abstractions` 和 `ClickAssistant.Application.Services`。C# 向上查找命名空间时匹配到 `ClickAssistant.Application`（命名空间），导致 `Application.Current` 被解析为 `ClickAssistant.Application.Current` 而非 `System.Windows.Application.Current`。
- **修复**：将 3 处 `Application.Current` 改为全限定名称 `System.Windows.Application.Current`。

#### 11. Android 资源合并错误 — styles.xml 与 themes.xml 重复 AppTheme
- **现象**：`packageReleaseResources` 任务报 `Duplicate resources: [style/AppTheme]`。
- **原因**：`res/values/styles.xml` 和 `res/values/themes.xml` 中同时定义了 `AppTheme`，内容完全重复。
- **修复**：从 `styles.xml` 移除 `AppTheme` 定义，由 `themes.xml` 统一管理主题。

### Windows 发布脚本 UTF-8 注释解析失败

- **现象**：执行 `tools/publish-windows.ps1` 时，Windows PowerShell 5.1 报 `Unexpected token '}'`，但脚本括号结构肉眼检查正常。
- **原因**：脚本为无 BOM 的 UTF-8 编码，PowerShell 5.1 按系统代码页读取中文注释，注释末尾字符被错误解码并吞入下一行 `if`，导致解析器认为末尾右括号多余。
- **修复**：将触发问题的发布 ZIP 注释改为 ASCII，重新解析和执行后自包含发布成功。

## 2026-07-11

### 桌面端编译错误

#### 1. `CS0111` — 重复方法定义
- **现象**：`ClickStep.cs` 编译报 `类型已定义了一个名为 Validate 的具有相同参数类型的成员`
- **原因**：D7 重构时 `Validate()` 方法未完全替换，剩余一个旧签名和一个新签名
- **修复**：删除重复的旧 `Validate()` 声明，仅保留一个

#### 2. `CS0103` — RefreshSelectedStepListItem 不存在
- **现象**：`MainWindowViewModel.cs:1261` 报 `当前上下文中不存在名称 RefreshSelectedStepListItem`
- **原因**：P4-3 重构中删除了 `RefreshSelectedStepListItem` 方法，但坐标捕获回调中残留了一处调用
- **修复**：替换为 `step.RaisePropertyChanged(null)`

#### 3. `DateTime.Now` 混用
- **现象**：领域层和基础设施层使用了 6 处 `DateTime.Now`，与数据库迁移使用的 `UtcNow` 不一致
- **原因**：未统一时间存储规范
- **修复**：全部改为 `DateTime.UtcNow`，仅保留执行日志 UI 展示的 `HH:mm:ss` 格式用 `Now`

### Android 编译错误

#### 4. `android.useAndroidX` 未启用
- **现象**：`processDebugNavigationResources` 任务失败，提示 `contains AndroidX dependencies, but the android.useAndroidX property is not enabled`
- **原因**：P2-4 添加了 `androidx.appcompat:appcompat:1.7.0` 依赖，但 `gradle.properties` 中 `android.useAndroidX` 仍为 `false`
- **修复**：`gradle.properties` 中设置 `android.useAndroidX=true`

#### 5. `BuildConfig` 找不到符号
- **现象**：`MainActivity.java:1117` 报 `找不到符号：变量 BuildConfig`
- **原因**：AGP 8+ 默认关闭 `BuildConfig` 生成；P2-5 中引入了 `BuildConfig.VERSION_NAME` 引用
- **修复**：`app/build.gradle` 中添加 `buildFeatures { buildConfig = true }`

#### 6. `totalSteps` 找不到符号
- **现象**：`MainActivity.java:1713` 报 `找不到符号：变量 totalSteps`
- **原因**：N2 排序按钮中误用了未定义的变量名 `totalSteps`，方法参数名为 `total`
- **修复**：将 `totalSteps` 改为 `total`

### 桌面端运行时错误

#### 7. `StaticResourceExtension` 启动失败
- **现象**：启动 .exe 弹出对话框 `在 System.Windows.StaticResourceExtension 上提供值时引发了异常`
- **原因**：`App.xaml` 的 `Application.Resources` 为空，所有 `StaticResource` 只能从各 XAML 自身的 `Window.Resources` 解析。但 `FloatingControlWindow.xaml` line 14 有 `Visibility="{Binding ..., Converter={StaticResource BoolToVisibility}}"`，line 14 在 line 15 `Window.Resources` 之前求值，导致独立窗口在解析 `BoolToVisibility` 时找不到资源。
- **修复**：把共享颜色 brush 和转换器（`BoolToVisibility`、`StepSummaryConverter`）全部迁移到 `App.xaml` 的 `Application.Resources`，删除 `MainWindow.xaml` 和 `FloatingControlWindow.xaml` 中的重复定义。这样所有窗口（含独立窗口）都能解析共享资源。

### 运行时 Bug

#### 8. 辅助功能权限开启后返回 App 仍停留在引导页
- **现象**：用户在系统设置中开启辅助功能后返回 App，引导页不自动跳转首页
- **原因**：`isAccessibilityEnabled()` 中仅用 `ComponentName.flattenToString()` 的长格式与 `AccessibilityServiceInfo.getId()` 比对。`getId()` 在不同系统版本可能返回短格式（如 `"com.clickassistant.mobile/.ClickAssistantAccessibilityService"`），与长格式不一致导致永远匹配不上
- **修复**：同时检查 `flattenToString()`（长格式）和 `flattenToShortString()`（短格式），并增加兜底——通过 `ResolveInfo.serviceInfo.packageName` + `serviceInfo.name` 做包名+类名双重比对

### 单元测试失败

#### 9. D5 setter 即时校验导致 2 个测试失败
- **现象**：`Validate_WhenKeyboardPressCountIsInvalid` 和 `Validate_WhenMouseClickCountIsInvalid` 测试失败，因为 `KeyPressCount=0` / `MouseClickCount=0` 在对象初始化器赋值时就抛异常了，没机会走到 `Validate()` 方法
- **原因**：D5 重构后数值属性 setter 加入即时校验，非法值在赋值时立即抛出 `DomainValidationException`；但测试仍期望在调用 `Validate()` 时才抛异常
- **修复**：测试改为 `Setter_WhenXxxIsZero_ThrowsImmediately`，在对象构造时用 `Assert.Throws` 包裹元素初始化器，验证 setter 即时抛异常的行为
