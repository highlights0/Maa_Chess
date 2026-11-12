# Maa_Chess Android App

Maa_Chess 的 Android 原生应用。该工程直接基于 [Aliothmoon/MAA-Meow](https://github.com/Aliothmoon/MAA-Meow) Android 工程迁移和改造，优先复用其成熟基础设施：

- Jetpack Compose / Material 3 UI。
- 设备管理与连接状态。
- Shizuku / Root 权限管理。
- 后台虚拟显示器、目标应用启动、Surface 画面桥接和定向触控。
- MAA Core 原生库装载、日志与任务生命周期。

## 当前改造内容

- 应用名称：Maa_Chess。
- Android applicationId：`com.highlights0.maachess`。
- Kotlin namespace 暂时保留为 `com.aliothmoon.maameow`，避免一次性重命名 AIDL 和全部源文件。
- 主导航：首页、任务、棋谱、设置。
- 前台模式通过 Android PackageManager 启动天天象棋，不依赖 Shizuku / Root。
- 后台模式复用 MAA-Meow 的虚拟显示和远端启动服务；已运行于主屏的游戏会尝试移入目标虚拟显示器。
- 天天象棋包名：`com.tencent.qqgame.xq`，可在设置中覆盖。
- 天天象棋后台显示初始分辨率：900×1600，240 DPI。
- 已加入 ONNX Runtime Android，nano-v3 模型按需下载到应用私有目录并进行 SHA-256 校验。
- 已加入棋盘识别入口：从后台预览 Surface 获取 JPEG 帧，按 MuMu 四角校准进行模型预处理，展示棋位结果、FEN 棋盘串和不确定位置。
- 标准对局和残局闯关入口已建立；当前选择只表示测试场景，不会自动操作游戏内菜单。

## 重要架构边界

MAA-Meow 中的 Arknights `MaaCore` 任务类型（`StartUp`、`Fight`、`Copilot` 等）不适用于象棋。当前改造复用它的设备、显示器、Surface、权限和应用生命周期能力；象棋识别通过独立 XiangqiRecognitionService 执行，不能调用 Arknights 任务链来声称完成象棋自动化。后续棋局调度和输入执行必须接入本项目自己的 MaaFramework pipeline / custom action、BoardState、规则验证和 Pikafish 引擎。

## 构建环境

- JDK 25（Eclipse Temurin）。
- Android SDK Platform 37。
- Android NDK 29.0.14206865。
- CMake 3.22.1。
- Python 3.12（下载和部署 MAA Core 预编译资源）。

## 下载 MAA Core 依赖

在本目录执行：

```powershell
python scripts/setup_maa_core.py --clean
```

该脚本下载 MAA Core Android 预编译库和资源，并部署到 `app/src/main/jniLibs` 与 `app/src/main/assets/MaaSync/MaaResource`。这些生成文件不应提交到 Git。

## 编译与测试

MuMu 使用 x86_64，默认同时构建 arm64-v8a 与 x86_64：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

单独构建某种 ABI：

```powershell
.\gradlew.bat assembleDebug -Pmaa.abi=x86_64
.\gradlew.bat assembleDebug -Pmaa.abi=arm64-v8a
```

Debug APK：

`app/build/outputs/apk/debug/app-debug.apk`

## 许可证

本 Android 工程是 MAA-Meow 的衍生工程，遵循 AGPL-3.0。请阅读本目录的 `LICENSE` 和 `docs/zh-cn/develop/THIRD_PARTY_NOTICES.md`。分发修改后的应用时，必须履行 AGPL-3.0 对应的完整源代码提供义务。
