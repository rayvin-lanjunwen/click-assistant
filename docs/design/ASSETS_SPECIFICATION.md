# Click Assistant 图片素材规格说明

文档版本：v1.0.0  
最后更新：2026-07-12

本文档记录 Click Assistant 项目中所有图片素材的规格、生成提示词和使用位置，方便后期手动修改和重新生成。所有素材采用统一风格：蓝紫渐变玻璃质感几何图标、透明背景 PNG。

## 1. 风格规范

| 属性 | 值 |
| --- | --- |
| 风格 | 简约几何图标 + 液态玻璃材质 |
| 主色调 | `#3978F6` 蓝 → `#5668D8` 紫 渐变（对角线 135°） |
| 语义色 | 成功 `#169B72` / 警告 `#E98732` / 危险 `#D84D5F` |
| 材质 | 半透明毛玻璃 + 顶部白色内高光描边 |
| 背景 | 透明（PNG Alpha 通道） |
| 文件格式 | PNG-24（支持透明度） |

## 2. 素材清单

### 2.1 logo_app.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024（运行时缩放到所需尺寸） |
| 用途 | 应用主 Logo，用于导航栏顶部、首页 Hero 区域 |
| 设计描述 | 两个叠加的半透明玻璃矩形抽象构成 "CA" 形状 |
| 生成提示词 | `A minimal geometric app logo icon with glass material effect. Two overlapping translucent glass rectangles forming an abstract 'CA' shape. Blue to purple diagonal gradient (#3978F6 to #5668D8). Inner glow and refractive edge highlights. Frosted glass texture with subtle white rim light on top edge. Clean transparent background. Modern flat UI icon style with glassmorphism depth. Centered, 512x512 composition.` |
| 使用位置 | WPF: MainWindow.xaml 导航栏 + 首页 Hero; Android: 个人中心页 |

### 2.2 icon_tap.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 鼠标点击任务类型图标 |
| 设计描述 | 圆角玻璃光标箭头形状 |
| 生成提示词 | `A minimal geometric icon for 'mouse click' action. A simple rounded glass cursor arrow shape with glass refraction effect. Blue to purple gradient (#3978F6 to #5668D8). Semi-transparent with white inner glow highlights on top edges. Frosted glass material. Clean transparent background. Flat modern UI icon, centered, 512x512.` |
| 使用位置 | WPF: 新建任务类型选择页第1张卡片; Android: 新建任务弹窗单击选项 |

### 2.3 icon_keyboard.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 键盘按键任务类型图标 |
| 设计描述 | 圆角玻璃矩形带水平按键线 |
| 生成提示词 | `A minimal geometric icon for 'keyboard' action. A simple glass rectangle with rounded corners and a few horizontal line keys inside. Glass refraction effect with blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with white rim light on top edge. Clean transparent background. Flat modern UI icon style, centered, 512x512.` |
| 使用位置 | WPF: 新建任务类型选择页第2张卡片; Android: 新建任务弹窗键盘选项 |

### 2.4 icon_text_input.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 文本输入任务类型图标 |
| 设计描述 | 玻璃矩形带光标线和抽象字符 |
| 生成提示词 | `A minimal geometric icon for 'text input' action. A glass rectangle with a text cursor line and a few abstract character shapes. Glass refraction effect with blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with white inner glow highlights. Clean transparent background. Flat modern UI icon, centered, 512x512.` |
| 使用位置 | WPF: 新建任务类型选择页第3张卡片; Android: 新建任务弹窗文本输入选项 |

### 2.5 icon_combo.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 组合任务类型图标 |
| 设计描述 | 两个叠加的玻璃矩形+号连接 |
| 生成提示词 | `A minimal geometric icon for 'combo task / combination' action. Two overlapping rounded glass rectangles connected by a plus symbol, representing combining multiple actions. Glass refraction effect with blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with white rim light. Clean transparent background. Flat modern UI icon, centered, 512x512.` |
| 使用位置 | WPF: 新建任务类型选择页第4张卡片; Android: 新建任务弹窗组合选项 |

### 2.6 icon_swipe.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 滑动手势任务类型图标 |
| 设计描述 | 圆角玻璃矩形内弧形箭头+起点终点 |
| 生成提示词 | `A minimal geometric icon for 'swipe gesture' action. A curved arrow path across a rounded glass rectangle with two small dots at start and end. Glass refraction effect with blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass material with white inner glow. Clean transparent background. Centered, 512x512.` |
| 使用位置 | WPF: 新建任务类型选择页第5张卡片; Android: 步骤类型选择滑动选项 |

### 2.7 nav_home.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 首页导航图标 |
| 设计描述 | 玻璃房屋形状（圆顶+门） |
| 生成提示词 | `A minimal geometric icon for 'home' navigation. A simple glass house shape with a rounded roof and door. Blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with subtle white edge highlights. Clean transparent background. Small 24x24 style UI navigation icon, centered, 512x512.` |
| 使用位置 | WPF: 左侧导航栏首页按钮; Android: 底部导航首页标签 |

### 2.8 nav_new_task.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 新建任务导航图标 |
| 设计描述 | 玻璃方块+中心加号 |
| 生成提示词 | `A minimal geometric icon for 'new task / create' navigation. A glass square with a plus symbol in the center. Blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with white rim light on top edge. Clean transparent background. Small 24x24 style UI navigation icon, centered, 512x512.` |
| 使用位置 | WPF: 左侧导航栏新建任务按钮; Android: 首页快速创建入口 |

### 2.9 nav_library.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 任务库导航图标 |
| 设计描述 | 堆叠的玻璃矩形列表 |
| 生成提示词 | `A minimal geometric icon for 'library / tasks' navigation. Three glass rectangles forming a stacked list with lines inside each. Blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with white edge highlights. Clean transparent background. Small 24x24 style UI navigation icon, centered, 512x512.` |
| 使用位置 | WPF: 左侧导航栏任务库按钮; Android: 底部导航任务库标签 |

### 2.10 nav_logs.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 执行日志导航图标 |
| 设计描述 | 玻璃剪贴板/文档+水平线 |
| 生成提示词 | `A minimal geometric icon for 'logs / history' navigation. A glass clipboard or document shape with horizontal lines representing log entries. Blue to purple gradient (#3978F6 to #5668D8). Semi-transparent frosted glass with subtle white rim light. Clean transparent background. Small 24x24 style navigation icon, centered, 512x512.` |
| 使用位置 | WPF: 左侧导航栏执行日志按钮; Android: 底部导航日志标签 |

### 2.11 status_success.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 执行成功/完成状态图标 |
| 主色 | `#169B72` (绿色) |
| 设计描述 | 玻璃圆形+对勾标记 |
| 生成提示词 | `A minimal geometric icon for 'success / completed' status. A glass circle with a checkmark inside. Green glass gradient (#169B72) with white inner glow highlight. Semi-transparent frosted glass material. Clean transparent background. Small 20x20 style status icon, centered, 512x512.` |
| 使用位置 | WPF: 执行日志成功条目、悬浮窗完成状态; Android: 执行日志卡片状态列 |

### 2.12 status_warning.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 警告状态图标 |
| 主色 | `#E98732` (橙色) |
| 设计描述 | 玻璃三角形+感叹号 |
| 生成提示词 | `A minimal geometric icon for 'warning' status. A glass triangle with an exclamation mark inside. Orange glass gradient (#E98732) with white inner glow highlight on top edge. Semi-transparent frosted glass material. Clean transparent background. Small 20x20 style status icon, centered, 512x512.` |
| 使用位置 | WPF: 执行前安全提示区域; Android: 权限未开启提示 |

### 2.13 status_error.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 错误/失败状态图标 |
| 主色 | `#D84D5F` (红色) |
| 设计描述 | 玻璃圆形+X标记 |
| 生成提示词 | `A minimal geometric icon for 'error / failed' status. A glass circle with an X mark inside. Red glass gradient (#D84D5F) with white inner glow highlight on top edge. Semi-transparent frosted glass material. Clean transparent background. Small 20x20 style status icon, centered, 512x512.` |
| 使用位置 | WPF: 执行日志失败条目、停止按钮旁; Android: 执行日志错误状态 |

### 2.14 status_running.png

| 属性 | 值 |
| --- | --- |
| 尺寸 | 1024×1024 |
| 用途 | 运行中状态图标 |
| 主色 | `#3978F6` (蓝色) |
| 设计描述 | 玻璃圆形+播放三角形/旋转形状 |
| 生成提示词 | `A minimal geometric icon for 'running / in progress' status. A glass circle with a play triangle or spinning-like shape inside. Blue glass gradient (#3978F6) with white inner glow highlight. Semi-transparent frosted glass material. Clean transparent background. Small 20x20 style status indicator icon, centered, 512x512.` |
| 使用位置 | WPF: 悬浮窗运行中状态; Android: 悬浮抽屉运行中指示 |

## 3. 存储位置

| 端 | 路径 |
| --- | --- |
| WPF | `src/ClickAssistant.App/Resources/Images/` |
| Android | `mobile/android/app/src/main/res/drawable/` |

两处存放完全相同的 PNG 文件。WPF 通过 XAML `<Image Source="Resources/Images/xxx.png" />` 引用，Android 通过 `R.drawable.xxx` 引用。

## 4. 后期修改指南

1. 修改某张图片时，在对应目录替换同名 PNG 文件即可，代码无需改动。
2. 如需重新生成，使用上表中的生成提示词调用 AI 图像生成工具。
3. 生成新图片后，同步替换 WPF 和 Android 两端的对应文件。
4. 新增素材时，参照现有命名规则（前缀_描述.png），并在本文档中补充记录。

## 5. 版本历史

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0.0 | 2026-07-12 | 初始版本，记录全部 14 张图片素材的规格、提示词和使用位置。 |
