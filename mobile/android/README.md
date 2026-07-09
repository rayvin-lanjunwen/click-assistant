# Click Assistant Android 原型工程

本文档用于说明 `mobile/android/` Android 可行性原型工程。

## 原型目标

第一阶段只验证四个关键点：

- 辅助功能服务授权。
- 固定坐标点击。
- 首页停止入口。
- 本地任务保存和恢复。

## 技术说明

该原型为了降低验证成本，暂时采用依赖较少的 Android 原生实现：

- Java。
- Android framework View。
- `SharedPreferences` 本地保存任务。
- `AccessibilityService` 分发固定坐标点击手势。

后续产品化阶段可迁移到 Kotlin、Jetpack Compose 和 Room。

## 本地运行

当前仓库机器未检测到 Android SDK、Gradle、ADB 或 Android Studio，因此本次只提交工程源码，未在本地生成 APK。

安装 Android Studio 后，可打开 `mobile/android/`，同步 Gradle 项目并使用 Android Studio 生成 debug APK。

当前仓库还未提交 Gradle Wrapper。如果本机已经安装 Gradle，也可以执行：

```powershell
gradle :app:assembleDebug
```

后续工具链就绪后，可在 `mobile/android/` 下生成 Gradle Wrapper，再补充：

```powershell
gradle wrapper
```

## 真机验证

1. 安装 debug APK 到 Android 真机。
2. 打开应用，点击“打开辅助功能设置”。
3. 在系统设置中启用“Click Assistant 移动原型”。
4. 回到应用，设置坐标和重复次数。
5. 点击“保存任务”。
6. 点击“开始执行”。
7. 运行中点击“停止”，确认后续点击停止。

涉及真实点击时，应先在安全界面中验证，例如空白桌面或测试页面。
