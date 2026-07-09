# Click Assistant 测试说明

文档版本：v1.0.0
最后更新：2026-07-09
维护人：项目负责人

本文档用于集中说明 Click Assistant 的测试目标、测试类型、当前覆盖范围、运行方式和后续补测方向。测试应贯穿整个开发过程，而不是只在编码结束后集中补充。

## 1. 测试目标

测试是保障软件质量的关键环节，当前项目测试目标包括：

- 发现并修复代码中的错误（Bug）。
- 确保功能实现符合需求规格和设计文档。
- 保护核心业务逻辑，避免后续迭代引入回归问题。
- 验证真实输入执行前后的状态流转、数据保存和异常处理。
- 逐步覆盖性能、安全、稳定性和可维护性等质量要求。

## 2. 测试类型

### 2.1 单元测试

单元测试用于验证最小代码单元是否符合预期，优先覆盖不依赖 UI、不依赖系统 API 的纯逻辑。

当前重点：

- 领域模型校验。
- 值对象规则。
- 执行状态判断。
- 参数边界和异常分支。

### 2.2 集成测试

集成测试用于验证模块之间的交互是否正确，尤其是应用层、基础设施层和数据库之间的协作。

当前重点：

- SQLite 数据库初始化。
- 任务仓储保存、读取和删除。
- 执行日志写入和读取。
- 设置仓储读取和保存。

### 2.3 系统测试

系统测试用于验证完整应用流程是否符合用户使用场景。

当前重点：

- 创建任务、编辑步骤并保存。
- 捕获坐标并执行鼠标点击任务。
- 配置键盘步骤并执行连按。
- 启动、暂停、继续和停止任务。
- 使用全局停止快捷键中止执行。
- 修改停止快捷键并验证持久化。

系统测试涉及真实鼠标点击和键盘输入，执行前必须确认目标环境安全，避免误操作。

### 2.4 用户验收测试

用户验收测试由用户基于真实场景验证软件是否满足预期。

当前重点：

- 常见重复点击流程是否可以配置并复用。
- 键盘连按是否符合使用习惯。
- 执行前确认和停止机制是否足够清晰。
- 界面信息是否容易理解。

## 3. 当前自动化测试

当前测试工程位于：

```text
tests/ClickAssistant.Tests/
```

当前已覆盖：

- 领域模型校验：任务名称、启用步骤、键盘连按次数等基础规则。
- 执行引擎状态流转：启动、暂停、继续、停止和停止日志写入。
- SQLite 仓储：任务和步骤的保存、读取和删除。

## 4. 运行测试

当前终端如果还没有刷新用户级 PATH，可以使用本机 SDK 完整路径执行：

```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" test ClickAssistant.slnx
```

如果已经可以直接使用 `dotnet` 命令，也可以执行：

```powershell
dotnet test ClickAssistant.slnx
```

建议在以下时机运行测试：

- 修改领域模型或校验规则后。
- 修改执行引擎状态流转后。
- 修改 SQLite 表结构或仓储逻辑后。
- 修改全局快捷键、设置保存或任务执行流程后。
- 提交代码前。

## 5. 测试命名规范

测试类命名建议使用被测对象名称加 `Tests` 后缀，例如：

```text
ClickTaskValidationTests
ClickExecutionEngineTests
SqliteClickTaskRepositoryTests
```

测试方法命名建议表达场景和预期结果，例如：

```text
ValidateForSave_WhenNameIsBlank_ThrowsDomainValidationException
StartPauseResumeAndStop_ShouldUpdateStatusAndWriteStoppedLog
SaveGetAndDeleteAsync_ShouldPersistTaskAndSteps
```

## 6. 测试数据规范

- 测试数据应尽量在测试内部构造，避免依赖用户本机真实数据。
- SQLite 集成测试必须使用临时数据库路径，不得写入 `%LOCALAPPDATA%\ClickAssistant\clickassistant.db`。
- 测试完成后应清理临时文件和连接池。
- 涉及真实鼠标和键盘输入的测试默认不进入自动化测试，应通过系统测试或用户验收测试手动验证。

## 7. 后续补测清单

后续建议优先补充以下测试：

- 快捷键注册失败时的降级和提示。
- 停止快捷键设置保存和重新加载。
- 执行前安全确认的界面流程。
- 执行日志读取排序和展示。
- 任务复制后的步骤归属和排序。
- 组合键输入和文本输入扩展能力。
- UI 交互自动化测试。
- 打包发布后的安装、启动和数据路径验证。

## 8. CI 计划

当前已建立 GitHub Actions 基础 CI：

```text
.github/workflows/ci.yml
```

触发条件：

- 推送到 `main` 分支。
- 推送到 `feature/**` 分支。
- 向 `main` 分支发起 Pull Request。
- 手动触发 `workflow_dispatch`。

CI 检查项：

```text
dotnet restore
dotnet build
dotnet test
```

CI 使用 Windows runner，以保证 WPF 的 `net10.0-windows` 项目可以正常构建。CI 通过后，才能进入 Pull Request 审核和后续发布流程。
