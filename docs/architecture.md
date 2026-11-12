# MaaXiangqi 架构设计

## 1. 目标与范围

首个客户端是天天象棋 Android 版，运行在 Android 手机或模拟器中。控制和截图统一使用 ADB / MaaFramework AdbController；不依赖 Windows 模拟器窗口的尺寸和边框。

首期支持两种场景：

- **标准对局**：完整标准中国象棋棋盘，识别双方棋子、轮到哪一方以及对局状态。
- **残局闯关**：从任意合法残局局面开始，识别当前残局棋盘、玩家执棋方、关卡提示和完成状态。

两种场景共用棋盘定位、棋位分类、BoardState、FEN 和引擎接口。场景适配器负责区分大厅、标准对局、残局、结算等 UI 状态，以及推断或确认行棋方。

联网竞技自动操作不是默认目标。优先在离线练习、残局闯关和获准的测试环境验证；如果平台规则不允许自动化，则只保留局面识别和辅助分析。

## 2. 分层架构

    MaaFramework / General UI
        ├── AdbController：Android 截图和触控
        ├── Pipeline：任务入口、节点调度和恢复
        └── Python Agent：注册自定义识别器 / 动作
                 |
                 v
    TiantianXiangqiAdapter
        ├── UIStateDetector
        ├── SceneModeDetector (standard / puzzle)
        ├── BoardLocator / Calibration
        └── MoveInputAdapter（仅授权场景）
                 |
                 v
    Shared Xiangqi Core
        ├── BoardGeometry：90 个交叉点的屏幕映射
        ├── PieceClassifier：15 类（空位 + 14 种棋子）
        ├── BoardState：10 × 9 棋盘、行棋方和历史
        ├── FEN / ICCS 坐标
        └── RuleValidator：合法性与局面一致性
                 |
                 v
    Engine Adapter
        └── Pikafish UCI：position / moves / go / bestmove

模块之间只传递明确的数据对象，不直接读写彼此的内部状态。

## 3. 坐标约定

### 3.1 图像坐标

- 原点为 Android 内部截图左上角。
- x 向右，y 向下。
- 棋盘逻辑列 col 为 0–8，行 row 为 0–9。
- row=0 对应黑方底线，row=9 对应红方底线。

### 3.2 ICCS / Pikafish 坐标

ICCS 从红方视角编号，文件为 a–i，红方底线 rank=0，黑方底线 rank=9。因此：

    iccs_file = "abcdefghi"[col]
    iccs_rank = 9 - row

例如图像左下角 (col=0,row=9) 对应 a0，图像右上角 (8,0) 对应 i9。

## 4. 棋盘几何

第一阶段使用四个最外侧棋盘交叉点进行人工校准，角点顺序固定为：

    TL -> TR -> BR -> BL

不能使用木质棋盘外框、装饰边框或楚河汉界文字区域的角点。

用四角计算逻辑棋盘矩形到截图的单应矩阵 H：

    [x, y, 1]^T ~ H [col, row, 1]^T

输出 9 × 10 个交叉点。只有在宽高比保持一致时才允许按比例缩放旧校准；宽高比变化时要求重新校准。后续再开发自动定位器，并以人工校准结果作为评估基准。

## 5. 棋子识别

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

训练集要覆盖不同皮肤、缩放、分辨率、选中态、动画和遮挡。不能只用用户当前的一张截图训练或评估模型。

## 6. 状态管理

必须区分：

- observed_state：由稳定截图识别并确认的真实棋局。
- predicted_state：引擎走法执行后预计出现的棋局。
- pending_move：已经发出触控但尚未确认的走法。

只有视觉结果与预期一致、且规则校验通过后，才把 predicted_state 提升为 observed_state。失败时保留前后截图、棋位差异和引擎走法，暂停后续操作。

## 7. 标准对局与残局

### 标准对局

从标准初始局面或经过识别的中途局面开始。需要识别红黑双方、行棋方、双方计时和结算状态。若从初始局面开始，应保留完整 move_history。

### 残局闯关

残局可能只有少量棋子，不应套用“棋子数量必须接近标准开局”的检查。仍需检查 10×9 结构、棋子符号、将帅存在性（按具体关卡规则处理）、行棋方和合法走法。残局 FEN 通常来自识别结果，rule60 与历史不明时必须标记未知，不能伪造历史计数。

## 8. Pikafish 接口

通过独立子进程使用 UCI：

    uci
    isready
    position fen <xiangqi-fen> moves <move...>
    go movetime 1000

ICCS 走法为起点和终点两个方格，例如 b0c2。Pikafish FEN 的棋子布局从黑方底线到红方底线；大写为红方，小写为黑方。FEN 的第五字段是 Pikafish 的 rule60 计数，不能直接把国际象棋语义的半回合钟随意映射过来。

FEN 只描述当前局面，无法单独恢复此前长将、长捉和重复局面历史。应保留起始 FEN 与走法序列。发送引擎前必须在本地完成格式和走法校验；非法输入可能导致引擎退出。

## 9. 数据流

    Android internal screenshot
        -> UI / scene state
        -> board geometry
        -> 90 cell crops
        -> piece probabilities
        -> BoardState + confidence
        -> rule and history validation
        -> engine search
        -> proposed move
        -> (authorized input only) source tap + target tap
        -> stable screenshot
        -> verify expected board delta
        -> update observed_state

## 10. 运行模式

- **辅助分析模式**：只识别、分析和显示推荐走法，不向游戏发送触控。
- **自动执行模式**：仅在离线练习、残局或明确允许自动化的测试环境启用；每一步均需前后状态验证，支持暂停和停止。
