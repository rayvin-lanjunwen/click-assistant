# Click Assistant 阅读说明

> **AI / 开发者须知：处理本项目任务前，请先读取 `AGENTS.md`。**

Click Assistant 是一个本地点击与键盘输入辅助工具，目标是帮助用户把重复性鼠标点击、键盘输入和文本输入操作配置成可复用任务，并支持多位置顺序点击、启动、暂停、停止和查看执行日志。

**当前版本：v0.16.0** | Android 端：v0.6.1 | Windows 端：v0.16.0

当前项目已进入编码实现阶段，首版 C# + WPF MVP 已完成基础工程骨架、任务管理、步骤配置、坐标捕获、SQLite 存储、鼠标点击执行、键盘连按执行、组合键执行、文本输入、全局停止快捷键、执行前安全确认、快捷键自定义、桌面端多页面任务中心、基础悬浮控制窗、核心自动化测试和基础 CI。Android 端已迁移适合手机环境的任务库、多步骤编辑、点击、文本输入、滑动、光标坐标拾取、点击与文本组合模板、执行日志、运行快照和悬浮启停控制，并采用首页、任务库、任务编辑与执行日志分离的简化页面结构；移动端明确不提供键盘按键与组合键注入。

**近期重要变更（v0.16.0）**：新增 AI 生成全套图片素材（Logo/任务类型/导航/状态共 14 张 PNG），双端新增深色主题支持（WPF 运行时切换 + Android 系统跟随），设计规范升级至 v2.0.0 补全色彩体系、字体排版、响应式断点和组件五态规范。


## 当前阶段

- 已建立项目执行规则：`AGENTS.md`
- 已建立项目风格规范：`STYLEGUIDE.md`
- 已建立需求规格说明：`docs/planning/REQUIREMENTS.md`
- 已建立低保真原型蓝图：`docs/planning/PROTOTYPE.md`
- 已建立移动端延伸规划：`docs/planning/MOBILE_EXTENSION_PLAN.md`
- 已建立 Android 端需求草案：`docs/planning/ANDROID_REQUIREMENTS.md`
- 已建立 Android 端低保真原型：`docs/planning/ANDROID_PROTOTYPE.md`
- 已建立架构设计：`docs/architecture/ARCHITECTURE.md`
- 已建立详细设计：`docs/design/DETAILED_DESIGN.md`
- 已建立 UI/UX 设计：`docs/design/UI_UX_DESIGN.md`
- 已建立桌面端界面改版设计：`docs/design/DESKTOP_UI_REDESIGN.md`
- 已建立双端液态玻璃设计规范：`docs/design/LIQUID_GLASS_DESIGN.md`
- 已建立图片素材规格说明：`docs/design/ASSETS_SPECIFICATION.md`
- 已建立编码实施计划：`docs/development/IMPLEMENTATION_PLAN.md`
- 已创建 WPF 桌面应用工程：`src/ClickAssistant.App/`
- 已创建领域层工程：`src/ClickAssistant.Domain/`
- 已创建应用服务层工程：`src/ClickAssistant.Application/`
- 已创建基础设施层工程：`src/ClickAssistant.Infrastructure/`
- 已创建 Android 原型工程：`mobile/android/`
- Android 端版本已提升至 `0.6.1`，可生成 release APK。
- 已创建自动化测试工程：`tests/ClickAssistant.Tests/`
- 已建立 GitHub Actions 基础 CI：`.github/workflows/ci.yml`
- 已建立工作记录：`WORKLOG.md`
- 已建立更新日志：`CHANGELOG.md`
- 已建立错误日志：`ERRORLOG.md`

## 运行方式

当前终端如果还没有刷新用户级 PATH，可以先使用已安装 SDK 的完整路径执行命令。

```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" build ClickAssistant.slnx
```

```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" run --project src\ClickAssistant.App\ClickAssistant.App.csproj
```

```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" test ClickAssistant.slnx
```

```powershell
.\tools\publish-windows.ps1
```

应用数据默认存放在 `%LOCALAPPDATA%\ClickAssistant\clickassistant.db`。

应用启动后默认显示首页，首页只保留"新建任务""任务库"和"执行日志"三个入口；点击入口后在主内容区切换到对应页面。
任务库左侧显示已保存任务，并提供"新建任务"和"删除选中任务"入口；右侧采用"基础信息 / 步骤设置 / 执行确认"三段式标签页，进入任务库时先选择任务，再按当前目标进入对应标签，减少同屏复杂控件。

全局停止快捷键默认为 `Ctrl + Alt + S`，用于在窗口失焦时请求立即停止当前任务。用户可在执行面板中修改停止快捷键，设置会保存到本地 SQLite `app_settings` 表。

启动任务前会显示执行前安全确认，提醒用户检查坐标、按键、重复次数和开始延迟。任务启动中和运行中会锁定任务编辑区；暂停后可以调整并保存任务配置，但本次运行仍以启动时读取的配置为准，如需让新配置生效，应停止后重新执行。任务执行时会显示基础悬浮控制窗，支持展开、收起、暂停、继续和停止。

鼠标点击任务支持配置多个点击位置，左侧位置列表的顺序就是执行顺序；每个位置都可以单独设置坐标、点击次数、点击间隔和点击方式。执行轮数表示整组位置会重复几轮；开始延迟表示点击"开始执行"后，等待多久再执行第一个步骤；点击间隔表示该位置一次点击完成后，等待多久再执行下一次点击、下一步或下一轮。鼠标点击执行前会显示置顶虚拟鼠标箭头和扩散动效，用于确认真实点击位置。
鼠标坐标采集通过全屏透明选择层完成，点击"选择坐标"后移动鼠标到目标位置，左键确认坐标，按 `Esc` 取消。

键盘步骤支持常用单键连按，例如 `A-Z`、`0-9`、`F1-F24`、`Enter`（回车）、`Space`（空格）、`Tab`、`Esc`、方向键、`Delete`（删除）和 `Backspace`（退格）。新建键盘按键步骤默认显示"未设置"，不会自动填入 `A`；用户需要在任务库的"步骤设置"中点击按键捕获框，并直接按下键盘上的一个键来确定目标按键。步骤列表会直接显示"键盘按键：目标键、连按次数、间隔"等摘要；界面会显示"当前按键"和"正在监听 / 已捕获 / 暂不支持"的反馈。组合键步骤支持类似 `Ctrl+C`、`Ctrl+V`、`Ctrl+Shift+Esc` 和 `Alt+Tab` 的格式。文本输入步骤支持输入一段文本，并可配置字符间隔。

## 测试说明

测试是保障软件质量的关键环节，应贯穿整个开发过程，而不是只在编码结束后集中补充。详细测试目标、测试类型、当前覆盖范围、运行方式和后续补测清单参见 `docs/development/TESTING.md`。

当前已建立 `tests/ClickAssistant.Tests/` 自动化测试工程，首批覆盖领域模型校验、执行引擎状态流转和 SQLite 仓储。

## 文档说明

- `AGENTS.md`：项目执行规则，优先级最高。
- `STYLEGUIDE.md`：文档、代码、日志、提交和目录风格规范。
- `docs/planning/REQUIREMENTS.md`：规划与需求分析阶段的需求规格说明书。
- `docs/planning/PROTOTYPE.md`：产品低保真页面蓝图。
- `docs/planning/MOBILE_EXTENSION_PLAN.md`：移动端延伸规划，记录 Android-first 路线、iOS 边界和原型范围。
- `docs/planning/ANDROID_REQUIREMENTS.md`：Android 端需求草案，记录授权、点击、停止和本地保存的原型范围。
- `docs/planning/ANDROID_PROTOTYPE.md`：Android 端低保真原型，记录首屏、授权引导、保存、执行和停止流程。
- `docs/architecture/ARCHITECTURE.md`：技术选型、系统分层、模块职责和接口方向。
- `docs/design/DETAILED_DESIGN.md`：核心模块、数据结构、执行状态和异常处理设计。
- `docs/design/UI_UX_DESIGN.md`：界面风格、页面结构、交互状态和视觉约束。
- `docs/design/DESKTOP_UI_REDESIGN.md`：桌面端多页面任务中心、任务库、鼠标编辑页和悬浮窗改版设计。
- `docs/design/LIQUID_GLASS_DESIGN.md`：Android 与 WPF 共用的液态玻璃设计令牌、组件规则和平台实现说明。
- `docs/design/ASSETS_SPECIFICATION.md`：AI 生成的图片素材规格说明，记录每张图片的尺寸、色值、生成提示词和使用位置。
- `docs/development/IMPLEMENTATION_PLAN.md`：编码阶段实施顺序、工程初始化、测试和 CI 计划。
- `docs/development/TESTING.md`：测试目标、测试类型、当前覆盖范围、运行方式和后续补测清单。
- `docs/development/RELEASE.md`：发布与上线思路、Windows 发布目录生成方式和发布前检查。
- `.github/workflows/ci.yml`：GitHub Actions 基础 CI，执行还原、构建和测试。
- `tools/publish-windows.ps1`：生成 Windows 发布目录的本地脚本。
- `src/ClickAssistant.App/`：WPF 桌面端界面层，负责窗口、命令和 ViewModel。
- `src/ClickAssistant.Domain/`：领域层，负责任务、步骤、执行日志和值对象。
- `src/ClickAssistant.Application/`：应用服务层，负责任务编排、执行引擎和抽象接口。
- `src/ClickAssistant.Infrastructure/`：基础设施层，负责 SQLite 存储、Windows 鼠标适配、键盘适配和全局快捷键适配。
- `mobile/android/`：Android 可行性原型工程，验证辅助功能授权、固定坐标点击、停止入口和本地任务保存。
- `tests/ClickAssistant.Tests/`：自动化测试工程，覆盖领域模型校验、执行引擎状态流转和 SQLite 仓储。
- `WORKLOG.md`：每次工作完成后的记录。
- `CHANGELOG.md`：版本、功能、修复和重要文档变更记录。
- `ERRORLOG.md`：开发过程中遇到的非显而易见错误、原因分析和修复方式的历史记录。

## 目录结构

- 根目录：只保留项目入口文档、执行规则、风格规范、工作记录和更新日志。
- `docs/planning/`：存放规划、需求分析和低保真原型相关文档。
- `docs/architecture/`：存放架构设计和技术方案文档。
- `docs/design/`：存放详细设计、交互设计和界面设计文档。
- `docs/development/`：存放开发实施、工程初始化和编码计划文档。
- `src/`：存放项目源代码，按 App、Domain、Application、Infrastructure 分层组织。
- `mobile/`：存放移动端原型和后续移动端工程。
- `tests/`：存放测试代码，并尽量与 `src/` 保持对应层级。

## 下一步

下一阶段建议双线推进：一条线对 Windows 多页面任务中心和悬浮窗做手动冒烟验证，并继续完善安装包、GitHub Releases、异常提示、快捷键冲突降级测试和更多 UI 交互测试；另一条线通过 USB 在 Android 真机上验证辅助功能授权、点击与文本输入、坐标拾取、悬浮暂停/停止、任务持久化和不同系统界面的兼容性。
