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
- 已加入 ONNX Runtime Android，nano-v3 模型随 APK 分发，首次运行离线安装到应用私有目录并进行 SHA-256 校验；不再要求手机访问模型站点。
- 已加入棋盘识别入口：后台从虚拟显示采帧，前台经无障碍服务截图；分别使用已实测的 MuMu 900×1600 与手机 1080×2400 四角校准。棋盘展示中文棋子名、FEN 和不确定位置。
- 已在 ARM64 真机标准开局验证 Pikafish 建议入口：完整识别后显示走法箭头、ICCS 坐标、深度、评分和参考变化；新棋盘自动切换分析基准，无需勾选新局确认。
- 已加入离线场景单步落子入口：输入前用新截图与已识别的视觉参照核对棋盘及轮次，输入后完整识别复核；轮次不明时暂停，不会自动猜测。
- 标准对局和残局闯关入口已建立；当前选择只表示测试场景，不会自动操作游戏内菜单。
- 前台悬浮操作面板已改为象棋识别、棋盘与建议走法界面，与任务页共用状态；截图和触摸期间隐藏浮层，不要求 Root / Shizuku 或外部存储权限。
- 已加入本地棋规与整盘变化校验。标准开局自动确定红先；内部状态只接受唯一合法的一着或两着变化，用户无需维护棋局记录。
- 前台面板默认收起棋盘详情；推荐箭头持续显示，通过轻量截图监测棋盘，检测到变化后清除；不会因应用自身浮层窗口事件误判游戏离开前台。
- 已加入“识别并走一着”和持续自动下棋，删除两项确认勾选。前台悬浮球 / 音量组合键、任务页和通知栏均可停止，停止后不继续发下一次触摸。
- 识别、分析、输入和停止原因始终写入 `XiangqiFlow` 日志；分别记录触摸耗时和完整截图复核耗时。最新功能和两秒输入目标仍由用户实机验收。
- 中途接入使用显式标记的搜索基准，`historyKnown=false`；不把基准中的计数作为真实 rule60，也不据此做历史重复局面裁定。

## 重要架构边界

MAA-Meow 中的 Arknights `MaaCore` 任务类型（`StartUp`、`Fight`、`Copilot` 等）不适用于象棋。当前改造复用它的设备、显示器、Surface、权限和应用生命周期能力；象棋识别通过独立 XiangqiRecognitionService 执行，不能调用 Arknights 任务链来声称完成象棋自动化。后续棋局调度和输入执行必须接入本项目自己的 MaaFramework pipeline / custom action、BoardState、规则验证和 Pikafish 引擎。

## 构建环境

- JDK 25（Eclipse Temurin）。
- Android SDK Platform 37。
- Android NDK 29.0.14206865。
- CMake 3.22.1。
- `pip install py7zr==1.1.4`，再运行 `python scripts/setup_pikafish.py`，将校验后的官方引擎和 NNUE 放入 Git 忽略的 APK 构建输入目录。x86_64 APK 可安装，但当前官方发布包没有 Android x86_64 引擎。
- Python 3.12（下载和部署 MAA Core 预编译资源）。

## 下载 MAA Core 依赖

在本目录执行：

```powershell
python scripts/setup_maa_core.py --clean
python ../src/maa_xiangqi/model_download.py --output app/src/main/assets/xiangqi-nano-v3.onnx
```

该脚本下载 MAA Core Android 预编译库和资源，并部署到 `app/src/main/jniLibs` 与 `app/src/main/assets/MaaSync/MaaResource`。它的 `--clean` 会清空生成的原生库，所以完成后再运行 `python scripts/setup_pikafish.py`。这些生成文件不应提交到 Git。

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
