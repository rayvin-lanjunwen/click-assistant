# Click Assistant 发布与上线说明

文档版本：v1.0.0
最后更新：2026-07-09
维护人：项目负责人

本文档用于说明 Click Assistant 的部署与上线思路、当前 Windows 发布目录生成方式、发布前检查和后续发布演进方向。

## 1. 发布目标

部署与上线是将测试通过的软件发布到可使用环境，让最终用户可以开始使用的过程。传统服务端软件通常包含准备服务器、配置环境、迁移数据和监控上线状态等步骤。

Click Assistant 当前是 Windows 本地桌面应用，现阶段发布目标是：

- 生成 Windows 可执行程序和发布目录。
- 让用户可以在目标电脑上启动应用。
- 保持用户数据存放在本机 `%LOCALAPPDATA%\ClickAssistant\clickassistant.db`。
- 暂不生成安装包，暂不自动发布到 GitHub Releases。

## 2. 发布类型

当前支持两类发布方式：

- 自包含发布：发布目录包含 .NET 运行时，目标电脑通常不需要额外安装 .NET。
- 依赖框架发布：发布目录更小，但目标电脑需要安装兼容的 .NET 桌面运行时。

当前默认采用自包含发布，优先降低最终用户的运行门槛。

## 3. 发布前检查

每次发布前应至少完成以下检查：

```powershell
& "$env:USERPROFILE\.dotnet\dotnet.exe" restore ClickAssistant.slnx
& "$env:USERPROFILE\.dotnet\dotnet.exe" build ClickAssistant.slnx --configuration Release --no-restore
& "$env:USERPROFILE\.dotnet\dotnet.exe" test ClickAssistant.slnx --configuration Release --no-build
```

检查通过后再生成发布目录。

## 4. 生成 Windows 发布目录

推荐使用项目脚本：

```powershell
.\tools\publish-windows.ps1
```

如果当前 PowerShell 执行策略阻止脚本运行，可以使用一次性绕过命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\publish-windows.ps1
```

默认输出目录：

```text
dist/ClickAssistant-win-x64/
```

默认可执行文件：

```text
dist/ClickAssistant-win-x64/ClickAssistant.App.exe
```

如果需要生成依赖框架发布，可以执行：

```powershell
.\tools\publish-windows.ps1 -FrameworkDependent
```

如果需要指定输出目录，可以执行：

```powershell
.\tools\publish-windows.ps1 -OutputPath "dist\ClickAssistant-custom"
```

## 5. 发布目录验证

生成发布目录后，应验证以下内容：

- 发布目录中存在 `ClickAssistant.App.exe`。
- 应用可以启动主窗口。
- 首次启动可以创建本地数据库。
- 可以创建任务并保存。
- 可以执行鼠标点击、键盘单键、组合键和文本输入步骤。
- 停止按钮和全局停止快捷键可用。

涉及真实鼠标点击和键盘输入的验证应在安全环境中执行。

## 6. 数据迁移说明

当前用户数据默认存放在：

```text
%LOCALAPPDATA%\ClickAssistant\clickassistant.db
```

当前版本的数据库初始化逻辑会在启动时自动创建表结构，并为旧数据库补充新增列。发布新版本时，通常不需要手动迁移数据。

后续如果出现破坏性数据库变更，应增加明确的迁移脚本、备份说明和回滚方案。

## 7. 后续计划

后续发布能力建议按以下顺序推进：

1. 生成 Windows 安装包。
2. 为发布产物添加版本号和构建信息。
3. 在 CI 中生成发布产物并上传 artifact。
4. 通过 Git Tag 触发 GitHub Releases。
5. 增加发布前冒烟测试清单。
