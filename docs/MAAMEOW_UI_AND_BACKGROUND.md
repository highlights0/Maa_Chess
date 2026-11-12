## 当前迁移状态

Maa_Chess 已直接迁移并修改 MAA-Meow Android 工程，位于 `android-app/`。设备管理、Shizuku / Root、后台虚拟显示、远程服务、Surface 与输入桥接和任务生命周期优先复用上游实现，遵循 AGPL-3.0 并保留上游许可证与第三方声明。

# UI 与 Android 后台运行设计

## 1. 参考项目与结论

参考项目：[Aliothmoon/MAA-Meow](https://github.com/Aliothmoon/MAA-Meow)。

该项目不是简单地把游戏窗口最小化，而是在 Android 设备上运行 MAA Core，并通过提权服务创建独立虚拟显示器，再将目标游戏启动到该显示器。自动化核心按 displayId 获取画面和注入触控，因此游戏可以不占用用户正在使用的主显示界面。

从其公开 README 和实现可以确认的设计要点：

- Android 原生界面使用 Jetpack Compose / Material 3。
- 前台运行与后台虚拟显示是两种独立运行模式。
- 通过 Shizuku 或 Root 启动有足够权限的远程服务。
- Legacy 虚拟显示方案使用 Android 显示管理相关接口。
- Android 14+ 增加基于 VirtualDeviceManager 的 VDM 路径。
- 目标应用启动时指定 displayId，并在启动后检查是否被系统移回主显示器。
- MAA Core 连接参数包含显示分辨率和 displayId。
- 后台运行期间监视游戏进程、虚拟显示帧和显示器状态。

重要参考源码：
- [VirtualDisplayManager.kt](https://github.com/Aliothmoon/MAA-Meow/blob/main/app/src/main/java/com/aliothmoon/maameow/remote/internal/VirtualDisplayManager.kt)
- [VdmDisplayController.kt](https://github.com/Aliothmoon/MAA-Meow/blob/main/app/src/main/java/com/aliothmoon/maameow/remote/internal/VdmDisplayController.kt)
- [BackgroundDisplay.kt](https://github.com/Aliothmoon/MAA-Meow/blob/main/app/src/main/java/com/aliothmoon/maameow/remote/internal/BackgroundDisplay.kt)
- [MaaCompositionService.kt](https://github.com/Aliothmoon/MAA-Meow/blob/main/app/src/main/java/com/aliothmoon/maameow/domain/service/MaaCompositionService.kt)
- [DriverClass.java](https://github.com/Aliothmoon/MAA-Meow/blob/main/app/src/main/java/com/aliothmoon/maameow/maa/DriverClass.java)
- [第三方代码声明](https://github.com/Aliothmoon/MAA-Meow/blob/main/docs/zh-cn/develop/THIRD_PARTY_NOTICES.md)

MAA-Meow 的 README 列出了 Android 9+、Shizuku 已运行并授权或 Root、arm64-v8a / x86_64 等要求。这些是参考项目的要求，不代表 MaaXiangqi 已经验证兼容相同设备。

## 2. 许可证与复用边界

MAA-Meow 仓库采用 AGPL-3.0，并声明包含按各自许可证分发的第三方代码。

MaaXiangqi 当前只借鉴其公开架构思路和 UI 交互模式，不复制其源文件、资源、图标、品牌素材或实现代码。后台虚拟显示、远程服务、输入桥接和界面均由本项目独立实现，并优先使用 MaaFramework 官方接口。

如果后续决定直接复用 MAA-Meow 的代码，必须先评估 AGPL-3.0 对分发、修改和网络使用场景的义务，并补齐第三方许可证声明；不能把复制代码当作普通 MIT 代码处理。

## 3. 产品 UI 方向

最终产品优先采用 Android 原生 Jetpack Compose + Material 3。参考 MAA-Meow 的信息层级和卡片式布局，但按象棋任务重新设计，不照搬其页面和素材。

### 3.1 导航结构

首版建议四个底部入口：

1. **首页**：设备连接、天天象棋状态、运行模式、当前场景和开始任务。
2. **任务**：实时棋盘预览、当前行棋方、推荐走法、执行状态、暂停和停止。
3. **棋谱**：历史局面、走法列表、FEN、识别结果和运行日志。
4. **设置**：设备权限、后台显示、引擎、识别阈值和诊断。

首版不需要照搬 MAA-Meow 的定时任务页。等核心对局流程稳定后，再判断是否需要定时启动或计划任务。

### 3.2 首页

首页至少包含：

- 设备卡片：设备型号、Android 版本、分辨率、连接状态。
- 权限卡片：Shizuku / Root 状态、授权状态、远程服务状态。
- 游戏卡片：天天象棋是否安装、包名、是否运行。
- 运行模式：前台 / 后台虚拟显示。
- 场景模式：标准对局 / 残局闯关。
- 工作模式：辅助分析 / 授权场景自动执行。
- 主操作：启动、停止、继续、进入诊断。

设备、权限、虚拟显示、游戏进程和 MAA Core 状态必须分开显示，不要用一个“已连接”状态掩盖不同故障。

### 3.3 任务页

- 显示游戏画面预览；后台模式显示虚拟显示器画面。
- 显示棋盘识别结果和 90 个棋位的可视化覆盖层。
- 显示红黑方、行棋方、当前局面置信度。
- 显示引擎推荐走法、评估和搜索状态。
- 显示走法历史、最近事件和错误原因。
- 提供暂停、停止、单步和人工接管。
- 后台模式下，预览可缩小或隐藏，但不能因此停止采集或改变游戏显示器。

### 3.4 视觉规范

- 使用 Material 3 的颜色体系、Typography、Surface、Card、NavigationBar。
- 采用清晰的页面标题、分组卡片、紧凑状态信息和明确主操作。
- 运行状态使用统一状态模型：未连接、准备中、运行中、暂停、完成、失败。
- 颜色不能作为唯一状态表达，还要有文字和图标。
- 支持浅色 / 深色主题和不同屏幕尺寸。
- 不复制 MAA-Meow 的 Logo、插画、截图、专属图标或源码。

## 4. 三种运行方式

MaaXiangqi 需要区分以下三种概念，不得混为一谈。

### 4.1 Windows ADB 主机模式

- 当前 Python CLI 和 ADB 工具继续保留。
- 由 Windows 通过 ADB 获取 Android 内部截图和发送输入。
- 主要用于开发、数据采集、棋盘校准和回归测试。
- 游戏在 Android 主显示器上运行。
- 最小化模拟器窗口后是否继续渲染，取决于模拟器行为；本模式不承诺真正后台运行。

### 4.2 Android 前台模式

- MaaXiangqi 原生 App 与天天象棋运行在同一 Android 系统。
- 游戏显示在主显示器。
- MAA Core 使用 Android 原生控制能力完成截图和触控。
- 用于无 Shizuku / Root 权限时的基础兼容路径。

### 4.3 Android 后台虚拟显示模式

- 由 Shizuku 或 Root 启动独立的特权服务进程。
- 特权服务创建虚拟显示器，并提供专用 Surface 作为画面采集目标。
- 将天天象棋启动到虚拟显示器的 displayId。
- MAA Core 连接虚拟显示器，截图和输入都必须明确绑定该 displayId。
- MaaXiangqi UI 保留在主显示器，用户可以切换到其他 App。
- 必须持续检查游戏是否仍在目标 displayId 上，以及虚拟显示器是否持续出帧。

该模式才是本项目所说的“类似 MAA-Meow 的后台运行”。

## 5. 后台运行组件设计

建议组件边界：

### Android UI / App Process

负责：
- Compose UI 和用户设置。
- 任务状态展示、日志、棋盘预览。
- 权限引导和运行前检查。
- 通过 Binder/AIDL 与特权服务通信。
- 管理 MAA Core 生命周期和任务生命周期。

### Privileged Remote Service

通过 Shizuku 或 Root 启动，负责：
- 创建和释放虚拟显示器。
- 目标应用启动到指定 displayId。
- 必要的显示器分辨率、方向和焦点管理。
- displayId 定向输入事件。
- 目标显示器画面采集桥接。
- 检测游戏进程退出、显示器漂移和 Binder 死亡。
- 失败时释放显示资源并向主进程报告。

特权服务必须与 UI 进程分离。UI 进程崩溃或切换页面不能直接导致虚拟显示器资源泄漏。

### MaaFramework Runtime

优先评估 MaaFramework 官方 Android Native Control Unit 和当前 MaaCore Android 产物能否满足 displayId 定向截图、触控和游戏启动需求。

如果官方接口无法覆盖虚拟显示器，才实现最小的自有 Android Native Bridge，并使用 MaaFramework 官方扩展点接入。不得直接复制 MAA-Meow 的 libbridge 或隐藏 API 包装代码。

### Display Backend

抽象为 DisplayBackend 接口，至少规划：

- LegacyVirtualDisplay：适配支持传统虚拟显示 API 的系统。
- VirtualDeviceDisplay：Android 14+ 且系统权限 / API 可用时尝试 VirtualDeviceManager。
- Unsupported：当前设备不支持或权限不足时明确报告。

优先自动选择后端，但必须允许诊断页显示实际使用的后端、权限档位和失败原因。不能因为虚拟显示创建失败就静默切回主屏并继续执行。

## 6. 权限、兼容性与限制

- 后台虚拟显示通常需要 Shizuku 授权或 Root 权限；普通应用权限不足以保证创建可信、可触控的独立显示器。
- Shizuku 需要用户自行启动和授权，MaaXiangqi 不得绕过系统授权流程。
- Android 14+ 的 VirtualDevice 路径与传统虚拟显示路径不同，需要按 API 级别和设备能力分别验证。
- OEM ROM 可能拒绝某些虚拟显示标志、将游戏移回主屏、限制后台进程或在锁屏后停止渲染。
- 模拟器是否可用取决于 Android 版本、系统镜像、CPU 架构、Root 开关、Shizuku 可用性和显示管理实现。
- 游戏本身可能拒绝非默认显示器、只在主屏渲染或因窗口模式变化退出。
- 前台服务、持续通知和电池优化设置需要按 Android 版本遵守系统要求。
- 屏幕熄灭 / 锁屏运行是单独的增强目标，不纳入首个后台模式验收。虚拟显示独立运行不等于保证锁屏、息屏时所有游戏都继续出帧。

## 7. 后台会话状态机

至少包括：

- STOPPED
- CHECKING_PERMISSION
- STARTING_REMOTE_SERVICE
- CREATING_DISPLAY
- LAUNCHING_GAME
- CONNECTING_MAA
- RUNNING
- PAUSED
- RECOVERING
- STOPPING
- FAILED

任何阶段失败都必须记录阶段、异常、displayId（若有）、游戏进程状态和日志。不能在虚拟显示器失效、画面黑屏、游戏漂移或远程服务死亡后继续注入触控。

## 8. 开发顺序与关键验证

后台模式是高风险系统能力，应先做最小 PoC，再迁移完整象棋流程。

PoC 验收顺序：

1. 识别当前 Android 版本、设备架构、模拟器类型和权限能力。
2. 检查 Shizuku / Root 服务启动、授权和 Binder 死亡恢复。
3. 创建虚拟显示器，读取真实 displayId、分辨率和刷新状态。
4. 将一个可控测试应用启动到虚拟显示器。
5. 验证采集到的画面确实来自虚拟显示器，而不是主屏。
6. 向虚拟显示器注入触控，并验证只有目标显示器响应。
7. 切换到主屏其他 App，确认后台测试应用仍持续出帧。
8. 验证游戏被系统移回主屏、进程退出、黑屏、服务重启和分辨率改变时能正确报警并停止任务。
9. 最后才接入天天象棋，分别验证标准对局和残局界面。

必须在用户实际使用的模拟器 / 手机上做集成验证。CI 单元测试通过不能代替真实设备后台验证。

## 9. 技术参考

- [MAA-Meow README](https://github.com/Aliothmoon/MAA-Meow)
- [MAA-Meow 后台显示管理](https://github.com/Aliothmoon/MAA-Meow/tree/main/app/src/main/java/com/aliothmoon/maameow/remote/internal)
- [MaaFramework](https://github.com/MaaXYZ/MaaFramework)
- [Shizuku](https://shizuku.rikka.app/)
