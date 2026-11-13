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
- nano-v3 模型随 APK 提供，首次启动离线安装并校验；手机无需访问模型下载站。
- 前台悬浮面板已改为象棋识别与推荐操作，默认紧凑显示；收起面板可在游戏棋盘上显示推荐箭头。
- 已加入本地走法合法性复核；内部状态可根据唯一合法的一着或两着棋盘变化更新行棋方。用户无需维护或导入记录。
- 自动输入前连续核对棋盘，输入坐标转换回实际显示尺寸；输入后等待稳定截图，并允许对手快速回应。
- 识别、引擎与触摸流程日志按流程编号持续保存，关闭调试模式也会保留。

## 尚未完成

- 天天象棋大厅、标准对局和残局闯关的自动场景识别。
- 棋盘自动定位与自动校准；当前 Android 端先使用 MuMu 900×1600 四角校准。
- 单张未知中途棋盘的行棋方与规则历史判定；当前仅能从标准开局、人工确认起点和连续合法变化中确定。
- Pikafish Android x86_64 引擎的 APK 集成与实机验收（已本地交叉编译，尚未验证运行）。
- 起点选中态识别、连续自动对弈，以及最新前台箭头和单步复核的完整实机验收。

**重要边界：** ARM64 手机上已验证后台标准开局识别与 Pikafish 建议。最新前台面板、游戏内箭头和单步落子由用户继续实测；未知局面不能自动输入。不会自动点击游戏内菜单或连续对弈。MAA-Meow 中的 Arknights `AsstAppendTask` 任务链不是象棋任务引擎；象棋识别与执行使用本项目独立 Xiangqi Core。

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
python ../src/maa_xiangqi/model_download.py --output app/src/main/assets/xiangqi-nano-v3.onnx
python scripts/setup_pikafish.py
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
