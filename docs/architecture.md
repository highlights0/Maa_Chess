# MaaXiangqi 架构设计

## 1. 目标与范围

目标客户端为天天象棋 Android 版，首期支持：

- 标准中国象棋对局。
- 残局闯关。

产品最终采用 Android 原生 UI 和 Android 原生 MAA Core 运行时，以便实现 MAA-Meow 风格的前台 / 后台双模式。当前 Windows Python + ADB 工具继续作为开发、截图采集、棋盘校准和算法验证工具，不将其误认为最终后台运行架构。

联网竞技自动操作不是默认目标。优先在离线练习、残局闯关和获准的测试环境验证；如果平台规则不允许自动化，则只保留局面识别和辅助分析。

## 2. 总体架构

    Android App (Kotlin / Jetpack Compose / Material 3)
        ├── UI：首页、任务、棋谱、设置
        ├── Session / State：任务状态、棋局状态、日志
        ├── Foreground Service：运行通知、任务生命周期
        └── Binder / AIDL
                |
                +------------------------------+
                |                              |
                v                              v
        MaaFramework Runtime             Privileged Remote Service
        (Android native)                 (Shizuku / Root)
                |                              |
                |                              ├── DisplayBackend
                |                              |     ├── Legacy Virtual Display
                |                              |     └── Android 14+ VDM
                |                              ├── Target App Launcher
                |                              ├── Display-specific Input
                |                              └── Display Capture Bridge
                |
                v
        Shared Xiangqi Core
            ├── Scene Adapter (standard / puzzle)
            ├── BoardGeometry (90 intersections)
            ├── PieceClassifier (15 classes)
            ├── BoardState / FEN / ICCS
            ├── RuleValidator / MoveHistory
            └── Pikafish Engine Adapter

    Windows Developer Tools (current Python package)
        ├── ADB device discovery / capture
        ├── Manual board calibration / geometry
        ├── 90-cell crop export
        └── Dataset inspection / regression tests

模块之间只传递明确的数据对象，不直接读写彼此的内部状态。

## 3. 运行后端

### 3.1 Windows ADB 主机模式

当前已有。由 Windows 通过 ADB 获取 Android 内部截图和发送输入，主要用于开发与调试。

此模式的游戏仍在 Android 主显示器上运行。模拟器最小化后是否继续渲染取决于模拟器本身，不承诺真正后台执行。

### 3.2 Android 前台模式

由 MaaXiangqi 原生 App 在 Android 内运行 MAA Core，目标游戏在主显示器运行。用于无 Shizuku / Root 权限时的基础兼容和调试。

### 3.3 Android 后台虚拟显示模式

这是目标后台能力：

1. 用户授予 Shizuku 或 Root 权限。
2. 特权服务创建虚拟显示器。
3. 将天天象棋启动到虚拟显示器的 displayId。
4. MAA Core 连接该显示器。
5. 截图采集和输入注入均绑定同一 displayId。
6. App UI 保留在主显示器，用户可以切换到其他应用。
7. Watchdog 持续验证游戏进程、虚拟显示器和帧输出。

不能把“启动了虚拟显示器”直接当作后台运行成功。必须确认目标游戏真的在该显示器渲染、采集到正确帧、触控只作用于目标显示器。

## 4. Android 原生 UI

最终 UI 使用 Jetpack Compose + Material 3，参考 MAA-Meow 的卡片式信息组织和底部导航，但按象棋场景独立设计。

建议四个入口：

- 首页：设备、权限、游戏状态、前台 / 后台模式、标准 / 残局选择、启动操作。
- 任务：棋盘预览、行棋方、识别置信度、引擎推荐、任务状态、暂停 / 停止 / 单步。
- 棋谱：走法历史、FEN、识别结果、日志和诊断。
- 设置：Shizuku / Root、虚拟显示后端和分辨率、引擎、识别阈值、通知和诊断。

设备连接、权限、MAA Core、虚拟显示、游戏进程和任务状态必须分开显示。不得用单一“已连接”状态掩盖局部故障。

详细要求见 docs/MAAMEOW_UI_AND_BACKGROUND.md。

## 5. Android 后台显示和控制

### 5.1 特权服务

通过 Shizuku 或 Root 启动独立远程服务，负责需要更高权限的显示管理、目标应用启动、显示器定向输入和采集桥接。

服务通过 Binder/AIDL 向主 App 暴露稳定接口。主进程和特权进程必须有独立生命周期、连接状态、超时和死亡恢复处理。

### 5.2 DisplayBackend

规划统一接口：

- LegacyVirtualDisplay：传统虚拟显示管理路径。
- VirtualDeviceDisplay：Android 14+ 且系统能力满足时使用 VirtualDeviceManager。
- Unsupported：权限或设备能力不足时明确返回不支持。

具体实现必须根据当前 Android API、模拟器系统镜像和 MaaFramework 官方接口验证。不得复制 MAA-Meow 的隐藏 API 包装或 JNI Bridge 代码。

### 5.3 MAA Core 连接

优先验证 MaaFramework 官方 Android Native Control Unit 和当前 Android MaaCore 产物，能否满足 displayId 定向截图、触控和游戏启动需求。

若官方接口无法满足虚拟显示器场景，再实现本项目自己的最小 Native Bridge，并通过 MaaFramework 官方扩展点接入。连接参数至少需要表达：

- 目标屏幕宽高。
- displayId。
- 截图 / Surface 资源句柄或桥接库配置。
- 触控坐标和目标 displayId。

不得假定 ADB screencap 读取的主屏截图就是虚拟显示器截图。

### 5.4 兼容性

后台虚拟显示通常需要 Shizuku 或 Root。Android 版本、OEM ROM、模拟器镜像、显示权限和游戏本身都会影响成功率。息屏 / 锁屏运行单独评估，不作为首个后台模式的验收前提。

## 6. 象棋业务层

标准对局与残局闯关共用棋盘定位、棋子分类、BoardState、FEN、ICCS、规则验证和引擎接口。场景适配器负责大厅、模式选择、标准对局、残局、结算等 UI 状态。

## 7. 坐标约定

### 7.1 图像坐标

- 原点为 Android 内部截图左上角。
- x 向右，y 向下。
- 棋盘逻辑列 col 为 0–8，行 row 为 0–9。
- row=0 对应黑方底线，row=9 对应红方底线。

### 7.2 ICCS / Pikafish 坐标

ICCS 从红方视角编号，文件为 a–i，红方底线 rank=0，黑方底线 rank=9：

    iccs_file = "abcdefghi"[col]
    iccs_rank = 9 - row

例如图像左下角 (col=0,row=9) 对应 a0，图像右上角 (8,0) 对应 i9。

## 8. 棋盘几何

第一阶段使用四个最外侧棋盘交叉点进行人工校准，角点顺序固定为 TL -> TR -> BR -> BL。不能使用木质棋盘外框、装饰边框或楚河汉界文字区域的角点。

用四角计算逻辑棋盘到截图的单应矩阵 H：

    [x, y, 1]^T ~ H [col, row, 1]^T

输出 9 × 10 个交叉点。只有在宽高比保持一致时才允许按比例缩放旧校准；宽高比变化时要求重新校准。

Android 的直接截图采用轴对齐矩形棋盘。公共定位器从亮度脊线拟合九列十行、接近正方形的网格，以河界内线中断区分行相位；证据不足或存在相近的不同候选则拒绝定位。每帧复核已定位网格，布局未变时保留完全相同的坐标，避免采样抖动破坏视觉历史和输入复核。布局变化后重新定位，不再用相同截图尺寸推定棋盘位置相同。透视照片尚不支持。

自动定位失败时，可在冻结截图上选择左上、右下最外侧交叉点，预览完整 9×10 网格并确认。手动配置按前后台模式及截图宽高保存作为兜底，所有识别、提示和输入路径复用同一坐标。后台截图的 JPEG 缩小不改变输入空间：自动触摸查询远端实际逻辑宽高、方向和 displayId，按比例映射，拒绝宽高比不匹配；远端在每次触摸前复核坐标系与游戏所在屏幕，同步返回系统 DOWN / UP 注入结果。系统接收不代表游戏已落子，仍需截图确认。

## 9. 棋子识别

目标为 15 类分类：

| ID | 标签 | 符号 |
|---:|---|---|
| 0 | empty | . |
| 1 | red_king | K |
| 2 | red_advisor | A |
| 3 | red_elephant | B |
| 4 | red_horse | N |
| 5 | red_rook | R |
| 6 | red_cannon | C |
| 7 | red_pawn | P |
| 8 | black_king | k |
| 9 | black_advisor | a |
| 10 | black_elephant | b |
| 11 | black_horse | n |
| 12 | black_rook | r |
| 13 | black_cannon | c |
| 14 | black_pawn | p |

每个交叉点裁剪一个小图块，模型返回类别概率。必须保留 top-1 置信度和 top-k 分布。低置信度不能强行落成某个棋子，应重新采样或请求人工确认。

开发阶段可用 Python/OpenCV 进行数据采集和基线验证；Android 运行时应选择可在目标 ABI 上稳定运行的推理实现，例如 ONNX Runtime Android，具体以实测为准。

## 10. 状态管理

必须区分：

- observed_state：由稳定截图识别并确认的真实棋局。
- predicted_state：引擎走法执行后预计出现的棋局。
- pending_move：已经发出触控但尚未确认的走法。

只有视觉结果与预期一致、且规则校验通过后，才把 predicted_state 提升为 observed_state。失败时保留前后截图、棋位差异和引擎走法，暂停后续操作。

## 11. 标准对局与残局

标准对局从标准初始局面或已识别的中途局面开始，识别双方棋子、行棋方、计时和结算状态。

残局可能只有少量棋子，不应套用标准开局棋子数量检查。仍需检查 10×9 结构、棋子符号、将帅存在性（按具体关卡规则处理）、行棋方和合法走法。历史不明时，以 `historyKnown=false` 保留未知状态。允许用当前棋盘与已确认轮次建立搜索基准；其 FEN 中的 0 / 1 只是搜索初始化值，不是观测到的 rule60 / 回合数，不可用于历史重复局面裁定。

## 12. Pikafish 接口

通过独立 UCI 进程使用：

    uci
    isready
    position fen <xiangqi-fen> moves <move...>
    go movetime 1000

ICCS 走法为起点和终点两个方格，例如 b0c2。FEN 布局从黑方底线到红方底线，大写为红方，小写为黑方。真实 rule60 不能从一张截图推断；中途接入的搜索基准与有完整历史的对局必须用 `historyKnown` 区分。

保留起始 FEN 与走法历史。发送引擎前必须完成格式和走法校验。

## 13. 数据流

    Android screenshot / virtual display frame
        -> UI / scene state
        -> board geometry
        -> 90 cell crops
        -> piece probabilities
        -> BoardState + confidence
        -> rule and history validation
        -> engine search
        -> proposed move
        -> authorized input (displayId + source + target)
        -> stable frame
        -> verify expected board delta
        -> update observed_state

## 14. 运行模式

- 辅助分析模式：只识别、分析和显示推荐走法，不向游戏发送触控。
- 自动执行模式：仅在离线练习、残局或明确允许自动化的测试环境启用；每一步均需前后状态验证，支持暂停和停止。
