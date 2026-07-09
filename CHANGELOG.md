# Click Assistant 更新日志

本文档用于记录项目中值得追踪的版本、功能、修复和重要变更，并与 GitHub Releases（GitHub 发布页面）保持同步。

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
