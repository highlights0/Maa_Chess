# MaaXiangqi

基于 MaaFramework 的天天象棋 Android 自动化与棋局分析项目。

> 目标客户端：腾讯《天天象棋》Android 版  
> 首期场景：标准对局、残局闯关  
> 产品 UI：Android 原生 Jetpack Compose / Material 3  
> 目标运行模式：ADB 开发模式、Android 前台模式、Android 后台虚拟显示模式

## 产品方向

项目 UI 和后台体验参考 [Aliothmoon/MAA-Meow](https://github.com/Aliothmoon/MAA-Meow) 的公开设计思路：

- 卡片式首页、任务页、棋谱页和设置页。
- 清晰的设备、权限、游戏、MAA Core 和任务状态。
- Android 原生运行 MAA Core。
- 通过 Shizuku / Root 特权服务创建后台虚拟显示器。
- 将天天象棋运行在独立显示器上，主界面继续用于其他操作。

本项目只借鉴其架构和交互思路，不复制 MAA-Meow 的源码、资源或品牌素材。参考项目使用 AGPL-3.0，若将来决定直接复用其代码，必须先完成许可证评估。

**重要：后台虚拟显示尚未实现。** 是否可用取决于 Android 版本、模拟器 / ROM、Shizuku 或 Root 权限和游戏对非默认显示器的兼容性。屏幕熄灭和锁屏运行不属于首版承诺。

## 当前代码状态

当前仓库包含 Windows Python 开发与诊断工具：

- ADB 设备发现、设备显示参数读取、应用启动和 Android 内部截图。
- MuMu / Android 环境诊断：Android API、ABI、模拟器 build properties、Root / Shizuku 线索、已安装游戏包和显示器信息。
- MuMu 900×1600 首轮棋盘校准 profile。
- 棋盘四角人工校准、透视坐标映射、90 个交叉点可视化。
- 棋位图块导出，为后续棋子分类模型建立数据集。
- BoardState、ICCS 坐标和 Pikafish 兼容 FEN 基础表示。
- MaaFramework Python Agent 的诊断截图动作。

这些 Python 工具用于开发、截图采集、棋盘校准和算法验证，不代表最终产品 UI 或后台运行能力已经完成。

**尚未完成：** Android 原生 App、Shizuku / Root 远程服务、虚拟显示器、MAA Core Android 运行时、天天象棋界面自动识别、14 类棋子模型、完整规则校验、Pikafish Android 引擎和自动落子闭环。

## Windows 开发工具快速开始

### 环境

- Windows 10/11
- Python 3.10+
- MuMu 模拟器已开启 ADB 调试
- Android Platform Tools（adb.exe）

### 安装

    py -3.11 -m venv .venv
    .\.venv\Scripts\Activate.ps1
    python -m pip install --upgrade pip
    pip install -e ".[dev]"

### 检查 ADB 设备

    maa-xiangqi devices
    maa-xiangqi device-info --serial <设备序列号>

### MuMu 环境诊断

    maa-xiangqi-diagnose --serial <设备序列号> --output debug/mumu-diagnostics.json

如需主动验证 su：

    maa-xiangqi-diagnose --serial <设备序列号> --probe-root --output debug/mumu-diagnostics-root.json

probe-root 可能触发 Root 授权提示。诊断报告不包含设备序列号；Shizuku 授权状态仍需在 Shizuku 管理界面核实。

### 获取 Android 内部截图

    maa-xiangqi capture --serial <设备序列号> --output debug/captures/current.png

截图通过 adb exec-out screencap -p 获取，不截取模拟器 Windows 标题栏和边框。

### 使用 MuMu 900×1600 校准

    maa-xiangqi overlay --image debug/captures/current.png --profile config/tiantian/mumu_900x1600.yaml --output debug/mumu-board-overlay.png

    maa-xiangqi crops --image debug/captures/current.png --profile config/tiantian/mumu_900x1600.yaml --output-dir debug/mumu-crops

该 profile 来自用户提供的 900×1600 Android 内部截图。更改模拟器分辨率或游戏布局后必须重新校准。

### 运行测试

    pytest
    ruff check .

## 项目文档

- [整体架构](docs/architecture.md)
- [MAA-Meow UI 与后台运行设计](docs/MAAMEOW_UI_AND_BACKGROUND.md)
- [MuMu 环境诊断与棋盘校准](docs/MUMU_ENVIRONMENT.md)
- [Codex 完整实施任务书](docs/CODEX_TASKS.md)
- [第一阶段操作与验收](docs/phase-1.md)
- [项目协作约定](AGENTS.md)

## 关键设计约定

1. 棋盘逻辑坐标固定为 9 列 × 10 行，图像 row=0 是黑方底线，row=9 是红方底线。
2. ICCS / Pikafish 坐标从红方视角编号：红方底线为 rank 0，黑方底线为 rank 9。
3. 标准对局与残局闯关共用棋盘、识别、规则和引擎核心；场景状态机分别处理。
4. ADB 主机模式不承诺真正后台运行；只有通过虚拟显示器、显示器定向采集和输入验证后，才可标记后台模式可用。
5. 未经视觉确认的预测局面不能覆盖已确认局面；点击失败或状态不确定时必须暂停。
6. 自动输入优先用于离线练习、残局和明确允许自动化的测试场景。

## 技术参考

- [MaaFramework](https://github.com/MaaXYZ/MaaFramework)
- [MaaPracticeBoilerplate](https://github.com/MaaXYZ/MaaPracticeBoilerplate)
- [Pikafish](https://github.com/official-pikafish/Pikafish)
- [Shizuku](https://shizuku.rikka.app/)
- [MAA-Meow](https://github.com/Aliothmoon/MAA-Meow)
