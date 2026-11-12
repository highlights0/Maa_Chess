# MuMu 环境诊断与首轮棋盘校准

## 当前已知信息

用户当前使用 MuMu 模拟器，并已提供一张天天象棋 Android 内部截图：

- Android 内部截图尺寸：900×1600。
- 棋盘四角初始校准：TL (66, 327)、TR (834, 327)、BR (834, 1191)、BL (66, 1191)。
- 对应 profile：config/tiantian/mumu_900x1600.yaml。
- 原始截图未提交到仓库；profile 仅保存校准元数据和坐标。

这组坐标已通过 9×10 网格叠加做过目视检查。若分辨率、DPI、游戏缩放或棋盘布局变化，必须重新校准。

## 1. 连接 MuMu ADB

先在 MuMu 设置中确认 ADB 调试已启用，然后在 Windows PowerShell 执行：

    adb devices -l

记下状态为 device 的序列号。不要把序列号提交到 GitHub。

## 2. 运行新增的环境诊断

    maa-xiangqi-diagnose --serial <你的MuMu设备序列号> --output debug/mumu-diagnostics.json

该命令收集：

- Android 版本和 API Level。
- CPU ABI / ABI 列表。
- MuMu / 其他模拟器的 build property 特征。
- 屏幕分辨率和 DPI。
- ADB shell 身份、su 二进制路径。
- Shizuku 包是否安装、相关进程是否可见。
- 候选天天象棋包名及版本。
- 当前 dumpsys display 中可见的显示器信息。

诊断 JSON 不包含 ADB 序列号，适合在确认内容后分享。

如需主动测试 su 是否真正可用，可以额外加 --probe-root：

    maa-xiangqi-diagnose --serial <你的MuMu设备序列号> --probe-root --output debug/mumu-diagnostics-root.json

注意：该选项会执行 su -c id，可能触发 Root 授权提示。默认诊断不会主动申请 Root。

Shizuku 是否已获得授权，不能仅凭普通 ADB shell 的包列表或进程列表可靠确认；需要在 Shizuku 管理界面检查。报告会明确标记这一限制。

## 3. 使用当前 900×1600 校准

先通过 ADB 获取一张新的当前截图：

    maa-xiangqi capture --serial <你的MuMu设备序列号> --output debug/captures/mumu-current.png

如果截图仍然是 900×1600，使用预置 profile 生成网格：

    maa-xiangqi overlay --image debug/captures/mumu-current.png --profile config/tiantian/mumu_900x1600.yaml --output debug/mumu-board-overlay.png

    maa-xiangqi crops --image debug/captures/mumu-current.png --profile config/tiantian/mumu_900x1600.yaml --output-dir debug/mumu-crops

检查网格交点是否穿过 90 个棋位中心，特别检查最外圈、九宫、楚河汉界和红方帅所在棋位。若不吻合，重新使用 calibrate 写入新 profile，不要修改代码里的坐标常量。

## 4. 后台虚拟显示能力仍需 PoC

环境诊断只负责收集系统信息，不会创建虚拟显示器，也不会自动修改系统权限。

要认定 MuMu 支持真正后台运行，后续 PoC 必须逐项证明：

1. 能创建一个 displayId 不等于主显示器的虚拟显示器。
2. 测试应用确实运行在该 displayId。
3. 捕获帧来自该虚拟显示器，而非主屏。
4. 输入事件只发往该 displayId。
5. 切换主屏其他应用后，虚拟显示器仍持续出帧。
6. 游戏进程退出、显示器漂移、黑屏或特权服务死亡时能检测并停止任务。

在这些验证完成前，不能把 MuMu 的 ADB 截图能力等同于后台虚拟显示能力。
