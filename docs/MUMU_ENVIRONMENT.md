# MuMu 环境诊断与首轮棋盘校准

## 当前已知信息

用户当前使用 MuMu 模拟器，并已提供一张天天象棋 Android 内部截图：

- Android 内部截图尺寸：900×1600。
- 棋盘四角初始校准：TL (66, 327)、TR (834, 327)、BR (834, 1191)、BL (66, 1191)。
- 对应 profile：config/tiantian/mumu_900x1600.yaml。
- 原始截图未提交到仓库；profile 仅保存校准元数据和坐标。

这组坐标已通过 9×10 网格叠加做过目视检查。若分辨率、DPI、游戏缩放或棋盘布局变化，必须重新校准。

## 1. MuMu 设备诊断结果（2026-11-12）

本节根据用户上传的 mumu-diagnostics.json 和 mumu-diagnostics-root.json 记录。原始 JSON、ADB 设备序列号和本机日志不提交到仓库。

| 项目 | 诊断结果 |
|---|---|
| Build manufacturer / brand | OPPO / OPPO |
| Model / product / device | PGEM10 / wukong / wukong |
| Display device product name | mumuscreen000 |
| Android release / API | 12 / 32 |
| CPU ABI | x86_64 |
| ABI 列表 | x86_64、arm64-v8a、x86、armeabi-v7a、armeabi |
| 屏幕 / DPI | 900×1600 / 240 |
| ADB shell UID | 2000（shell） |
| su 路径 | 未发现 |
| Shizuku | 未安装，未检测到进程 |
| 天天象棋包名 / 版本 | com.tencent.qqgame.xq / 4.3.0.2 |
| 当前显示器 | 仅观察到 Display 0，状态 ON |

### 诊断解释

1. 模拟器 build properties 使用 OPPO PGEM10 / wukong 作为设备标识，不能仅凭这些字段识别 MuMu。
2. dumpsys display 的设备产品名称包含 mumuscreen000，这是更明确的 MuMu 线索。诊断程序已增加对该显示元数据的识别。
3. Android API 32 满足 Headless Android 项目声明的 Android 10+ 最低版本要求；这不代表其虚拟显示功能已在当前 MuMu 版本上验证。
4. 当前 display dump 出现 VirtualDisplayAdapter，但 Display Devices 列表只有一个 Display 0。这只能说明系统注册了虚拟显示适配组件，不能证明 shell 权限可以成功创建独立显示器。
5. shell UID 为 2000，不是 root。
6. Root 探测命令超时。超时属于未知结果，不等同于明确拒绝或确认没有 Root。su 路径未发现也不能单独证明系统没有 Root 管理器。
7. Shizuku 未安装，因此当前没有可供 PoC 使用的 Shizuku 授权服务。

### 当前结论

- MuMu 设备信息和天天象棋安装信息已采集。
- 当前未确认 Root。
- 当前未安装 Shizuku。
- 尚未创建虚拟显示器，后台运行能力尚未验证。
- 下一步优先在 MuMu 中安装并启动 Shizuku，使用 Shizuku shell 模式测试；不要把 ADB shell UID 2000 误认为 Root。
- 若 Shizuku 无法在当前 MuMu 系统镜像运行，再单独评估模拟器 Root 开关。不要在未经确认的情况下修改系统镜像或执行未知提权脚本。

## 2. 连接 MuMu ADB

MuMu 官方 Windows 开发者手册说明，常见默认 ADB 地址为 127.0.0.1:7555；部分安装版本使用 MuMu 自带的 adb_server.exe。端口也可能被用户自定义，优先以 MuMu 当前实例显示的 ADB 端口为准。

官方参考：
- https://www.mumuplayer.com/help/win/developers-essentials-manual.html

先尝试：

    adb connect 127.0.0.1:7555
    adb devices -l

如果系统没有 adb 命令，可以使用 MuMu 安装目录内的 adb_server.exe。常见旧版路径示例：

    C:\\Program Files (x86)\\Nemu\\vmonitor\\bin\\adb_server.exe

实际路径随 MuMu 版本和安装位置变化。运行诊断时可以通过 --adb 指定该可执行文件：

    maa-xiangqi-diagnose --adb "C:\\Program Files (x86)\\Nemu\\vmonitor\\bin\\adb_server.exe" --serial 127.0.0.1:7555 --output debug/mumu-diagnostics.json

记下状态为 device 的序列号。不要把序列号提交到 GitHub。

## 3. 运行环境诊断

    maa-xiangqi-diagnose --serial <你的MuMu设备序列号> --output debug/mumu-diagnostics.json

如需主动测试 su：

    maa-xiangqi-diagnose --serial <你的MuMu设备序列号> --probe-root --output debug/mumu-diagnostics-root.json

--probe-root 会执行 su -c id，可能触发 Root 授权提示。默认诊断不会主动申请 Root。诊断程序现在区分明确拒绝和命令超时：明确 permission denied / su not found 才标记不可用；超时或 ADB 服务异常保持未知。

Shizuku 是否已获得授权，不能仅凭普通 ADB shell 的包列表或进程列表可靠确认；需要在 Shizuku 管理界面检查。

## 4. 使用当前 900×1600 校准

先通过 ADB 获取一张新的当前截图：

    maa-xiangqi capture --serial <你的MuMu设备序列号> --output debug/captures/mumu-current.png

如果截图仍然是 900×1600，使用预置 profile 生成网格：

    maa-xiangqi overlay --image debug/captures/mumu-current.png --profile config/tiantian/mumu_900x1600.yaml --output debug/mumu-board-overlay.png

    maa-xiangqi crops --image debug/captures/mumu-current.png --profile config/tiantian/mumu_900x1600.yaml --output-dir debug/mumu-crops

检查网格交点是否穿过 90 个棋位中心，特别检查最外圈、九宫、楚河汉界和红方帅所在棋位。若不吻合，重新使用 calibrate 写入新 profile，不要修改代码里的坐标常量。

## 5. 后台虚拟显示能力仍需 PoC

环境诊断只负责收集系统信息，不会创建虚拟显示器，也不会自动修改系统权限。

要认定 MuMu 支持真正后台运行，后续 PoC 必须逐项证明：

1. 能创建一个 displayId 不等于主显示器的虚拟显示器。
2. 测试应用确实运行在该 displayId。
3. 捕获帧来自该虚拟显示器，而非主屏。
4. 输入事件只发往该 displayId。
5. 切换主屏其他应用后，虚拟显示器仍持续出帧。
6. 游戏进程退出、显示器漂移、黑屏或特权服务死亡时能检测并停止任务。

在这些验证完成前，不能把 MuMu 的 ADB 截图能力等同于后台虚拟显示能力。
