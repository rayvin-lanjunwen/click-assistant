# Click Assistant 液态玻璃设计规范

文档版本：v2.0.0  
最后更新：2026-07-12

本文档定义 Android 与 WPF 共用的液态玻璃视觉语言。设计目标是在不改变核心任务流程的前提下，建立清晰、克制、可读且跨端一致的操作界面。v2.0.0 新增深色主题适配、完整字体排版体系、响应式断点逻辑、组件五态规范和图片素材规格引用。

## 1. 设计原则

- 玻璃材质用于表达层级，不作为纯装饰；主内容必须保持足够对比度。
- 背景使用冷灰、浅青和少量淡紫形成空间深度，避免单一蓝色主导整个界面。
- 交互控件优先保持平台原生行为，视觉参数一致但不强求像素级相同。
- 动画、阴影和透明度不得影响重复操作效率，低性能设备使用兼容材质降级。
- 深色模式下保持相同的层级表达，玻璃透明度相应降低以维持对比度。
- 所有图片素材统一使用语义化命名，并附带完整的生成提示词记录。

## 2. 色彩体系

### 2.1 浅色模式设计令牌

| 语义 | WPF Key | Android 常量 | 色值 | 用途 |
| --- | --- | --- | --- | --- |
| 主色 | PrimaryBrush | PRIMARY | `#3978F6` | 选中态、主操作、关键状态 |
| 主色深色 | PrimaryDarkBrush | — | `#245CC7` | 按下态、边缘强调 |
| 主色浅色 | PrimaryLightBrush | PRIMARY_LIGHT | `#DDEAFF` | 选中背景、浅色反馈 |
| 主色渐变起点 | — | — | `#4C8DFF` | 主按钮渐变起点 |
| 主色渐变终点 | — | — | `#5668D8` | 主按钮渐变终点 |
| 辅助青色 | SecondaryBrush | CYAN | `#0F9FA4` | 次级强调、跨端辅助色 |
| 辅助青色浅 | SecondaryLightBrush | CYAN_LIGHT | `#D8F3F1` | 青色浅色背景 |
| 主要文字 | TextPrimaryBrush | TEXT_PRIMARY | `#17233A` | 标题、正文重点 |
| 次要文字 | TextSecondaryBrush | TEXT_SECONDARY | `#526176` | 描述、辅助信息 |
| 弱化文字 | TextMutedBrush | — | `#8290A3` | 禁用态、时间戳 |
| 玻璃边框 | BorderBrush | BORDER | `#B8FFFFFF` | 卡片/容器 1px 亮边 |
| 浅玻璃边框 | BorderLightBrush | — | `#80FFFFFF` | 次级轻声描边 |
| 成功 | SuccessBrush | SUCCESS | `#169B72` | 已完成、可用状态 |
| 成功浅色 | SuccessLightBrush | SUCCESS_LIGHT | `#D8F4E8` | 成功背景反馈 |
| 警告 | WarningBrush | ORANGE | `#E98732` | 长按、执行提醒 |
| 警告浅色 | WarningLightBrush | ORANGE_LIGHT | `#FFF0DB` | 警告背景反馈 |
| 危险 | DangerBrush | RED | `#D84D5F` | 停止、删除、失败 |
| 危险浅色 | DangerLightBrush | RED_LIGHT | `#FCE4E8` | 危险背景反馈 |
| 辅助蓝青 | CyanBrush | — | `#168DA4` | 滑动/新建辅助色 |
| 辅助浅青 | CyanLightBrush | — | `#DDF3F6` | 辅助蓝青浅背景 |

### 2.2 浅色模式渐变背景

| 渐变名称 | 色标 | 用途 |
| --- | --- | --- |
| 页面背景 | `0% #E8F2F7`, `52% #F4F7FA`, `100% #EEEAF6` | 所有页面底层背景 |
| 玻璃卡片 | `0% #E8FFFFFF`, `100% #BFFFFFFF` | 卡片/面板玻璃基底 |
| 玻璃导航 | `0% #F2FFFFFF`, `100% #CBEAF3F8` | 左侧导航/底部导航 |
| 微妙玻璃 | `0% #BFFFFFFF`, `100% #8FEAF4F7` | 次级嵌套卡片 |
| 主按钮渐变 | `0% #4C8DFF`, `62% #3978F6`, `100% #5668D8` | 主操作按钮 |

### 2.3 深色模式设计令牌

| 语义 | 色值 | 用途 |
| --- | --- | --- |
| 深色页面背景起点 | `#0F111A` | 黑暗蓝黑底色 |
| 深色页面背景中点 | `#1A1D2E` | 中间过渡 |
| 深色页面背景终点 | `#151720` | 带微紫的暗色基底 |
| 深色玻璃卡片顶 | `#18FFFFFF` | 深色玻璃高光面（9% 透明） |
| 深色玻璃卡片底 | `#0AFFFFFF` | 深色玻璃暗面（4% 透明） |
| 深色玻璃导航顶 | `#14FFFFFF` | 导航玻璃亮面（8% 透明） |
| 深色玻璃导航底 | `#0AF4F7FA` | 导航玻璃暗面 |
| 深色微妙玻璃 | `#0CFFFFFF` | 次级玻璃（5% 透明） |
| 深色主文字 | `#E2E8F0` | 高对比度浅色文字 |
| 深色次要文字 | `#94A3B8` | 中对比度灰蓝文字 |
| 深色弱化文字 | `#64748B` | 低对比度辅助文字 |
| 深色玻璃边框 | `#1EFFFFFF` | 深色模式下 1px 玻璃亮边（12% 透明） |
| 深色主按钮渐变起点 | `#5B9BFF` | 深色主按钮高光（比浅色亮） |
| 深色主按钮渐变终点 | `#6B78E8` | 深色主按钮暗端 |

深色模式下主色 `#3978F6`、成功 `#169B72`、警告 `#E98732`、危险 `#D84D5F` 保持不变，它们在暗色背景上的对比度自然增强。

### 2.4 深色模式渐变背景

| 渐变名称 | 色标 | 用途 |
| --- | --- | --- |
| 深色页面背景 | `0% #0F111A`, `52% #1A1D2E`, `100% #151720` | 所有页面深色底层 |
| 深色玻璃卡片 | `0% #18FFFFFF`, `100% #0AFFFFFF` | 深色卡片玻璃基底 |
| 深色玻璃导航 | `0% #14FFFFFF`, `100% #0AF4F7FA` | 深色导航背景 |
| 深色微妙玻璃 | `0% #0CFFFFFF`, `100% #06FFFFFF` | 深色次级卡片 |
| 深色主按钮 | `0% #5B9BFF`, `62% #3978F6`, `100% #6B78E8` | 深色主操作按钮 |

### 2.5 阴影参数

| 层级 | 浅色阴影（color, blur, offset, opacity） | 深色阴影（color, blur, offset, opacity） | 用途 |
| --- | --- | --- | --- |
| 轻阴影 | `#26334A`, 8px, 4px, 6% | `#000000`, 12px, 4px, 25% | 次级卡片 |
| 标准阴影 | `#26334A`, 24px, 8px, 13% | `#000000`, 28px, 8px, 40% | 主卡片 |
| 强阴影 | `#253248`, 28px, 8px, 22% | `#000000`, 32px, 10px, 50% | 悬浮窗 |

## 3. 字体排版体系

### 3.1 字体族

- **Windows (WPF)**：`Microsoft YaHei UI`, fallback `Segoe UI`
- **Android**：系统默认 `sans-serif`（对应 Roboto / Noto Sans）

### 3.2 排版层级

| 层级 | WPF 字号 | Android SP | 字重 | 行高 | 用途 |
| --- | --- | --- | --- | --- | --- |
| 大标题 (H1) | 28px | 24sp | Bold (700) | 1.3 | 页面主标题 |
| 标题 (H2) | 24px | 20sp | Bold (700) | 1.3 | 页面标题 |
| 区块标题 (H3) | 18px | 18sp | SemiBold (600) | 1.35 | 区块标题、卡片标题 |
| 副标题 (H4) | 16px | 16sp | SemiBold (600) | 1.4 | 小型区块标题 |
| 正文 (Body) | 14px | 14sp | Regular (400) | 1.5 | 内容文字 |
| 辅助文字 (Caption) | 13px | 12sp | Regular (400) | 1.4 | 表单标签、说明文字 |
| 微小文字 (Micro) | 11px | 10sp | Regular (400) | 1.3 | 版本号、时间戳 |

### 3.3 按钮文字

| 按钮类型 | WPF 字号 | Android SP | 字重 |
| --- | --- | --- | --- |
| 主按钮 | 14px | 14sp | SemiBold (600) |
| 次要按钮 | 14px | 14sp | Regular (400) |
| 导航按钮 | 14px | 14sp | Regular (400) |
| 导航激活 | 14px | 14sp | SemiBold (600) |

## 4. 响应式断点

### 4.1 WPF 桌面端

| 断点 | 最小宽度 | 行为 |
| --- | --- | --- |
| 最小窗口 | 1080px | 所有页面正常显示，左侧导航 228px，内容区弹性伸缩 |
| 紧凑窗口 | 1080-1280px | 任务库左栏缩小至 280px，卡片 padding 缩减 |
| 标准窗口 | 1280-1600px | 默认布局，卡片 padding 20px |
| 宽屏窗口 | >1600px | 内容区最大宽度限制 1200px 居中（可选） |

### 4.2 Android 移动端

| 断点 | 屏幕宽度 | 行为 |
| --- | --- | --- |
| 小屏 | 320-360dp | 单列布局，卡片水平 margin 12dp |
| 标准屏 | 360-420dp | 首页 2x2 网格，卡片 margin 16dp |
| 大屏/平板 | >420dp | 考虑双列或三列网格，根据实际设备适配 |

## 5. 组件交互状态规范

### 5.1 主按钮 (Primary)

| 状态 | 视觉效果 | WPF 实现 | Android 实现 |
| --- | --- | --- | --- |
| 默认态 (Normal) | 蓝紫渐变背景 + 白色文字 | PrimaryGradientBrush | roundedDrawable(PRIMARY) |
| 悬停态 (Hover) | 整体透明度 0.9 | IsMouseOver Trigger Opacity=0.9 | — |
| 按下态 (Pressed) | Scale 0.97 + 背景暗 10% | 可选 Storyboard | animate().scaleX(0.97) |
| 聚焦态 (Focused) | 边框 2px PrimaryBrush | FocusVisualStyle | — |
| 禁用态 (Disabled) | 整体透明度 0.4 | IsEnabled=False Opacity=0.5 | setAlpha(0.4f) |

### 5.2 次要按钮 (Secondary)

| 状态 | 视觉效果 | WPF 实现 | Android 实现 |
| --- | --- | --- | --- |
| 默认态 | 半透明白底 + 文字色 + 1px 白边 | #AFFFFFFF bg + BorderBrush | GRAY_BG 底 + BORDER 边 |
| 悬停态 | 白底加深 (#E6FF) + 边框变蓝 | IsMouseOver Trigger | — |
| 按下态 | Scale 0.97 | 可选 Storyboard | animate().scaleX(0.97) |
| 聚焦态 | 边框变蓝 2px | FocusVisualStyle | — |
| 禁用态 | 整体透明度 0.4 | Opacity=0.5 | setAlpha(0.4f) |

### 5.3 危险按钮 (Danger)

| 状态 | 视觉效果 | WPF 实现 | Android 实现 |
| --- | --- | --- | --- |
| 默认态 | 红色文字 + 淡红边框 | DangerBrush + #FECACA 边框 | RED 文字色 |
| 悬停态 | 淡红背景 | DangerLightBrush bg | — |
| 按下态 | Scale 0.97 + 红底加深 | 可选 Storyboard | animate().scaleX(0.97) |
| 禁用态 | 整体透明度 0.4 | Opacity=0.5 | setAlpha(0.4f) |

### 5.4 玻璃卡片

| 状态 | 视觉效果 | WPF 实现 | Android 实现 |
| --- | --- | --- | --- |
| 默认态 | 半透明白渐变 + 1px 白边 + 阴影 | CardStyle | glassDrawable(radius) |
| 悬停态 | 阴影加深 | Effect BlurRadius+4 | elevation 增加 |
| 按下态 | 阴影变浅（下沉感）+ 背景微暗 | Effect BlurRadius-4 | — |

### 5.5 文本输入框

| 状态 | 视觉效果 | WPF 实现 | Android 实现 |
| --- | --- | --- | --- |
| 默认态 | 半透明白底 + 浅白边框 | BgCardBrush + BorderBrush | glassDrawable(9) |
| 悬停态 | 边框加深 | — | — |
| 聚焦态 | 边框变蓝 + 蓝光外发光 | 可选 FocusVisualStyle | 蓝色边框高亮 |
| 禁用态 | 灰色底 + 文字变灰 | Gray bg | setEnabled(false) |

### 5.6 导航按钮

| 状态 | 视觉效果 | WPF 实现 | Android 实现 |
| --- | --- | --- | --- |
| 默认态 | 透明背景 + 灰色文字 | NavButtonStyle | 透明底 + TEXT_SECONDARY |
| 悬停态 | 半透明高亮背景 | IsMouseOver #8FFFFFFF | — |
| 激活态 | 蓝色文字 + 浅蓝背景 | DataTrigger PrimaryBrush + PrimaryLightBrush | PRIMARY 色文字 + PRIMARY_LIGHT 底 |

### 5.7 Switch / Toggle

| 状态 | 视觉效果 | Android 实现 |
| --- | --- | --- |
| 关闭态 | 灰色滑块 | SwitchCompat 默认 |
| 开启态 | 主色滑块 | SwitchCompat trackTint 主色 |
| 禁用态 | 整体透明度 0.4 | setEnabled(false) |

## 6. 圆角与间距系统

### 6.1 圆角等级

| 等级 | 数值 | 用途 |
| --- | --- | --- |
| 大圆角 | 16 dp/px | 主要容器、大卡片 |
| 中圆角 | 12 dp/px | 紧凑卡片、弹窗 |
| 小圆角 | 9-11 dp/px | 按钮、输入框 |
| 迷你圆角 | 4-8 dp/px | 标签、徽章、子图标背景 |

### 6.2 间距系统

| 等级 | 数值 | 用途 |
| --- | --- | --- |
| 微小间距 | 4 dp/px | 紧密关联元素 |
| 小间距 | 8 dp/px | 同组元素间隔 |
| 标准间距 | 12-16 dp/px | 区块内部间距 |
| 大间距 | 20-24 dp/px | 区块间间距、卡片 padding |
| 页面边距 | 24-28 dp/px | 页面内容区边距 |

## 7. 图片素材体系

### 7.1 素材清单

所有图片素材为透明背景 PNG，蓝紫渐变玻璃质感几何风格。详细规格、生成提示词和版本信息参见 `docs/design/ASSETS_SPECIFICATION.md`。

| 文件名 | 尺寸 | 用途 |
| --- | --- | --- |
| `logo_app.png` | 512×512 | 应用主 Logo |
| `icon_tap.png` | 512×512 | 鼠标点击任务类型图标 |
| `icon_keyboard.png` | 512×512 | 键盘按键任务类型图标 |
| `icon_text_input.png` | 512×512 | 文本输入任务类型图标 |
| `icon_combo.png` | 512×512 | 组合任务类型图标 |
| `icon_swipe.png` | 512×512 | 滑动手势任务类型图标 |
| `nav_home.png` | 512×512 | 首页导航图标 |
| `nav_new_task.png` | 512×512 | 新建任务导航图标 |
| `nav_library.png` | 512×512 | 任务库导航图标 |
| `nav_logs.png` | 512×512 | 执行日志导航图标 |
| `status_success.png` | 512×512 | 执行成功状态图标 |
| `status_warning.png` | 512×512 | 警告状态图标 |
| `status_error.png` | 512×512 | 错误/失败状态图标 |
| `status_running.png` | 512×512 | 运行中状态图标 |

### 7.2 素材存储位置

- **WPF**：`src/ClickAssistant.App/Resources/Images/`
- **Android**：`mobile/android/app/src/main/res/drawable/`
- 两处存放相同的 PNG 文件，按需使用。素材规格文档为唯一真实来源。

## 8. 深浅色主题适配

### 8.1 切换机制

| 平台 | 实现方式 | 存储位置 |
| --- | --- | --- |
| WPF | 代码切换 ResourceDictionary + 持久化到 `app_settings` | `app_settings` SQLite 表 |
| Android | 系统自动切换 `values/colors.xml` + `values-night/colors.xml` | 跟随系统设置 |

### 8.2 适配对比表

| 视觉元素 | 浅色模式 | 深色模式 |
| --- | --- | --- |
| 页面背景 | `#E8F2F7 → #F4F7FA → #EEEAF6` | `#0F111A → #1A1D2E → #151720` |
| 玻璃卡片 | `rgba(255,255,255,0.91~0.75)` | `rgba(255,255,255,0.09~0.04)` |
| 玻璃描边 | `#B8FFFFFF` (72%) | `#1EFFFFFF` (12%) |
| 内高光 | `rgba(255,255,255,0.6)` | `rgba(255,255,255,0.08)` |
| 卡片阴影 | `rgba(38,51,74,0.13)` | `rgba(0,0,0,0.40)` |
| 主文字 | `#17233A` | `#E2E8F0` |
| 次文字 | `#526176` | `#94A3B8` |
| 弱化文字 | `#8290A3` | `#64748B` |
| 主色 | `#3978F6` | `#3978F6` (不变) |
| 成功 | `#169B72` | `#169B72` (不变) |
| 警告 | `#E98732` | `#E98732` (不变) |
| 危险 | `#D84D5F` | `#D84D5F` (不变) |
| 系统栏 | 浅蓝灰 | 深蓝黑 |
| PNG 图标 | 正常显示（透明背景） | 正常显示（透明背景，无需额外处理） |

## 9. 平台实现

### 9.1 WPF

- 使用 `LinearGradientBrush`、半透明边框与 `DropShadowEffect` 构成玻璃层。
- 共享颜色和材质集中在 `App.xaml`，浅色和深色各一个独立 ResourceDictionary。
- 主窗口 `MainWindow.xaml` 通过 `DynamicResource` 引用颜色（主题切换时自动更新）。
- 悬浮窗由执行状态驱动显示和隐藏，避免空闲时遮挡导航与编辑内容。
- 主题切换逻辑在 `MainWindow.xaml.cs` 中实现，设置持久化到 `app_settings` 表。
- 卡片入场动画：`Loaded` 事件触发 `BeginStoryboard`，从 Opacity=0 + TranslateY=20 淡入上浮，持续 300ms。

### 9.2 Android

- 使用 `GradientDrawable` 构建背景、玻璃卡片、主按钮和导航高光。
- 所有颜色集中定义在 `res/values/colors.xml`，深色模式覆盖在 `res/values-night/colors.xml`。
- `MainActivity.java` 通过 `ContextCompat.getColor()` 动态获取颜色，自动响应系统主题切换。
- 采用半透明渐变、描边与原生 elevation，兼容 Android 7+，不依赖 API 31 的实时背景模糊。
- 系统栏、取点标记、悬浮抽屉和任务类型色均复用共享设计语义。
- 卡片入场动画：`ViewPropertyAnimator` alpha 0→1 + translationY 20→0，持续 400ms，DecelerateInterpolator。

### 9.3 图标集成方式

| 平台 | 图标引用方式 | 示例 |
| --- | --- | --- |
| WPF | `<Image Source="Resources/Images/icon_tap.png" Width="48" Height="48" />` | XAML Image 控件 |
| Android | `imageView.setImageResource(R.drawable.icon_tap)` | ImageView + R.drawable 引用 |

## 10. 动画与过渡

### 10.1 动画参数

| 动画 | 时长 | 缓动曲线 | 适用范围 |
| --- | --- | --- | --- |
| 卡片入场 | 300ms (WPF) / 400ms (Android) | ease-out / DecelerateInterpolator | 页面首次加载卡片 |
| 按钮按下 | 100ms 缩放 / 200ms 恢复 | ease-out | 所有按钮 |
| 页面切换 | 200ms 淡入淡出 | ease-out | 导航切换 |
| 悬浮窗展开 | 250ms | ease-out | 悬浮控制窗 |

### 10.2 实施约束

- 所有动画使用硬件加速属性（opacity、translation、scale），避免触发 layout 重排。
- 动画帧率在低性能设备上不低于 30fps。
- 无用户交互时不执行动画（空闲节电）。

## 11. 可用性与无障碍

### 11.1 对比度要求

- 正文与背景对比度 ≥ 4.5:1（WCAG AA 标准）。
- 大标题与背景对比度 ≥ 3:1。
- 运行中/暂停/停止/完成/失败状态必须同时通过文字、颜色和图标三重表达。
- 深色模式下确保主色 `#3978F6` 在 `#E2E8F0` 文字旁有足够区分度。

### 11.2 交互规范

- 所有可点击元素最小触控区域 44×44 dp（Android）/ 32×32 px（WPF）。
- 按钮间距 ≥ 8dp，避免误触。
- 页面缩放或内容换行时不得出现标题、提示和操作栏重叠。
- 输入框聚焦态保持平台默认键盘和无障碍行为（屏幕阅读器兼容）。

### 11.3 测试验收

- Android 无真机时至少完成单元测试、Debug APK 构建和资源检查。
- WPF 必须通过自包含发布版进行原生窗口截图验收。
- 深色主题需在系统深色模式下截图验证所有页面。

## 12. 版本历史

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v2.0.0 | 2026-07-12 | 新增深色主题设计令牌、完整字体排版体系、响应式断点逻辑、组件五态规范、图片素材体系和浅深色适配规则。 |
| v1.0.0 | 2026-07-12 | 初始版本，定义基本设计原则、共享令牌、组件规则和平台实现方式。 |
