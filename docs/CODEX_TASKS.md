# Codex 实施任务书：MaaXiangqi

> 仓库：highlights0/Maa_Chess  
> 目标客户端：天天象棋 Android  
> 截图与控制：ADB / MaaFramework AdbController  
> 场景：标准中国象棋对局 + 残局闯关  
> 当前阶段：基础工程骨架、ADB 诊断、人工棋盘校准

## 0. Codex 执行要求

开始前阅读：
- README.md
- AGENTS.md
- docs/architecture.md
- docs/phase-1.md

先检查当前代码和测试，不要推翻已完成模块。所有新增功能必须配套测试。不能运行的测试必须如实报告。不要提交用户截图、设备序列号、设备本机路径、模型权重或引擎二进制。

优先复用 MaaFramework Python Binding、MaaPracticeBoilerplate、OpenCV 和 Pikafish，不重复造轮子。使用 MaaFramework 时以官方当前文档和 Python Binding 源码为准。

## 1. 产品定义

### 1.1 两种模式

**辅助分析模式**
- 识别棋盘与行棋方。
- 生成棋局状态和 FEN。
- 调用 Pikafish 分析。
- 展示推荐走法和局面评估。
- 不向游戏发送触控。

**自动执行模式**
- 只在离线练习、残局或明确允许自动化的测试环境启用。
- 每次执行前检查状态和走法。
- 先点起点，再检查棋子选中状态，再点终点。
- 等待动画结束后重新识别。
- 只有识别结果与预期状态一致，才提交本回合。
- 任意不确定状态都暂停，不得盲目继续。

### 1.2 场景范围

标准对局与残局闯关必须都能识别。二者共用：
- 棋盘定位和 90 点几何映射。
- 15 类棋位分类。
- BoardState、FEN、ICCS 坐标。
- 棋规校验和引擎接口。

二者分别处理：
- UI 场景状态。
- 行棋方判定。
- 棋局开始 / 继续 / 结束。
- 关卡提示和残局完成状态。

不要把“标准初始棋子数量”作为所有局面的硬性要求；残局可以有少量棋子。

## 2. 当前已有基础

- ADB 设备发现、设备参数读取、应用启动和 Android 内部截图 CLI。
- 人工四角校准、透视映射、网格叠加图和 90 个棋位图块导出。
- BoardState、Xiangqi FEN 和 ICCS 坐标转换。
- MaaFramework Python Agent 诊断截图动作和 General UI interface/pipeline 初始配置。
- 单元测试基础。

当前没有自动棋盘定位、棋子分类模型、完整规则引擎、Pikafish 子进程管理或自动落子闭环。不要在文档或 UI 中宣称这些已经完成。

## 3. 阶段 A：完成基础工具验收（当前优先级 P0）

任务：
1. 在 Windows Python 3.10+ 环境安装项目依赖。
2. 运行 pytest 和 ruff，修复静态问题。
3. 用模拟器实际验证 adb devices、device-info、capture、launch。
4. 用真实 ADB 截图校准四角，输出 overlay 和 90 个 crops。
5. 验证截图分辨率改变时的缩放策略。
6. 在 MaaFramework 通用 UI 中加载 assets/interface.json，连接 AdbController，运行 CaptureDiagnostic。
7. 核对 Agent 路径、Python 环境、MAA Agent Binary 和资源路径。

交付：
- 测试记录（命令、环境、通过/失败）。
- 一张脱敏后的网格叠加图（如用户同意才提交）。
- 校准配置字段说明。
- 不要提交真实设备序列号。

验收：
- CLI 设备选择无歧义。
- 截图为 Android 内部画面。
- 网格交点对齐实际棋位。
- 90 个 crops 全部可打开。
- MAA Pipeline 诊断截图可用。

## 4. 阶段 B：天天象棋 UI 状态识别（P1）

新增模块建议：
- src/maa_xiangqi/adapters/tiantian/ui_state.py
- src/maa_xiangqi/adapters/tiantian/scene_detector.py
- src/maa_xiangqi/adapters/tiantian/profile.py
- tests/adapters/tiantian/

状态枚举至少包括：
- UNKNOWN
- HOME
- MODE_SELECT
- STANDARD_GAME
- PUZZLE_GAME
- RESULT
- DIALOG / BLOCKED

要求：
1. 先用模板匹配、OCR 和少量稳定 UI 特征实现，不需要先训练大模型。
2. 对每个状态输出置信度和证据。
3. 需要连续多帧稳定后才切换状态。
4. 弹窗、广告、加载和动画状态不能误判为棋局。
5. 标准对局与残局闯关必须分别提供测试截图集。
6. 游戏包名可配置；运行时可读取设备已安装包名辅助确认，不要把未经设备验证的包名当成事实。

## 5. 阶段 C：棋盘定位和几何自动校准（P1）

新增模块建议：
- src/maa_xiangqi/vision/board_locator.py
- src/maa_xiangqi/vision/grid_detector.py
- tests/vision/

先保留人工四角校准作为可靠 fallback。自动定位器输出：
- 棋盘四角（TL, TR, BR, BL）。
- 置信度。
- 估计的截图 ROI。
- 9 条横向和 10 条纵向网格位置（或等价的透视模型）。
- 校准误差。

建议流程：
1. 限定候选 ROI。
2. 颜色 / 纹理 / 边缘检测定位木质棋盘区域。
3. 通过网格线交点和棋盘比例约束候选四角。
4. 用 90 点网格拟合验证，而不是只依赖外框矩形。
5. 对多帧检测结果做稳定融合。
6. 自动定位失败时回退到人工校准。

不要将棋盘木框角点误当成交叉点；楚河汉界文字、装饰线和九宫斜线都可能干扰线段检测。

## 6. 阶段 D：棋子识别数据与模型（P1/P2）

目标类别：
- empty
- red_king / red_advisor / red_elephant / red_horse / red_rook / red_cannon / red_pawn
- black_king / black_advisor / black_elephant / black_horse / black_rook / black_cannon / black_pawn

任务：
1. 建立人工标注数据集和类别映射文件。
2. 训练/微调轻量 CNN 分类器；先做基线，不预设 YOLO 必须使用。
3. 导出 ONNX。
4. 保存输入尺寸、通道顺序、归一化、标签映射和模型 SHA-256。
5. 实现 OnnxPieceClassifier，返回 top-k 类别和置信度。
6. 低置信度棋位支持重新采样或标记 unknown，不可强制分类。
7. 用按棋局/采集批次隔离的数据集划分 train/val/test，防止相邻帧泄漏。
8. 分别报告标准对局、残局、选中态、动画态、不同皮肤的准确率和混淆矩阵。

不要将空棋位和棋子遮挡混为一类；棋子被选中、发光或被提示圈覆盖时要有样本。

## 7. 阶段 E：棋局状态、历史与规则验证（P2）

新增模块建议：
- src/maa_xiangqi/games/xiangqi/rules.py
- src/maa_xiangqi/games/xiangqi/history.py
- src/maa_xiangqi/core/state_store.py
- tests/games/xiangqi/

优先评估复用成熟的 Xiangqi 规则库；如果许可证或维护状态不合适，再实现薄适配层。不能只用棋子数量校验局面。

状态至少包括：
- observed_state
- predicted_state
- pending_move
- move_history
- side_to_move
- scene_mode
- confidence map

验证：
- 10 行 × 9 列。
- 棋子字符合法。
- 红黑将帅和位置规则（按标准对局/残局语义区分）。
- 棋子走法、路径阻挡、将帅照面、将军状态。
- 走棋前后棋盘差异与上一手走法一致。
- 不确定行棋方时请求重新采样或人工确认。

FEN 规则：
- 布局从黑方底线到红方底线。
- 大写棋子为红方，小写为黑方。
- side-to-move 的 w 表示红方，b 表示黑方。
- Pikafish 第五字段按其 rule60 语义维护；不能从一张截图伪造历史计数。
- 保存起始 FEN 与 ICCS 走法历史；FEN 不能代替完整历史。

## 8. 阶段 F：Pikafish UCI 引擎适配（P2）

新增：
- src/maa_xiangqi/engines/base.py
- src/maa_xiangqi/engines/pikafish.py
- tests/engines/

要求：
1. 引擎常驻子进程，不要每回合重新启动。
2. 支持 uci / uciok、isready / readyok、ucinewgame、position、go、stop、quit。
3. 解析 info 和 bestmove，支持超时、stderr 日志和进程异常退出。
4. 配置 Threads、Hash、movetime/depth。
5. 通过配置指定二进制和 NNUE 文件路径。
6. 启动时检查文件存在、架构兼容和版本信息。
7. 每次搜索设置位置前先通过本地规则校验 FEN 和 moves。
8. 用固定残局和标准局面做回归测试。

不要把引擎搜索的 bestmove 当作合法性校验；引擎输入本身必须先校验。

## 9. 阶段 G：辅助分析模式（P2）

流程：
1. 截图并等待稳定。
2. 识别 UI 场景。
3. 定位棋盘并识别 90 个棋位。
4. 生成 BoardState。
5. 规则校验；低置信度时不调用引擎。
6. 调用 Pikafish。
7. 显示最佳走法、评估、深度和置信度。
8. 不发送触控。

这是第一个完整端到端里程碑，优先于自动执行模式。

## 10. 阶段 H：授权场景的自动执行（P3）

只在离线练习、残局或明确获准的环境开放。

每回合状态机：
1. 确认场景允许自动执行。
2. 确认当前 observed_state 稳定且轮到本方。
3. 获取引擎走法并进行规则校验。
4. 映射起点、终点屏幕坐标。
5. 点击起点。
6. 确认选中状态。
7. 点击终点。
8. 等待动画。
9. 重新识别棋盘。
10. 比较预期与观测棋盘。
11. 成功才更新历史；失败就暂停并保存诊断包。

必须支持暂停、停止、单步执行、最大重试次数和人工接管。不能在未知弹窗、网络断开、棋盘被遮挡或识别不确定时继续点击。

## 11. 阶段 I：界面和打包（P3）

- MAA General UI 作为主要任务界面。
- 可选 PySide6 独立诊断界面，不要过早开发重复 UI。
- 配置设备、截图、棋盘 profile、场景模式、引擎参数。
- 提供日志、棋盘叠加图、棋谱回放和故障诊断导出。
- 模型和引擎资源独立下载并校验 SHA-256。
- 发布前核对 MaaFramework、模型、引擎及第三方规则库许可证。

## 12. 全局验收指标

| 项目 | 验收要求 |
|---|---|
| ADB 截图 | 100 次截图无损保存成功；失败能定位原因 |
| 棋盘定位 | 多分辨率下 90 点都落在交叉点附近 |
| 棋子识别 | 分场景报告准确率、召回率和混淆矩阵 |
| 状态同步 | 不允许未确认的预测状态覆盖观测状态 |
| 规则验证 | 非法走法和未知局面不会传入引擎 |
| 引擎 | 固定用例稳定返回合法 ICCS bestmove |
| 自动执行 | 单步失败能暂停，不会盲目连走 |
| 回归 | pytest、ruff 和关键集成测试通过 |

## 13. Codex 每次提交的报告格式

每个阶段完成后，Codex 必须报告：
1. 修改的文件。
2. 实现的功能。
3. 运行过的命令。
4. 测试结果（通过、失败、未运行）。
5. 尚存问题。
6. 下一阶段建议。

不要把计划完成描述成代码已经实现。
