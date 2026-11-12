# MaaXiangqi

基于 MaaFramework 的天天象棋 Android 自动化与棋局分析项目。

> 当前目标客户端：腾讯《天天象棋》Android 版  
> Android 包名（默认配置，运行时需在设备上核实）：`com.tencent.qqgame.xq`  
> 控制方式：MaaFramework + ADB，截图取自 Android 内部画面  
> 首期场景：标准对局、残局闯关

## 当前开发状态

本仓库当前从空仓库建立第一阶段工程骨架。已规划并逐步实现：

- ADB 设备发现、设备显示参数读取、应用启动和无损截图。
- 棋盘四角人工校准、透视坐标映射、90 个交叉点可视化。
- 棋位图块导出，为后续棋子分类模型建立数据集。
- 中国象棋棋盘状态、ICCS 坐标和 Pikafish 兼容 FEN 基础表示。
- MaaFramework Python Agent 的诊断截图动作。
- Codex 分阶段实施任务书和测试基线。

**尚未完成：** 天天象棋界面自动识别、14 类棋子模型、完整规则合法性验证、Pikafish 进程管理、自动落子和完整对局闭环。没有模型文件或引擎二进制随仓库发布。

## 快速开始（Windows）

### 1. 环境

- Windows 10/11
- Python 3.10+
- Android 模拟器已开启 ADB 调试
- Android Platform Tools（`adb.exe`）
- MaaFramework 通用 UI（需要使用 MAA Pipeline 时）

### 2. 安装

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -e ".[dev]"
```

### 3. 检查 ADB 设备

```powershell
maa-xiangqi devices
maa-xiangqi device-info --serial <设备序列号>
```

如果只有一个处于 `device` 状态的设备，截图命令可以省略 `--serial`。

### 4. 获取 Android 内部截图

```powershell
maa-xiangqi capture --serial <设备序列号> --output debug/captures/current.png
```

截图通过 `adb exec-out screencap -p` 获取，不截取模拟器 Windows 标题栏和边框。

### 5. 校准棋盘

当前版本采用人工点选四角的校准方式。四角必须是**最外侧棋盘交叉点**，按左上、右上、右下、左下顺序填写，而不是木质棋盘外框。

```powershell
maa-xiangqi calibrate `
  --image debug/captures/current.png `
  --corners "xTL,yTL;xTR,yTR;xBR,yBR;xBL,yBL" `
  --profile config/tiantian/board_profile.yaml

maa-xiangqi overlay `
  --image debug/captures/current.png `
  --profile config/tiantian/board_profile.yaml `
  --output debug/board_overlay.png

maa-xiangqi crops `
  --image debug/captures/current.png `
  --profile config/tiantian/board_profile.yaml `
  --output-dir debug/crops
```

校准角点坐标使用 ADB 截图像素坐标。若 Android 内部截图分辨率或画面布局改变，应重新校准或确认缩放适配结果。

### 6. 运行测试

```powershell
pytest
```

## 项目文档

- [整体架构](docs/architecture.md)
- [Codex 实施任务书](docs/CODEX_TASKS.md)
- [第一阶段操作与验收](docs/phase-1.md)
- [项目协作约定](AGENTS.md)

## 关键设计约定

1. 棋盘逻辑坐标固定为 9 列 × 10 行，图像行 0 是黑方底线，行 9 是红方底线。
2. ICCS / Pikafish 坐标从红方视角编号：红方底线为 rank 0，黑方底线为 rank 9。
3. 棋盘识别、局面状态、规则校验、引擎和输入执行必须解耦。
4. 标准对局与残局闯关共用棋盘和规则核心；场景识别、流程状态单独适配。
5. 未经视觉确认的预测局面不能覆盖已确认局面；点击失败或状态不确定时必须暂停，不可盲目继续。
6. 联网真人竞技场景必须遵守游戏规则和平台政策；开发验证优先使用离线练习、残局或获准测试场景。

## 技术参考

- [MaaFramework](https://github.com/MaaXYZ/MaaFramework)
- [MaaPracticeBoilerplate](https://github.com/MaaXYZ/MaaPracticeBoilerplate)
- [Pikafish](https://github.com/official-pikafish/Pikafish)
- [Pikafish UCI / FEN 说明](https://www.pikafish.com/wiki/reference/uci-protocol.html)
