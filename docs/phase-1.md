# 第一阶段：MuMu ADB 连接、截图与棋盘校准

## 当前已知设备信息

用户当前使用 MuMu 模拟器，并已提供一张天天象棋 Android 内部截图：

- Android 内部截图尺寸：900×1600。
- 棋盘四角校准：TL (66,363)、TR (834,363)、BR (834,1227)、BL (66,1227)；已修正旧配置整体偏上的误差。
- 对应 profile：config/tiantian/mumu_900x1600.yaml。
- 原始截图未提交到仓库；profile 仅保存校准元数据和坐标。

这组坐标已通过 9×10 网格叠加做过目视检查。若分辨率、DPI、游戏缩放或棋盘布局变化，必须重新校准。

## 目标

完成 Windows 开发机上的基础工具：

1. 发现 Android ADB 设备。
2. 读取 Android 内部显示分辨率和密度。
3. 诊断 MuMu 模拟器的 Android API、ABI、Root / Shizuku 线索和显示器状态。
4. 启动天天象棋（包名可配置）。
5. 通过 ADB 获取 Android 内部 PNG 截图。
6. 使用预置 MuMu profile 或人工四角校准生成棋盘透视映射。
7. 生成 9×10 网格叠加图和 90 个棋位图块。

本阶段是开发和诊断基础，不是最终产品 UI，也不实现真正后台运行。

## 环境准备

Windows PowerShell：

    py -3.11 -m venv .venv
    .\.venv\Scripts\Activate.ps1
    python -m pip install --upgrade pip
    pip install -e ".[dev]"

确认 ADB：

    adb version
    adb devices -l

如果 MuMu 未显示，先在 MuMu 设置中启用 ADB 调试。多个模拟器同时连接时，所有设备命令必须显式指定 --serial。

## MuMu 环境诊断

先确认 MuMu 的 ADB 序列号，然后运行：

    maa-xiangqi-diagnose --serial <设备序列号> --output debug/mumu-diagnostics.json

该报告收集 Android API、CPU ABI、模拟器 build properties、Root / Shizuku 线索、候选游戏包名和 dumpsys display 摘要。报告不保存设备序列号。

如果希望显式测试 Root 授权：

    maa-xiangqi-diagnose --serial <设备序列号> --probe-root --output debug/mumu-diagnostics-root.json

probe-root 会执行 su -c id，可能触发 Root 授权提示。默认诊断不会主动申请 Root。Shizuku 是否已授权不能只靠普通 ADB shell 可靠确认，需要在 Shizuku 管理界面检查。

## 获取截图与核实游戏包名

    maa-xiangqi capture --serial <设备序列号> --output debug/captures/mumu-current.png

    adb -s <设备序列号> shell pm list packages | findstr /i "tencent qqgame xiangqi chess"

不要默认认为 com.tencent.qqgame.xq 一定是实际包名。以设备查询结果为准。

## 使用 MuMu 900×1600 预置校准

如果当前截图仍然是 900×1600：

    maa-xiangqi overlay --image debug/captures/mumu-current.png --profile config/tiantian/mumu_900x1600.yaml --output debug/mumu-board-overlay.png

    maa-xiangqi crops --image debug/captures/mumu-current.png --profile config/tiantian/mumu_900x1600.yaml --output-dir debug/mumu-crops

检查 90 个棋位中心、最外圈、九宫、楚河汉界和帅所在位置是否对齐。

如果布局发生变化，使用当前截图重新校准：

    maa-xiangqi calibrate --image debug/captures/mumu-current.png --corners "xTL,yTL;xTR,yTR;xBR,yBR;xBL,yBL" --profile config/tiantian/mumu_900x1600.yaml

## 已核实的 MuMu 设备信息（2026-11-12）

用户提供的诊断报告确认：

- Android API 32 / Android 12。
- CPU ABI：x86_64；可用 ABI 列表同时包含 arm64-v8a。
- Android 内部截图：900×1600，240 DPI。
- 天天象棋已安装包名：`com.tencent.qqgame.xq`，版本 4.3.0.2。
- Shizuku 未安装；Root 探测超时并判定不可用。
- 当前只检测到主显示器 displayId=0；虚拟显示器尚未创建和验证。

因此，应用已将天天象棋包名和初始后台竖屏分辨率登记为默认配置。前台启动可通过 Android 启动器完成；后台模式仍需先安装并授权 Shizuku 或配置 Root，再执行独立虚拟显示 PoC。

## 后台虚拟显示 PoC

环境诊断并不会创建虚拟显示器。后台能力必须通过独立 PoC 证明：

1. 创建非主显示器 displayId。
2. 启动测试应用到该 displayId。
3. 证明采集帧来自该虚拟显示器。
4. 证明输入事件只发往该 displayId。
5. 切换主屏其他 App 后，虚拟显示器仍持续出帧。
6. 游戏进程退出、显示器漂移、黑屏或特权服务死亡时能检测并停止任务。

在这些验证完成前，不能把 MuMu 的 ADB 截图能力等同于后台虚拟显示能力。

## 下一阶段输入

完成后请提供（诊断 JSON 默认已隐藏设备序列号）：

- debug/mumu-diagnostics.json。
- 如果用户允许并已执行 Root 探测，再提供 debug/mumu-diagnostics-root.json。
- MuMu 设置界面显示的模拟器版本。
- Shizuku 管理界面的运行和授权状态。
- 最新棋盘网格叠加图的检查结果。
