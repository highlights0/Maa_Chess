# 第一阶段：ADB 连接、截图与棋盘校准

## 目标

完成 Windows 开发机上的基础工具：

1. 发现 Android ADB 设备。
2. 读取 Android 内部显示分辨率和密度。
3. 启动天天象棋（包名可配置）。
4. 通过 ADB 获取 Android 内部 PNG 截图。
5. 人工输入四个最外侧棋盘交叉点，生成棋盘透视映射。
6. 生成 9 × 10 网格叠加图。
7. 导出 90 个棋位裁剪图。
8. 通过 MaaFramework Agent 的诊断任务保存截图。

本阶段是开发和诊断基础，不是最终产品 UI，也不实现真正后台运行。

## 重要：两种截图坐标不可混用

用户最初提供的是 Windows 模拟器窗口截图，图像尺寸为 463 × 871，画面中包含模拟器窗口标题栏和外框。该截图可供界面分析，但不是 ADB Android 内部截图。

因此，之前目测的棋盘角点只能作为视觉参考，**不得直接写入 ADB 校准配置**。必须先运行 capture 获取 Android 内部截图，再在这张截图上重新测量四个最外侧棋盘交叉点。

## 环境准备

Windows PowerShell：

    py -3.11 -m venv .venv
    .\.venv\Scripts\Activate.ps1
    python -m pip install --upgrade pip
    pip install -e ".[dev]"

确认 ADB：

    adb version
    adb devices -l

如果模拟器未显示，先在模拟器设置中启用 ADB 调试。多个模拟器同时连接时，所有设备命令必须显式指定 --serial。

## 设备信息与截图

    maa-xiangqi devices
    maa-xiangqi device-info --serial emulator-5554
    maa-xiangqi capture --serial emulator-5554 --output debug/captures/standard.png

截图应该只包含 Android 内部画面，不含 Windows 标题栏。检查截图分辨率，并记录设备型号、模拟器版本、Android 版本、分辨率和 DPI。

核实应用包名：

    adb -s emulator-5554 shell pm list packages | findstr /i "tencent"
    adb -s emulator-5554 shell monkey -p com.tencent.qqgame.xq 1

如果包名不同，修改 config/tiantian/board_profile.yaml 的 game.package_name。

## 四角校准

在 ADB 截图上找到棋盘最外侧的四个网格交叉点，按 TL、TR、BR、BL 顺序输入：

    maa-xiangqi calibrate --image debug/captures/standard.png --corners "xTL,yTL;xTR,yTR;xBR,yBR;xBL,yBL" --profile config/tiantian/board_profile.yaml

注意：
- 角点是最外侧横线和竖线的交点，不是木框边缘。
- 角点顺序必须为左上、右上、右下、左下。
- 不要把屏幕截图坐标、Windows 客户端窗口坐标和 Android 内部坐标混在一起。
- 如果棋盘动画正在播放或被弹窗遮挡，应等待稳定后重新截图。

## 生成网格叠加图和棋位图块

    maa-xiangqi overlay --image debug/captures/standard.png --profile config/tiantian/board_profile.yaml --output debug/board_overlay.png

    maa-xiangqi crops --image debug/captures/standard.png --profile config/tiantian/board_profile.yaml --output-dir debug/crops/standard

人工检查叠加图：
- 网格线是否穿过 90 个棋位中心。
- 左右边缘是否与最外列棋位中心重合。
- 上下边缘是否与最外行棋位中心重合。
- 透视缩放后仍然贴合棋位。
- 每个裁剪图是否完整覆盖棋子主体且没有大量邻格内容。

## 标准对局与残局样本

至少分别采集：
- 标准对局：初始局面、若干中盘局面、红方走后、黑方走后。
- 残局闯关：当前用户截图所示的小兵关卡，以及至少数个棋子种类和数量不同的残局。
- 每种场景都要采集静止帧、选中帧和动画结束后的稳定帧。

当前上传截图是残局闯关界面，棋盘上只有少量棋子，可用于首轮棋盘几何和部分棋子裁剪验证，但不足以训练或评估完整 15 类分类器。

## 验收标准

- ADB 设备发现能区分 device / unauthorized / offline。
- 截图文件是有效 PNG，尺寸与 Android 内部显示分辨率一致。
- 设备数为 0 或大于 1 时，命令给出明确错误或要求指定 serial。
- 四角校准能生成 90 个交叉点。
- 四角位置映射误差在人工检查允许范围内；不能只凭程序成功写文件就判定通过。
- 叠加图的横纵网格线穿过实际棋位中心。
- 导出图块数量为 90，文件命名包含 row / col。
- 宽高比变化超过 1% 时拒绝静默缩放，要求重新校准。
- pytest 全部通过。

## 下一阶段：后台虚拟显示 PoC

在开始完整棋子识别之前，先验证后台运行能力：

1. 记录实际模拟器名称、Android API 级别、CPU 架构和 Root / Shizuku 状态。
2. 验证特权服务可以稳定启动、授权和重连。
3. 创建虚拟显示器并获取 displayId。
4. 将测试应用启动到虚拟显示器。
5. 采集虚拟显示器画面，证明不是主屏截图。
6. 对虚拟显示器注入触控，证明输入不会误发到主屏。
7. 切换到主屏其他应用，确认虚拟显示器持续出帧。
8. 最后用天天象棋分别验证标准对局和残局页面。

如果模拟器不支持所需权限或虚拟显示能力，必须先报告具体限制，再决定使用 Root、Shizuku、另一种模拟器或真实 Android 设备。不能把 ADB 主机模式描述成等价的后台模式。

## 下一阶段输入

完成后提供：
- 模拟器名称和版本。
- Android API 级别与系统镜像。
- CPU 架构。
- Root 是否可用。
- Shizuku 是否可安装、启动和授权。
- ADB 截图分辨率与 DPI。
- 一张标准对局截图和一张残局闯关截图。
- 棋盘网格叠加图和本地 90 个棋位裁剪检查结果。
- 天天象棋实际包名和客户端版本。
