# Click Assistant 阅读说明

Click Assistant 是一个本地点击与键盘输入辅助工具，目标是帮助用户把重复性鼠标点击和键盘连按操作配置成可复用任务，并支持启动、暂停、停止和查看执行日志。

当前项目已进入编码实现阶段，首版 C# + WPF MVP 已完成基础工程骨架、任务管理、步骤配置、坐标捕获、SQLite 存储、鼠标点击执行、键盘连按执行、全局停止快捷键、执行前安全确认、快捷键自定义、核心自动化测试和基础 CI。

## 当前阶段

- 已建立项目执行规则：`RULE.md`
- 已建立项目风格规范：`STYLEGUIDE.md`
- 已建立需求规格说明：`docs/planning/REQUIREMENTS.md`
- 已建立低保真原型蓝图：`docs/planning/PROTOTYPE.md`
- 已建立架构设计：`docs/architecture/ARCHITECTURE.md`
- 已建立详细设计：`docs/design/DETAILED_DESIGN.md`
- 已建立 UI/UX 设计：`docs/design/UI_UX_DESIGN.md`
- 已建立编码实施计划：`docs/development/IMPLEMENTATION_PLAN.md`
- 已创建 WPF 桌面应用工程：`src/ClickAssistant.App/`
- 已创建领域层工程：`src/ClickAssistant.Domain/`
- 已创建应用服务层工程：`src/ClickAssistant.Application/`
- 已创建基础设施层工程：`src/ClickAssistant.Infrastructure/`
- 已创建自动化测试工程：`tests/ClickAssistant.Tests/`
- 已建立 GitHub Actions 基础 CI：`.github/workflows/ci.yml`
- 已建立工作记录：`WORKLOG.md`
- 已建立更新日志：`CHANGELOG.md`

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

应用数据默认存放在 `%LOCALAPPDATA%\ClickAssistant\clickassistant.db`。

全局停止快捷键默认为 `Ctrl + Alt + S`，用于在窗口失焦时请求立即停止当前任务。用户可在执行面板中修改停止快捷键，设置会保存到本地 SQLite `app_settings` 表。

启动任务前会显示执行前安全确认，提醒用户检查坐标、按键、重复次数和开始延迟。任务启动中、运行中和暂停时，任务配置区会被锁定，避免运行期间修改配置造成误操作。

键盘步骤支持常用单键连按，例如 `A-Z`、`0-9`、`F1-F24`、`Enter`（回车）、`Space`（空格）、`Tab`、`Esc`、方向键、`Delete`（删除）和 `Backspace`（退格）。

## 测试说明

测试是保障软件质量的关键环节，应贯穿整个开发过程，而不是只在编码结束后集中补充。详细测试目标、测试类型、当前覆盖范围、运行方式和后续补测清单参见 `docs/development/TESTING.md`。

当前已建立 `tests/ClickAssistant.Tests/` 自动化测试工程，首批覆盖领域模型校验、执行引擎状态流转和 SQLite 仓储。

## 文档说明

- `RULE.md`：项目执行规则，优先级最高。
- `STYLEGUIDE.md`：文档、代码、日志、提交和目录风格规范。
- `docs/planning/REQUIREMENTS.md`：规划与需求分析阶段的需求规格说明书。
- `docs/planning/PROTOTYPE.md`：产品低保真页面蓝图。
- `docs/architecture/ARCHITECTURE.md`：技术选型、系统分层、模块职责和接口方向。
- `docs/design/DETAILED_DESIGN.md`：核心模块、数据结构、执行状态和异常处理设计。
- `docs/design/UI_UX_DESIGN.md`：界面风格、页面结构、交互状态和视觉约束。
- `docs/development/IMPLEMENTATION_PLAN.md`：编码阶段实施顺序、工程初始化、测试和 CI 计划。
- `docs/development/TESTING.md`：测试目标、测试类型、当前覆盖范围、运行方式和后续补测清单。
- `.github/workflows/ci.yml`：GitHub Actions 基础 CI，执行还原、构建和测试。
- `src/ClickAssistant.App/`：WPF 桌面端界面层，负责窗口、命令和 ViewModel。
- `src/ClickAssistant.Domain/`：领域层，负责任务、步骤、执行日志和值对象。
- `src/ClickAssistant.Application/`：应用服务层，负责任务编排、执行引擎和抽象接口。
- `src/ClickAssistant.Infrastructure/`：基础设施层，负责 SQLite 存储、Windows 鼠标适配、键盘适配和全局快捷键适配。
- `tests/ClickAssistant.Tests/`：自动化测试工程，覆盖领域模型校验、执行引擎状态流转和 SQLite 仓储。
- `WORKLOG.md`：每次工作完成后的记录。
- `CHANGELOG.md`：版本、功能、修复和重要文档变更记录。

## 目录结构

- 根目录：只保留项目入口文档、执行规则、风格规范、工作记录和更新日志。
- `docs/planning/`：存放规划、需求分析和低保真原型相关文档。
- `docs/architecture/`：存放架构设计和技术方案文档。
- `docs/design/`：存放详细设计、交互设计和界面设计文档。
- `docs/development/`：存放开发实施、工程初始化和编码计划文档。
- `src/`：存放项目源代码，按 App、Domain、Application、Infrastructure 分层组织。
- `tests/`：存放测试代码，并尽量与 `src/` 保持对应层级。

## 下一步

下一阶段建议继续扩展组合键/文本输入、应用打包发布、更完整的异常提示和更多 UI 交互测试。
