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

- 天天象棋大厅、标准对局和残局闯关的自动场景识别。
- 棋盘自动定位与自动校准；当前 Android 端先使用 MuMu 900×1600 四角校准。
- 自动识别行棋方、维护增量走法历史。
- Pikafish Android ABI 引擎接入与 UCI 生命周期管理。
- 走法合法性复核、起点 / 终点触控、动画等待和落子后复识别闭环。

**重要边界：** Android 任务页已接入游戏启动、前台/后台显示、后台帧采集、nano-v3 模型下载与校验、ONNX Runtime 本地推理和棋盘结果展示入口；但尚未完成 MuMu 实机准确率验收，也不会自动点击游戏内菜单或自动对弈。行棋方识别、Pikafish Android 引擎和自动落子闭环仍在开发。MAA-Meow 中的 Arknights `AsstAppendTask` 任务链不是象棋任务引擎；象棋识别与执行使用本项目独立 Xiangqi Core，并通过受控 MaaFramework pipeline / custom action 接入。

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

保留 ADB 设备发现、Android 内部截图、MuMu 环境诊断、棋盘四角校准、nano-v3 预训练模型推理、FEN / ICCS 坐标、Pikafish UCI 和 MaaFramework Python Agent。

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev,vision]"
maa-xiangqi-download-model
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
