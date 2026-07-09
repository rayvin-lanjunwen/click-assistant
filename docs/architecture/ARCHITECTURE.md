# Click Assistant 架构设计

文档版本：v0.1.0
最后更新：2026-07-09
维护人：项目负责人
当前状态：初稿，待确认

本文档用于记录 Click Assistant 首版 MVP 的概要架构设计，包括技术选型、系统分层、模块职责、数据存储和关键接口方向。

## 1. 设计结论

- 首版平台：仅支持 Windows。
- 开发语言：C#。
- `.NET` 版本策略：使用本机已安装的稳定版本，工程初始化前必须确认 `dotnet` 命令可用。
- 桌面框架：WPF（Windows Presentation Foundation，Windows 桌面表现层框架）。
- 后续候选：WinUI 可作为未来视觉升级或重构方案，但不进入首版 MVP。
- 坐标模式：首版使用屏幕绝对坐标。
- 快捷键：必须支持全局快捷键，尤其是立即停止。
- 数据存储：SQLite。
- 数据访问方式：轻量手写 SQL，不使用 Entity Framework Core（EF Core，实体框架核心）。
- 多显示器：首版不支持多显示器专项适配。
- 导入导出：首版不支持任务导入导出。
- UI 风格：优雅简洁，参考透明毛玻璃感，但优先保证可读性和稳定性。

## 2. 技术选型

### 2.1 C# 与 WPF

选择 C# + WPF 的原因：

- 与 Windows 平台集成稳定，适合本项目首版只支持 Windows 的目标。
- 支持成熟的 MVVM（Model-View-ViewModel，模型-视图-视图模型）开发模式。
- 便于实现全局快捷键、窗口控制、托盘和本地文件访问。
- 生态成熟，便于后续打包、测试和维护。

暂不选择 WinUI 的原因：

- WinUI 在视觉表现上更现代，但项目初期更需要稳定的桌面能力和更低的实现风险。
- WPF 对传统桌面工具的开发资料、问题解决路径和第三方库支持更成熟。
- 毛玻璃效果可以在 WPF 中通过窗口透明、背景模糊或近似视觉方案实现。

### 2.2 SQLite

SQLite 用于存储任务、步骤、设置和执行日志。

选择原因：

- 本地单文件数据库，部署简单。
- 适合结构化数据，例如任务、步骤和执行记录。
- 支持事务，便于保证保存任务和步骤时的数据一致性。
- 后续如需导入导出或数据迁移，也有清晰的扩展路径。

首版采用轻量手写 SQL，不使用 Entity Framework Core。所有 SQL 执行必须使用参数化查询，避免字符串拼接造成安全和维护风险。

### 2.3 输入与系统能力

首版需要使用 Windows API 实现以下能力：

- 鼠标点击执行。
- 键盘按键、组合键和文本输入执行。
- 当前鼠标坐标读取。
- 全局快捷键注册。
- 窗口置顶或运行状态提示。

具体实现可封装在系统适配层中，避免系统调用散落在界面代码里。

## 3. 总体架构

系统采用分层架构：

```text
┌──────────────────────────────────────────────┐
│ 表现层 Presentation                           │
│ WPF 窗口、控件、样式、ViewModel               │
├──────────────────────────────────────────────┤
│ 应用层 Application                            │
│ 任务用例、步骤用例、运行控制、设置管理        │
├──────────────────────────────────────────────┤
│ 领域层 Domain                                 │
│ Task、Step、Execution、规则校验、状态模型     │
├──────────────────────────────────────────────┤
│ 基础设施层 Infrastructure                     │
│ SQLite、Windows API、日志、配置、时间服务     │
└──────────────────────────────────────────────┘
```

## 4. 模块划分

### 4.1 任务管理模块

职责：

- 创建、编辑、删除和复制任务。
- 管理任务基础信息。
- 维护任务与步骤之间的关系。

### 4.2 步骤配置模块

职责：

- 新增、编辑、删除、排序输入步骤。
- 管理动作类型、坐标、点击类型、键盘按键、组合键、文本内容、连按次数、等待时间和启用状态。
- 校验步骤参数是否完整。

### 4.3 执行引擎模块

职责：

- 按任务配置执行鼠标点击步骤、键盘按键步骤、组合键步骤和文本输入步骤。
- 处理启动、暂停、继续、停止状态。
- 保证立即停止优先级最高。
- 记录执行结果。

### 4.4 坐标采集模块

职责：

- 读取当前鼠标屏幕绝对坐标。
- 将确认后的坐标写入当前步骤。
- 支持取消采集。

### 4.5 快捷键模块

职责：

- 注册全局快捷键。
- 监听启动、暂停和停止命令。
- 在应用关闭时释放快捷键注册。

### 4.6 数据存储模块

职责：

- 管理 SQLite 数据库连接。
- 提供任务、步骤、设置和日志的数据访问。
- 执行数据库初始化和版本迁移。

### 4.7 UI/UX 模块

职责：

- 提供主窗口、任务编辑、步骤编辑、坐标采集和设置页面。
- 实现优雅简洁的透明毛玻璃视觉风格。
- 保证运行状态和停止按钮足够醒目。

## 5. 数据库设计概览

首版数据库建议包含以下表：

- `tasks`：点击任务。
- `task_steps`：输入步骤，包含鼠标点击、键盘按键、组合键和文本输入配置。
- `execution_logs`：执行日志。
- `app_settings`：应用设置。
- `schema_migrations`：数据库版本迁移记录。

## 6. 项目代码结构建议

```text
src/
  ClickAssistant.App/
    App.xaml
    MainWindow.xaml
    Views/
    ViewModels/
    Resources/
  ClickAssistant.Application/
    Services/
    UseCases/
    Dtos/
  ClickAssistant.Domain/
    Entities/
    Enums/
    ValueObjects/
  ClickAssistant.Infrastructure/
    Persistence/
    Windows/
    Logging/
  ClickAssistant.Tests/
    Unit/
    Integration/
```

说明：

- `ClickAssistant.App` 负责 WPF 界面和依赖注入启动。
- `ClickAssistant.Application` 负责应用用例编排。
- `ClickAssistant.Domain` 负责核心实体和业务规则。
- `ClickAssistant.Infrastructure` 负责 SQLite、Windows API 和日志等外部能力。
- `ClickAssistant.Tests` 负责测试代码。

## 7. 关键接口方向

### 7.1 任务仓储接口

```csharp
public interface IClickTaskRepository
{
    Task<IReadOnlyList<ClickTask>> GetAllAsync();
    Task<ClickTask?> GetByIdAsync(Guid taskId);
    Task SaveAsync(ClickTask task);
    Task DeleteAsync(Guid taskId);
}
```

### 7.2 执行引擎接口

```csharp
public interface IClickExecutionEngine
{
    Task StartAsync(Guid taskId, CancellationToken cancellationToken);
    Task PauseAsync();
    Task ResumeAsync();
    Task StopAsync();
}
```

### 7.3 坐标采集接口

```csharp
public interface ICursorPositionService
{
    ScreenPoint GetCurrentPosition();
}
```

### 7.4 键盘输入接口

```csharp
public interface IKeyboardInputService
{
    Task PressKeyAsync(string keyName, int pressCount, int intervalMs, CancellationToken cancellationToken);
    Task PressShortcutAsync(string shortcutKeys, CancellationToken cancellationToken);
    Task TypeTextAsync(string text, int intervalMs, CancellationToken cancellationToken);
}
```

### 7.5 快捷键接口

```csharp
public interface IGlobalHotkeyService
{
    void RegisterHotkeys();
    void UnregisterHotkeys();
}
```

## 8. 架构约束

- 表现层不得直接访问 SQLite。
- 表现层不得直接调用 Windows API 执行点击或键盘输入。
- 执行引擎必须通过取消令牌和状态机支持立即停止。
- 数据库结构必须预留迁移机制。
- 所有外部系统能力必须通过接口封装，方便测试和替换。

## 9. 待后续确认

- WPF 毛玻璃效果采用原生窗口模糊、半透明背景，还是先使用近似视觉方案。
- 是否需要托盘运行。
- 是否需要应用开机自启动。
- 是否需要提供完整历史日志页面。
