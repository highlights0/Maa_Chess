# Maa_Chess

基于 MAA-Meow Android 基础工程改造的天天象棋自动化项目，目标客户端为腾讯《天天象棋》。

## 当前方向

**Android 原生 App 是主线。** 直接复用 MAA-Meow 已有的设备管理、Shizuku / Root 权限服务、后台虚拟显示、Surface 画面桥接、定向输入和任务生命周期，不再平行重写这些基础设施。

首期场景：
- 标准对局。
- 残局闯关。

产品导航：首页、任务、棋谱、设置。

## 当前已接入

- MAA-Meow Android 工程迁移至 `android-app/`。
- 应用品牌和独立 applicationId：`com.highlights0.maachess`。
- 前台模式通过 Android 启动器直接启动天天象棋，不要求 Shizuku / Root。
- 后台模式沿用 MAA-Meow 的虚拟显示器、远端启动、Surface 预览和 Shizuku / Root 通道。
- 已修复后台模式下游戏已运行于主屏时，重复启动不会移入虚拟显示器的问题。
- 天天象棋包名已在用户提供的 MuMu 诊断中核实：`com.tencent.qqgame.xq`（4.3.0.2）。
- 初始后台分辨率使用用户设备的 900×1600、240 DPI 竖屏配置。
- 标准对局 / 残局闯关入口、棋谱页占位和客户端包名设置入口。

## 尚未完成

- 天天象棋大厅、标准对局和残局闯关的界面状态识别。
- 棋盘自动定位与 90 个交叉点识别。
- 15 类棋子识别模型与置信度校验。
- 完整象棋规则验证和走法历史维护。
- Pikafish Android 引擎接入。
- 起点 / 终点触控、动画等待、走法结果复识别和自动落子闭环。

**重要边界：** 当前 Android 任务页只完成游戏启动、前台/后台显示和预览接入。标准 / 残局选择目前是场景入口，不会自动点击游戏内菜单，也不会自动对弈。MAA-Meow 中的 Arknights `AsstAppendTask` 任务链不是象棋任务引擎；后续象棋识别与执行必须使用本项目的 MaaFramework pipeline / custom action 和独立 Xiangqi Core，不能把 Arknights 任务误当成已接入。

## 仓库结构

```text
Maa_Chess/
├── android-app/       # MAA-Meow Android 基础工程及象棋 UI 改造
├── src/               # Python 开发、校准和视觉算法工具
├── agent/             # MaaFramework Python Agent
├── assets/resource/   # MaaFramework pipeline 配置
├── config/            # 天天象棋与 MuMu 棋盘配置
├── tests/             # Python 单元测试
├── docs/              # 架构、迁移和 Codex 任务书
├── engines/           # Pikafish（后续接入）
└── models/            # 棋子识别模型（后续接入）
```

## 构建 Android App

环境要求：JDK 25、Android SDK Platform 37、NDK 29.0.14206865、CMake 3.22.1、Python 3.12。

PowerShell：

```powershell
cd android-app
python scripts/setup_maa_core.py --clean
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

默认同时构建 arm64-v8a 与 x86_64，兼容 ARM64 Android 真机和 MuMu x86_64。

APK 输出：

`android-app/app/build/outputs/apk/debug/app-debug.apk`

## Python 开发工具

保留 ADB 设备发现、Android 内部截图、MuMu 环境诊断、棋盘四角校准、90 个棋位裁剪、FEN / ICCS 坐标和 MaaFramework Python Agent。

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
pytest
ruff check .
```

## 后台兼容性说明

用户提供的诊断报告确认设备分辨率为 900×1600、240 DPI，已安装天天象棋包名为 `com.tencent.qqgame.xq`。该报告中 Shizuku 未安装、Root 探测未通过，虚拟显示 PoC 尚未测试。因此目前不能声称该设备已验证后台运行；前台启动不依赖这两项权限，后台模式需要先完成授权和实际显示器验证。

## 上游与许可证

Android 工程基于 [Aliothmoon/MAA-Meow](https://github.com/Aliothmoon/MAA-Meow) 迁移和修改，遵循 AGPL-3.0。保留上游 LICENSE 和第三方代码声明。分发衍生版本时，必须提供对应完整源代码并履行 AGPL-3.0 义务。

- [Android 工程说明](android-app/README.md)
- [MAA-Meow UI 与后台运行设计](docs/MAAMEOW_UI_AND_BACKGROUND.md)
- [MuMu 环境与校准](docs/MUMU_ENVIRONMENT.md)
- [Codex 实施任务书](docs/CODEX_TASKS.md)
