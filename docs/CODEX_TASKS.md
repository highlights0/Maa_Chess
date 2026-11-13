# 当前实施状态（2026-11-13）

- 已将 MAA-Meow Android 工程迁移到 android-app/，并开始改造为 Maa_Chess。
- 已完成独立 applicationId、品牌资源、四页导航、天天象棋任务页、包名配置入口。
- 已接入前台无特权启动、后台虚拟显示启动，以及已运行游戏移入虚拟显示器的路径。
- 已按用户设备配置 900×1600、240 DPI 竖屏显示。
- 已添加游戏启动路由和竖屏分辨率单元测试。
- Python 端已接入 MIT 许可 nano-v3 预训练模型、固定预处理、置信度门控和真实模型冒烟测试；本地 67 项 Python 测试与 Ruff 已通过。
- 已用真实手机的标准开局和“第1关 小兵”残局截图验证 nano-v3 棋子识别；这两次使用人工校准 `config/tiantian/phone_1080x2400.yaml` 和人工指定行棋方，原始截图留在本地忽略目录。MuMu 900×1600 旧校准整体偏上约 36 像素，已按现有内部截图修正并目视检查网格。
- Android 端已加入 nano-v3 模型下载 / SHA-256 校验、ONNX Runtime 推理和后台帧采集。`085e911` APK 已在手机后台标准开局识别 32 枚棋子，平均置信度 85.3%；先前的 ONNX 原生库冲突与输出 Optional 解包错误均已修复。
- 已核对官方 Pikafish Android arm64 发布包和 NNUE，并在手机 shell 中完成 UCI 握手及标准开局、该残局的搜索测试；Python UCI 适配器经 ADB 调用也返回该残局的一步杀 `f8e8`。`085e911` APK 已在手机标准开局返回 `b2e2`、深度 17 和评分。官方发布包未提供 Android x86_64 可执行文件，MuMu 引擎兼容性需另行验证。
- 新版棋盘示意图使用与天天象棋画面一致的中文棋子字形，并按 ICCS 坐标标出建议走法起点、终点与箭头。前台模式已接无障碍截图与手机实测四角校准；离线场景单步落子已接入输入前双截图核对、输入后截图复核。上述新版交互已本地编译，仍待真机验收。
- 前台悬浮面板已替换成象棋操作并缩为紧凑布局，游戏内推荐箭头在截图 / 输入期间隐藏。模型已内置到 APK，首次运行离线安装并校验。
- 新增本地棋规与唯一合法的一着 / 两着变化接续；状态留在内部，不要求用户维护记录。识别、分析、输入和停止原因始终写入带流程编号的 `XiangqiFlow` 日志。
- 根据用户提供的两张默认皮肤截图验证了白圈与目的棋子白色高亮线索：红炮 `h2e2` 后轮黑、黑象 `g9e7` 后轮红。Android 检测已接入，包含反向棋盘、模糊标记和合法性过滤单元测试，仍由用户继续实机验证。
- 截图无法提供 rule60、回合数和完整走法历史；Python 识别结果在未知计数时不再生成完整 FEN，避免把推测状态交给引擎。
- 已接入一键识别落子、持续自动下棋与通知栏停止，删除离线 / 新局确认勾选；新棋盘自动切换分析基准，未知历史保留未知标记。推荐路线持续显示，棋盘变化后清除。快速输入改用已识别画面参照核对，落子后完整识别；日志分别记录触摸与复核耗时。
- 用户测试日志确认黑方在下时重复执行正反两次推理，每次约 2.7 秒；已改为优先上次确认的方向。自动模式中的悬浮球可能遮挡棋位，现由棋盘外的简要状态条替代。推理前增加轻量稳定帧检查，并按运行库能力接入 XNNPACK / NNAPI，失败退回 CPU；34 项相关本地测试通过，最新手机连续运行验收待用户进行。
- 尚未完成自动棋盘定位、无标记局面的完整轮次判定、未观测历史还原，以及最新持续路线 / 自动落子 / 两秒输入目标的完整手机验收。不确定棋位和轮次暂停输入；未知历史不参与重复局面裁定。
- 旧版 App 的 16:9 调整按钮曾把手机主屏从 1080×2400 覆盖为 1080×1920，现已通过 ADB 恢复原始尺寸；新版删除主屏尺寸写入接口。后台预览触摸已在真机验证可操作游戏菜单。

**架构边界：** MAA-Meow 的 Arknights AsstAppendTask 不是象棋任务引擎。象棋任务后续使用本项目 MaaFramework pipeline / custom action 与 Xiangqi Core。

# Codex 实施任务书：Maa_Chess

> 仓库：highlights0/Maa_Chess  
> 目标客户端：天天象棋 Android  
> 场景：标准对局 + 残局闯关  
> 最终产品：Android 原生 App（Jetpack Compose / Material 3）  
> 运行后端：ADB 开发模式、Android 前台模式、Android 后台虚拟显示模式

## 0. Codex 执行要求

开始前阅读：

- README.md
- AGENTS.md
- docs/architecture.md
- docs/MAAMEOW_UI_AND_BACKGROUND.md
- docs/MUMU_ENVIRONMENT.md
- docs/HEADLESS_ANDROID_EVALUATION.md
- docs/phase-1.md

先检查当前代码和测试，不要推翻已完成模块。所有新增功能必须配套测试。不能运行的测试必须如实报告。不要提交用户截图、设备序列号、设备本机路径、模型权重或引擎二进制。

优先复用 MaaFramework 官方 Android Native Control Unit / MaaCore、MaaFramework Python Binding、OpenCV、ONNX Runtime 和 Pikafish。使用 MaaFramework 时以官方当前文档、发布产物和源码为准。

MAA-Meow Android 工程已迁移并直接复用，所有衍生修改按 AGPL-3.0 处理，必须保留 LICENSE、版权和第三方声明。

## 1. 产品定义

### 1.1 场景

标准对局和残局闯关必须都能识别。二者共用：

- 棋盘定位和 90 点几何映射。
- 15 类棋位分类。
- BoardState、FEN、ICCS 坐标。
- 棋规校验和引擎接口。

二者分别处理 UI 场景、行棋方判定、棋局开始 / 继续 / 结束、关卡提示和残局完成状态。不能把标准开局棋子数量作为残局的硬性要求。

### 1.2 工作模式

**辅助分析模式**
- 识别棋盘与行棋方。
- 生成棋局状态和 FEN。
- 调用 Pikafish 分析。
- 展示推荐走法和局面评估。
- 不向游戏发送触控。

**自动执行模式**
- 仅在离线练习、残局或明确允许自动化的测试环境启用。
- 每次执行前检查状态和走法。
- 先点起点，再检查选中状态，再点终点。
- 等待动画结束后重新识别。
- 只有识别结果与预期状态一致，才提交本回合。
- 任意不确定状态都暂停，不得盲目继续。

### 1.3 运行后端

必须区分：

1. **Windows ADB 主机模式**：当前 Python 工具，服务于开发、截图采集、校准和调试。不承诺真正后台运行。
2. **Android 前台模式**：原生 App 和 MAA Core 在 Android 运行，游戏位于主显示器。
3. **Android 后台虚拟显示模式**：通过 Shizuku / Root 特权服务创建虚拟显示器，将游戏运行到指定 displayId，MAA Core 定向采集和输入。

只有完成虚拟显示器、目标游戏渲染、定向截图、定向输入和主屏切换验证后，才允许在 UI 中把后台模式标记为可用。

### 1.4 UI 要求

使用 Kotlin + Jetpack Compose + Material 3。借鉴 MAA-Meow 的卡片式布局和状态表达，但按象棋任务重新设计。

首版四个底部入口：

- 首页：设备、权限、游戏状态、运行模式、场景模式和启动操作。
- 任务：棋盘预览、行棋方、识别置信度、引擎推荐、暂停 / 停止 / 单步。
- 棋谱：走法历史、FEN、识别结果、日志和诊断。
- 设置：Shizuku / Root、虚拟显示后端和分辨率、引擎、识别阈值。

设备连接、权限、MAA Core、虚拟显示、游戏进程和任务状态必须分开显示。首版不需要定时任务页。

## 2. 阶段 A 开始时的基础（历史记录）

- ADB 设备发现、设备参数读取、应用启动和 Android 内部截图 CLI。
- MuMu / Android 环境诊断命令 maa-xiangqi-diagnose，支持 API、ABI、Root / Shizuku 线索、游戏包和显示器摘要。
- MuMu 900×1600 初始棋盘校准配置 config/tiantian/mumu_900x1600.yaml。
- 人工四角校准、透视映射、网格叠加图和 90 个棋位图块导出。
- BoardState、Xiangqi FEN 和 ICCS 坐标转换。
- MaaFramework Python Agent 诊断截图动作和 General UI interface/pipeline 初始配置。
- 单元测试和 GitHub Actions。

以上为阶段 A 开始时的清单；当前进度以本文开头的实施状态为准。未完成的能力不得在文档或 UI 中宣称已完成。

## 3. 阶段 A：完成现有 ADB 开发工具验收（P0）

任务：

1. 在 Windows Python 3.10+ 环境安装项目依赖。
2. 运行 pytest 和 ruff。
3. 用 MuMu 设备执行 maa-xiangqi-diagnose --serial <serial> --output debug/mumu-diagnostics.json。
4. 如用户明确同意，再加 --probe-root 测试 su -c id；不要默认触发 Root 授权。
5. 用实际模拟器验证 adb devices、device-info、capture、launch。
6. 使用 config/tiantian/mumu_900x1600.yaml 生成 overlay 和 90 个 crops，并人工检查。
7. 验证截图分辨率变化时的缩放策略。
8. 将真实设备截图、诊断日志和设备序列号保留在本地 debug，不提交仓库。

验收：

- CLI 设备选择无歧义。
- 截图为 Android 内部画面。
- 网格交点对齐实际棋位。
- 90 个 crops 全部可打开。
- pytest 和 ruff 通过。

## 4. 阶段 B：后台虚拟显示 PoC（最高优先级 P0，先于完整棋子识别）

这是本项目最大的系统风险。必须先在用户实际使用的模拟器 / 手机上验证，不要先开发完整 UI 或棋子模型。优先用已固定到 experiments/headless-android 的 Apache-2.0 PoC 验证，不要先重写虚拟显示、ImageReader 和 displayId 定向输入。

### B0. 复用现有后台显示 PoC

- 先阅读 docs/HEADLESS_ANDROID_EVALUATION.md。
- experiments/headless-android 固定引用 HakaI963/headless-android 的指定 commit，许可证为 Apache-2.0。
- 先构建并安装 PoC APK，使用其 Shizuku 服务创建虚拟显示器、启动原始已安装游戏、预览帧并注入触控。
- 只使用 Launch installed app 路径；不要使用 Clone / APK repackaging 功能，避免更改天天象棋包名和签名。
- 在 MuMu 验证通过前，不要将该 PoC 描述为 Maa_Chess 已具备后台能力。

### B1. 环境探测

采集并展示：

- 模拟器品牌、版本和系统镜像。
- Android API 级别。
- CPU ABI（arm64-v8a / x86_64 等）。
- 天天象棋实际包名和版本。
- Root 是否可用。
- Shizuku 是否安装、运行和授权。
- 当前设备支持的显示管理后端。

未经设备验证的包名不能当成事实。

### B2. 特权服务

实现独立的 Privileged Remote Service：

- 通过 Shizuku 或 Root 启动。
- 使用 Binder / AIDL 与主 App 通信。
- 有明确的连接、断开、死亡、超时和重连状态。
- UI 进程死亡不能造成虚拟显示资源永久泄漏。
- Shizuku 授权必须由用户明确完成，不得绕过系统授权。

### B3. 虚拟显示后端

抽象 DisplayBackend：

- LegacyVirtualDisplay：传统虚拟显示路径。
- VirtualDeviceDisplay：Android 14+ 且系统能力满足时使用 VirtualDeviceManager。
- Unsupported：权限或设备能力不足时明确报告。

要求：

1. 创建虚拟显示器并读取真实 displayId。
2. 输出请求和实际生效的显示器参数。
3. 分辨率变化时安全释放并重建采集资源。
4. 释放顺序必须避免 Surface、VirtualDisplay 和 native capture 资源泄漏。
5. 不支持时明确失败，不能静默回退主屏继续执行。

### B4. 游戏启动、采集和输入 PoC

先用可控测试应用，再测试天天象棋：

1. 启动目标应用到指定 displayId。
2. 检查应用是否仍在该 displayId，不能只依赖启动命令返回成功。
3. 将专用 Surface 连接到帧采集桥接。
4. 证明捕获到的是虚拟显示器画面，而不是主屏截图。
5. 向指定 displayId 注入触控，证明主屏不会误响应。
6. 切换到主屏其他 App，验证虚拟显示器仍持续出帧。
7. 验证目标应用被移回主屏、进程退出、黑屏、Binder 死亡、服务重启和分辨率改变时能报警并停止任务。

### B5. MAA Core 集成

优先验证 MaaFramework 官方 Android Native Control Unit / MaaCore 是否能完成 displayId 定向截图、触控和游戏启动。

如果官方接口无法覆盖虚拟显示器场景，再开发本项目自己的最小 Native Bridge，并通过 MaaFramework 官方扩展点接入。不得复制 MAA-Meow 的 libbridge 或隐藏 API 包装代码。

### B6. PoC 验收

- 能稳定创建和释放虚拟显示器。
- displayId 真实有效。
- 目标游戏在虚拟显示器上持续渲染。
- 采集帧来自目标虚拟显示器。
- 触控只作用于目标虚拟显示器。
- 主屏可切换其他 App。
- 进程退出、显示器漂移、黑屏和服务死亡均能被检测。
- 失败时不会继续发送触控。

如果当前模拟器不支持，先提交诊断报告和可行替代方案，不得把 ADB 主机模式描述成等价的后台模式。

## 5. 阶段 C：Android 原生 App 和 MAA Core 前台运行（P1）

建议目录：

- android-app/：Kotlin / Jetpack Compose / Material 3。
- android-app/.../service/：Foreground Service 和任务生命周期。
- android-app/.../remote/：Binder / AIDL 客户端。
- android-app/.../maa/：MaaCore 包装和运行时。
- android-app/.../presentation/：首页、任务、棋谱、设置。
- native/：仅放官方扩展点需要的 JNI / C++ 桥接。
- src/maa_xiangqi/：保留现有 Python 开发工具和离线算法原型。

任务：

1. 创建 Android Gradle 工程，配置 arm64-v8a；如目标模拟器需要，再增加 x86_64。
2. 使用官方 MaaFramework Android 产物，核对 MaaCore 与 Android Native Control Unit 版本兼容。
3. 先连接主显示器，验证截图、触控和任务生命周期。
4. 创建持续运行通知和必要的前台服务。
5. 设计设备、权限和任务状态的单一数据源。
6. 实现 Material 3 四页导航和浅色 / 深色主题。

验收：安装 APK 后可以连接目标设备、读取主屏画面、运行诊断任务并安全停止。

## 6. 阶段 D：天天象棋 UI 状态识别（P1）

建议模块：

- src/maa_xiangqi/adapters/tiantian/ui_state.py
- src/maa_xiangqi/adapters/tiantian/scene_detector.py
- src/maa_xiangqi/adapters/tiantian/profile.py
- Android runtime 对应的接口实现和测试。

状态枚举至少包括：

- UNKNOWN
- HOME
- MODE_SELECT
- STANDARD_GAME
- PUZZLE_GAME
- RESULT
- DIALOG / BLOCKED

要求：

1. 先用模板匹配、OCR 和稳定 UI 特征实现，不需要先训练大模型。
2. 每个状态输出置信度和证据。
3. 连续多帧稳定后才切换状态。
4. 弹窗、广告、加载和动画不能误判为棋局。
5. 标准对局与残局闯关必须分别提供测试截图集。
6. 游戏包名可配置，并从实际设备核实。

## 7. 阶段 E：棋盘定位和几何自动校准（P1）

新增模块建议：

- src/maa_xiangqi/vision/board_locator.py
- src/maa_xiangqi/vision/grid_detector.py
- tests/vision/

保留人工四角校准作为 fallback。自动定位器输出：

- 棋盘四角（TL, TR, BR, BL）。
- 置信度和估计 ROI。
- 90 个交叉点。
- 校准误差。

用 90 点网格拟合验证，而不是只依赖木质外框。楚河汉界文字、装饰线和九宫斜线都可能干扰线段检测。

## 8. 阶段 F：棋子识别数据与模型（P1/P2）

### 当前实现更新（2026-11-12）

已优先复用 MIT 许可的 Chinese Chess Recognition nano-v3 预训练模型，不从零训练第一版分类器。模型一次输出 90 个交叉点、16 类（空位、未知、红方 7 子、黑方 7 子），输入为 RGB NCHW float32 `[1,3,315,280]`，输出 `[1,90,16]`。模型下载固定到已核验 revision，并在下载后检查文件大小与 SHA-256；模型权重不提交 Git。

已实现 Python 与 Android 两端的推理适配和置信度 / 棋子数量 / 宫位合法性门控。Android 识别入口目前使用 MuMu 900×1600 人工四角校准，并从后台虚拟显示 Surface 获取帧；尚未完成自动定位、前台采集和实机准确率验收。未知棋位不得生成可供引擎使用的完整 FEN。

目标类别：

- `.`：empty
- `x`：unknown / occluded
- `KABNRCP`：红帅、仕、相、马、车、炮、兵
- `kabnrcp`：黑将、士、象、马、车、炮、卒

后续只有在实机样本验证显示 nano-v3 对天天象棋皮肤存在系统性缺陷时，才启动自建数据集和微调 / 替代模型。

任务：

1. 使用用户提供的 MuMu 实际截图和不同阶段截图建立独立验收集，不提交含账号信息的原始截图。
2. 对比模型输出和人工标注，分别统计棋子类型、阵营、空位、unknown 的准确率和召回率。
3. 在 Android 真机 / MuMu 上验证 Bitmap 预处理与 Python 参考实现数值一致。
4. 测试不同分辨率、缩放、选中态、动画、遮挡和棋盘方向。
5. 只有 nano-v3 对天天象棋皮肤存在稳定系统性错误时，才建立标注集训练替代模型。
6. 低置信度、unknown、超额棋子或非法宫位一律阻止生成完整 FEN。

## 9. 阶段 G：棋局状态、历史与规则验证（P2）

建议模块：

- src/maa_xiangqi/games/xiangqi/rules.py
- src/maa_xiangqi/games/xiangqi/history.py
- src/maa_xiangqi/core/state_store.py
- tests/games/xiangqi/

优先评估复用成熟 Xiangqi 规则库；若许可证或维护状态不合适，再实现薄适配层。

状态至少包括：

- observed_state
- predicted_state
- pending_move
- move_history
- side_to_move
- scene_mode
- confidence map

验证棋盘尺寸、棋子字符、将帅、走法、路径阻挡、将帅照面、将军状态，以及前后局面变化是否对应上一手走法。

FEN 从黑方底线到红方底线，大写红方、小写黑方；side-to-move 的 w 表示红方，b 表示黑方。Pikafish rule60 不能从一张截图伪造。保存起始 FEN 与 ICCS 走法历史。

## 10. 阶段 H：Pikafish UCI 引擎适配（P2）

建议模块：

- src/maa_xiangqi/engines/base.py
- src/maa_xiangqi/engines/pikafish.py
- tests/engines/

要求：

1. 引擎常驻子进程。
2. 支持 uci / uciok、isready / readyok、ucinewgame、position、go、stop、quit。
3. 解析 info 和 bestmove，支持超时和异常退出。
4. 配置 Threads、Hash、movetime/depth。
5. 为 Android 目标 ABI 提供匹配的引擎构建或官方二进制。
6. 启动时校验二进制、NNUE 文件、版本和 SHA-256。
7. 引擎输入必须先通过本地规则验证。

## 11. 阶段 I：辅助分析模式（P2）

流程：

1. 截图并等待稳定。
2. 识别 UI 场景。
3. 定位棋盘并识别 90 个棋位。
4. 生成 BoardState。
5. 规则校验；低置信度时不调用引擎。
6. 调用 Pikafish。
7. 展示推荐走法、评估、深度和置信度。
8. 不发送触控。

这是第一个完整端到端里程碑，优先于自动执行。

## 12. 阶段 J：授权场景的自动执行（P3）

仅在离线练习、残局或明确获准的环境开放。

每回合：

1. 确认场景允许自动执行。
2. 确认 observed_state 稳定且轮到本方。
3. 获取引擎走法并进行规则校验。
4. 映射起点、终点坐标，并附带当前 displayId。
5. 点击起点并确认选中状态。
6. 点击终点。
7. 等待动画结束。
8. 重新识别棋盘。
9. 比较预期与观测棋盘。
10. 成功才更新历史；失败暂停并保存诊断包。

支持暂停、停止、单步、最大重试次数和人工接管。未知弹窗、网络断开、棋盘遮挡、虚拟显示失效或识别不确定时不能继续点击。

## 13. 阶段 K：打包与发布（P3）

- Android APK 作为最终产品。
- Windows Python CLI 作为开发和诊断工具。
- 提供权限引导、后台模式兼容性诊断和故障导出。
- 模型、引擎和 NNUE 资源独立下载并校验 SHA-256。
- 发布前核对 MaaFramework、模型、引擎及第三方规则库许可证。
- 不打包未经许可的 MAA-Meow 源码或素材。

## 14. 全局验收指标

| 项目 | 验收要求 |
|---|---|
| ADB 开发工具 | 设备发现、截图、校准和 90 crops 正常 |
| 后台虚拟显示 | 目标应用在虚拟屏持续出帧，主屏可切换其他 App |
| 定向输入 | 触控只作用于目标 displayId |
| 生命周期 | 游戏退出、显示漂移、黑屏、服务死亡能检测并停止 |
| 棋盘定位 | 多分辨率下 90 点落在交叉点附近 |
| 棋子识别 | 分场景报告准确率、召回率和混淆矩阵 |
| 状态同步 | 未确认的预测状态不能覆盖观测状态 |
| 规则验证 | 非法走法和未知局面不会传入引擎 |
| 引擎 | 固定用例稳定返回合法 ICCS bestmove |
| 自动执行 | 单步失败能暂停，不会盲目连走 |
| 回归 | pytest、ruff、Android 单元测试和关键设备集成测试通过 |

## 15. Codex 每次提交的报告格式

每个阶段完成后报告：

1. 修改的文件。
2. 实现的功能。
3. 运行过的命令。
4. 测试结果（通过、失败、未运行）。
5. 实际测试设备 / Android API / ABI。
6. 尚存问题。
7. 下一阶段建议。

不要把计划完成描述成代码已经实现。
