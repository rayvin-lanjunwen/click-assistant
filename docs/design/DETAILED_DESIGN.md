# Click Assistant 详细设计

文档版本：v0.1.0
最后更新：2026-07-09
维护人：项目负责人
当前状态：初稿，待确认

本文档用于记录 Click Assistant 首版 MVP 的模块内部逻辑、核心数据结构、状态流转和异常处理设计。

## 1. 领域模型

### 1.1 点击任务

`ClickTask` 表示一个可执行的点击任务。

字段建议：

- `Id`：任务唯一标识。
- `Name`：任务名称。
- `Description`：任务描述。
- `Enabled`：是否启用。
- `RepeatCount`：重复次数。
- `StartDelayMs`：启动前延迟毫秒数。
- `Steps`：输入步骤列表。
- `CreatedAt`：创建时间。
- `UpdatedAt`：更新时间。

规则：

- 任务名称不能为空。
- `RepeatCount` 必须大于或等于 1。
- `StartDelayMs` 不能小于 0。
- 至少存在一个启用的输入步骤时，任务才允许启动。

### 1.2 输入步骤

`ClickStep` 表示一次输入动作，可以是鼠标点击，也可以是键盘按键。

字段建议：

- `Id`：步骤唯一标识。
- `TaskId`：所属任务标识。
- `Name`：步骤名称。
- `Enabled`：是否启用。
- `ActionType`：输入动作类型。
- `X`：屏幕绝对横坐标。
- `Y`：屏幕绝对纵坐标。
- `ClickType`：点击类型。
- `KeyName`：键盘按键名称。
- `KeyPressCount`：键盘连按次数。
- `KeyIntervalMs`：键盘连按间隔。
- `BeforeDelayMs`：执行前等待毫秒数。
- `AfterDelayMs`：执行后间隔毫秒数。
- `Order`：执行顺序。

规则：

- 鼠标点击步骤的坐标必须为有效整数。
- 键盘按键步骤必须填写按键名称，连按次数必须大于或等于 1。
- `BeforeDelayMs` 和 `AfterDelayMs` 不能小于 0。
- 同一任务下的 `Order` 应保持唯一。

### 1.3 执行日志

`ExecutionLog` 表示一次任务执行记录。

字段建议：

- `Id`：日志唯一标识。
- `TaskId`：关联任务标识。
- `Status`：执行结果。
- `StartedAt`：开始时间。
- `EndedAt`：结束时间。
- `Message`：结果说明。

## 2. 枚举设计

### 2.1 点击类型

```csharp
public enum ClickType
{
    LeftSingle,
    LeftDouble,
    RightSingle
}
```

### 2.2 输入动作类型

```csharp
public enum InputActionType
{
    MouseClick,
    KeyboardPress
}
```

### 2.3 执行状态

```csharp
public enum ExecutionStatus
{
    Idle,
    Starting,
    Running,
    Paused,
    Completed,
    Stopped,
    Failed
}
```

## 3. 执行引擎设计

执行引擎是首版最重要的模块，应独立于界面实现。

### 3.1 状态流转

```text
Idle -> Starting -> Running -> Completed -> Idle
Idle -> Starting -> Running -> Paused -> Running
Running -> Stopped -> Idle
Paused -> Stopped -> Idle
Running -> Failed -> Idle
```

规则：

- `Stop` 的优先级最高，任何状态下都应尽快响应。
- `Pause` 只对 `Running` 状态有效。
- `Resume` 只对 `Paused` 状态有效。
- 任务执行失败后应进入 `Failed`，记录错误信息，再回到 `Idle`。

### 3.2 执行流程

1. 根据任务标识读取任务和步骤。
2. 校验任务是否可执行。
3. 记录执行开始日志。
4. 等待启动倒计时。
5. 按重复次数进入循环。
6. 按步骤顺序执行启用步骤。
7. 每一步执行前检查停止请求。
8. 每一步执行前处理暂停状态。
9. 根据动作类型执行鼠标点击或键盘按键。
10. 写入步骤执行状态。
11. 任务完成后写入完成日志。

### 3.3 暂停与停止

- 暂停通过状态标记控制，不应中断数据库写入。
- 停止通过 `CancellationToken` 和执行状态共同控制。
- 每次等待和每次点击前后都应检查停止请求。
- 停止后应记录“用户停止任务”日志。

### 3.4 输入执行

首版输入执行基于 Windows API。

设计要求：

- 鼠标移动到目标屏幕绝对坐标。
- 根据点击类型执行单击、双击或右键单击。
- 将键盘按键名称解析为 Windows 虚拟键码。
- 按配置次数执行键盘按下和抬起，多次连按之间按配置间隔等待。
- 输入执行前后均应短暂检查停止状态。
- 输入执行失败时，应停止任务并记录失败原因。

## 4. 坐标采集设计

### 4.1 采集流程

1. 用户点击“采集坐标”。
2. 应用进入坐标采集模式。
3. 界面实时展示当前鼠标坐标。
4. 用户点击确认。
5. 当前坐标写入步骤编辑表单。

### 4.2 坐标规则

- 首版只记录屏幕绝对坐标。
- 首版不做多显示器专项适配。
- 坐标采集时应提示“当前仅支持主显示器场景”。
- 后续可扩展窗口相对坐标。

## 5. 快捷键设计

### 5.1 首版快捷键

- 立即停止：`Ctrl + Alt + S`。

### 5.2 处理规则

- 应用启动时注册全局快捷键。
- 应用关闭时释放全局快捷键。
- 快捷键注册失败时，应提示用户并允许继续使用界面按钮。
- 立即停止快捷键必须在任务执行期间保持可用。

## 6. 数据库详细设计

### 6.1 `tasks`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 任务唯一标识 |
| `name` | TEXT | 任务名称 |
| `description` | TEXT | 任务描述 |
| `enabled` | INTEGER | 是否启用 |
| `repeat_count` | INTEGER | 重复次数 |
| `start_delay_ms` | INTEGER | 启动前延迟 |
| `created_at` | TEXT | 创建时间 |
| `updated_at` | TEXT | 更新时间 |

### 6.2 `task_steps`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 步骤唯一标识 |
| `task_id` | TEXT | 所属任务 |
| `name` | TEXT | 步骤名称 |
| `enabled` | INTEGER | 是否启用 |
| `action_type` | TEXT | 输入动作类型 |
| `x` | INTEGER | 屏幕绝对横坐标 |
| `y` | INTEGER | 屏幕绝对纵坐标 |
| `click_type` | TEXT | 点击类型 |
| `key_name` | TEXT | 键盘按键名称 |
| `key_press_count` | INTEGER | 键盘连按次数 |
| `key_interval_ms` | INTEGER | 键盘连按间隔 |
| `before_delay_ms` | INTEGER | 执行前等待 |
| `after_delay_ms` | INTEGER | 执行后间隔 |
| `step_order` | INTEGER | 执行顺序 |

### 6.3 `execution_logs`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 日志唯一标识 |
| `task_id` | TEXT | 关联任务 |
| `status` | TEXT | 执行结果 |
| `started_at` | TEXT | 开始时间 |
| `ended_at` | TEXT | 结束时间 |
| `message` | TEXT | 结果说明 |

### 6.4 `app_settings`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | TEXT | 设置键 |
| `value` | TEXT | 设置值 |
| `updated_at` | TEXT | 更新时间 |

## 7. ViewModel 设计

### 7.1 `MainWindowViewModel`

职责：

- 管理任务列表。
- 管理当前选中任务。
- 暴露启动、暂停、继续、停止命令。
- 展示当前执行状态和最近日志。

### 7.2 `TaskEditorViewModel`

职责：

- 编辑任务名称、描述、重复次数和启动延迟。
- 管理步骤列表。
- 校验任务表单。

### 7.3 `StepEditorViewModel`

职责：

- 编辑步骤名称、动作类型、坐标、点击类型、键盘按键、连按次数和间隔。
- 调用坐标采集服务。
- 校验步骤表单。

### 7.4 `SettingsViewModel`

职责：

- 管理默认步骤间隔。
- 管理默认开始倒计时。
- 管理快捷键设置。
- 管理安全选项。

## 8. 异常处理

- 任务不存在：提示任务已删除或不可用。
- 无可执行步骤：提示需要至少启用一个步骤。
- 快捷键注册失败：提示快捷键被占用。
- 输入执行失败：停止任务并记录失败日志。
- 数据库读写失败：提示保存失败或读取失败，保留当前界面输入。

## 9. 测试重点

- 任务保存、编辑、删除和复制。
- 步骤排序和参数校验。
- 执行状态流转。
- 暂停、继续和停止优先级。
- SQLite 数据读写。
- 快捷键注册失败时的降级行为。
