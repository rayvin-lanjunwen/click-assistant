# Click Assistant 液态玻璃设计规范

文档版本：v2.5.0  
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

### 2.5 三阶阴影系统

| 层级 | 代号 | 浅色阴影（color, blur, offset, opacity） | 深色阴影（color, blur, offset, opacity） | 用途 |
| --- | --- | --- | --- | --- |
| 表层 (L1) | ShadowSm / SHADOW_L1 | `#26334A`, 8px, 3px, 8% | `#000000`, 12px, 4px, 25% | 小控件、Badge、Switch |
| 中景 (L2) | ShadowMd / SHADOW_L2 | `#26334A`, 16px, 6px, 13% | `#000000`, 28px, 8px, 40% | 标准玻璃卡片 |
| 深层 (L3) | ShadowLg / SHADOW_L3 | `#26334A`, 32px, 14px, 18% | `#000000`, 32px, 14px, 50% | 导航栏、弹窗、FAB、悬浮窗 |

**物理模拟原理**：L1（3dp 高度）对应薄玻璃片贴近桌面，阴影小而实；L2（6dp）对应标准玻璃卡片悬浮；L3（14dp）对应厚玻璃板远离背景，阴影深且分散。

### 2.6 Specular 高光层

| 平台 | 实现方式 | 浅色模式 | 深色模式 |
| --- | --- | --- | --- |
| Android | `LayerDrawable` 叠加 4 停 TL_BR 渐变 | `#60FFFFFF` → `#20FFFFFF` → 透明 → 透明 | 系统自动（`ContextCompat.getColor`） |
| WPF | CardStyle ControlTemplate 内层 Border | `#60FFFFFF` 1px Left+Top 描边 | 同上 |

高光层位于玻璃基底之上，模拟玻璃表面左上角捕获环境光源的反射效果。高光为中性白色（不与品牌色混合），品牌色通过基底渐变体现。

### 2.7 背景环境光晕

页面背景在基础三色线性渐变之上叠加两处径向光晕：
- **右上暖光**（`#25FFF0E0`，15% 不透明度，半径 500dp）：模拟窗外暖色环境光散射。
- **左下冷光**（`#18E0F0FF`，10% 不透明度，半径 400dp）：模拟屏幕环境中蓝色冷光补光。

Android 端通过 `GradientDrawable.RADIAL_GRADIENT` + `setGradientCenter` 实现，WPF 端仅导航栏底部叠加品牌蓝氛围光（`#183978F6` 底部渐变）。

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
| 悬浮阴影 | `rgba(38,51,74,0.18)` | `rgba(0,0,0,0.50)` |
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

## 10. 动效系统 (Phase C)

### 10.1 动效核心理念

Apple Liquid Glass 的"液态"感来自：
1. **Staggered 错位** — 多个元素不是同时出现，而是依次流入（每项差 40-60ms）
2. **Spring 弹性** — 弹入是"过冲一点再回弹"，不是线性淡入（OvershootInterpolator 0.35-0.5）
3. **Contextual 呼应** — 点击一个元素，它和周围的元素同时有反应

### 10.2 动画类型与参数

| 动画类型 | 方法 | 属性 | 时长 | 延迟 | 插值器 | 适用范围 |
| --- | --- | --- | --- | --- | --- | --- |
| 页面弹入 | `animateSlideUp()` | alpha 0→1, translationY 24dp→0 | 300ms | 0 | DecelerateInterpolator(1.2f) | 页面切换 showPage() |
| 弹窗进入 | `animateDialogEntrance()` | alpha 0→1, scaleX/Y 0.85→1 | 250ms | 0 | OvershootInterpolator(0.5f) | 所有 AlertDialog |
| 按压反馈 | `animatePressFeedback()` | scaleX/Y 0.96→1 | 80ms + 150ms | 0 | OvershootInterpolator(0.4f) | 卡片/按钮点击 |
| 错位弹入 | `animateStaggeredPopIn()` | alpha 0→1, scaleX/Y 0.95→1 | 200ms/item | i×staggerMs | OvershootInterpolator(0.35f) | 网格/列表/步骤/日志 |
| 通用弹入 | `animatePopIn()` | alpha 0→1, scaleX/Y 0.92→1 | 250ms | 0 | OvershootInterpolator(0.4f) | 通用卡片 |
| 抽屉滑入 | expand() 改进 | alpha 0→1, translationX -40dp→0 | 200ms | 0 | DecelerateInterpolator | FloatingTriggerButton |
| 导航轻弹 | updateBottomNav() | scaleX/Y 1.05→1 | 80ms + 120ms | 0 | — | 底部导航选中项 |

### 10.3 错位间隔（staggerMs）

| 场景 | staggerMs | 说明 |
| --- | --- | --- |
| 首页 2×2 网格 | 60ms | 4 张卡片，总序列 0-180ms |
| 任务库列表 | 50ms | 按筛选结果数量动态延时 |
| 步骤编辑器 | 40ms | 步骤数量较少，密集弹入 |
| 日志列表 | 30ms | 条目多，快速流式弹入 |

### 10.4 实现方式

- **纯 Java ViewPropertyAnimator** — 使用 `view.animate()` 链式调用，零 Kotlin/Compose 依赖
- **硬件加速属性** — 仅使用 alpha、scaleX/Y、translationX/Y，不触发 layout 重排
- **非阻塞设计** — 动画只是视觉反馈，所有点击立即响应，动画并行播放
- **兼容性** — Android API 16+（ViewPropertyAnimator 自 API 12 可用）

### 10.5 执行时序示例

```
用户点击"任务库"导航按钮
  ├─ 0ms     → showPage(LIBRARY)
  │           → loadTasks() 读数据
  │           → refreshLibrary() 建卡片
  │           → animateSlideUp(contentView)       ← 页面整体弹入
  ├─ 50ms    → 第 1 张卡片错位弹入
  ├─ 100ms   → 第 2 张卡片错位弹入
  └─ 300ms   → 所有动画完成

用户点击一张任务卡片
  ├─ 0ms     → animatePressFeedback(card)         ← 缩小到 0.96×
  ├─ 80ms    → card 回弹到 1.0×
  └─ 80ms+   → showPage(EDITOR) → animateSlideUp + animateStaggeredPopIn(steps)
```

### 10.6 已有动画 (v0.17.0 前)

| 动画 | 位置 | 说明 |
| --- | --- | --- |
| FloatingTriggerButton 展开/收起 alpha | FloatingTriggerButton.java | Phase C 已增强为 translationX 滑入 |

### 10.7 WPF 桌面端动效 (Phase D)

#### 10.7.1 Storyboard 动画类型

| 动画类型 | 触发方式 | 属性 | 时长 | 缓动 | 位置 |
| --- | --- | --- | --- | --- | --- |
| 页面内容淡入 | IsVisibleChanged (code-behind) | Opacity 0→1 | 200ms | Quadratic EaseOut | 4 个页面 Grid |
| 页面上移弹入 | IsVisibleChanged (code-behind) | Margin (0,20,0,0)→0 | 250ms | Quadratic EaseOut | 4 个页面 Grid |
| 导航按钮高亮过渡 | PageVis via code-behind | Foreground/Background Color | 150ms | Quadratic EaseOut | 4 个 NavButton |
| 导航按钮 hover | Trigger.EnterActions | Background Color Transparent→#8FFFFFFF | 150ms | EaseOutQuad | NavButtonStyle |
| 主按钮光泽扫过 | Trigger.EnterActions | ShineBorder Margin 循环 + Opacity | 600ms/循环 | EaseInOutQuad | PrimaryButtonStyle |
| 主按钮悬浮暗化 | Trigger.EnterActions | RootBorder Opacity 1→0.85 | 150ms | EaseOutQuad | PrimaryButtonStyle |
| 选择卡片上浮 | Trigger.EnterActions | RenderTransform.Y 0→-4px | 200ms | EaseOutQuad | ChoiceButtonStyle |
| 步骤选中高亮 | Trigger.EnterActions | Background Color →#DDEAFF | 150ms | EaseOutQuad | StepListBox |
| 悬浮窗弹性展开 | EventTrigger(Loaded) | ScaleY 0→1 + Opacity | 200ms | EaseOutBack | FloatingControlWindow |

#### 10.7.2 共享缓动函数

| 资源 Key | 类型 | 参数 | 用途 |
| --- | --- | --- | --- |
| EaseOutQuad | QuadraticEase | EaseOut | 通用淡入淡出/上浮 |
| EaseInOutQuad | QuadraticEase | EaseInOut | 光泽扫过循环 |
| EaseOutBack | BackEase | EaseOut, Amplitude=0.3 | 弹窗/展开（过冲回弹） |
| EaseOutElastic | ElasticEase | EaseOut, Oscillations=1, Springiness=3 | 弹簧效果（预留） |

#### 10.7.3 导航过渡时序示例

```
用户点击"任务库"导航按钮
  ├─ 0ms     → IsTaskLibraryPageVisible = true
  │           → 首页 Grid 隐藏（Visibility.Collapsed）
  │           → 任务库 Grid 显示（Visibility.Visible）
  │           → IsVisibleChanged 触发
  │
  ├─ 0ms     → HandlePageVisibilityChanged:
  │           ├─ 任务库 Grid Opacity=0, Margin=(0,20,0,0)
  │           ├─ Storyboard: Opacity 0→1 (200ms EaseOut)
  │           ├─ Storyboard: Margin (0,20,0,0)→0 (250ms EaseOut)
  │           └─ AnimateNavButtonActive()
  │               ├─ "首页" 按钮 Foreground → TextSecondaryColor (150ms)
  │               ├─ "首页" 按钮 Background → Transparent (150ms)
  │               ├─ "任务库" 按钮 Foreground → PrimaryColor (150ms)
  │               └─ "任务库" 按钮 Background → PrimaryLightColor (150ms)
  │
  └─ 250ms  → 所有动画完成
```

#### 10.7.4 实现方式

- **XAML Storyboard** — Trigger.EnterActions/ExitActions + BeginStoryboard 用于 hover/选中态
- **Code-behind Storyboard** — IsVisibleChanged 事件处理器用于页面切换（因为 DataTrigger 不支持 EnterActions）
- **ColorAnimation** — 需要 `Color` 资源（非 Brush），在 App.xaml 中分别定义
- **硬件加速** — 所有动画使用 RenderTransform（Translate/Scale）和 Opacity，不触发 Layout 重排

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
| v2.5.0 | 2026-07-12 | Phase E：逐元素精修 — 品牌渐变标题（LinearGradient + 阴影）、玻璃胶囊标签/按钮、玻璃内嵌输入框、玻璃圆形图标/徽章、虚线边框玻璃卡片、TabItem 玻璃胶囊选项卡等 20 处视觉元素深度玻璃化规范。 |
| v2.4.0 | 2026-07-12 | Phase D：WPF 桌面端动效 — 7 种 Storyboard 动画（页面淡入/上移/导航颜色/光泽扫过/卡片上浮/步骤高亮/悬浮窗弹性展开），4 个共享缓动函数，App.xaml 增补 Color 纯色资源支持 ColorAnimation，IsVisibleChanged code-behind 过渡方案。 |
| v2.3.0 | 2026-07-12 | Phase C：移动端动效系统 — 5 种动画类型（SlideUp/PressFeedback/StaggeredPopIn/DialogEntrance/PopIn）、错位间隔规范、FloatingTriggerButton 滑入改进、导航轻弹，全部基于纯 Java ViewPropertyAnimator 实现。 |
| v2.2.0 | 2026-07-12 | Phase B：新增光晕泄漏规范（`glass_bleed` 浅色 7% / 深色 19%），背景光晕改为动态颜色资源（`bg_glow_warm`/`bg_glow_cool`），WPF CardStyle 第④层升级为 `GlassBleedBrush` 品牌蓝渐变。 |
| v2.1.0 | 2026-07-12 | 阴影系统升级为三阶（L1 8px/3px/8%, L2 16px/6px/13%, L3 32px/14px/18%），新增 Specular 高光层规范、背景环境光晕设计，CardStyle 增加 4 层 ControlTemplate。 |
| v2.0.0 | 2026-07-12 | 新增深色主题设计令牌、完整字体排版体系、响应式断点逻辑、组件五态规范、图片素材体系和浅深色适配规则。 |
| v1.0.0 | 2026-07-12 | 初始版本，定义基本设计原则、共享令牌、组件规则和平台实现方式。 |
