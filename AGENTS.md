# Codex / Agent 协作约定

修改仓库前先阅读根目录 README.md、AGENTS.md、docs/architecture.md、docs/CODEX_TASKS.md，以及 android-app/README.md 和 android-app/docs/zh-cn/develop/THIRD_PARTY_NOTICES.md。

## 工程原则

1. **Android App 是当前主线。** 主要交付是可安装的 Maa_Chess APK；Python 工具保留为开发、校准和离线算法工具。
2. MAA-Meow 是 Android 基础工程，允许直接复用和修改其 UI、设备管理、Shizuku / Root、后台虚拟显示、Surface / 输入桥接、MAA Core 库装载、日志和任务生命周期。
3. 复用 MAA-Meow 必须遵守 AGPL-3.0：保留 LICENSE、版权和第三方声明；分发衍生版本时提供对应完整源代码。
4. **不要把 Arknights 的 AsstAppendTask 任务类型当作象棋任务。** 象棋任务必须由本项目自己的 MaaFramework pipeline / custom action 和 Xiangqi Core 承担。
5. 前台模式通过 Android 正常启动器启动游戏；后台模式需要 Shizuku / Root、虚拟显示器、displayId 定向 Surface 和输入。
6. MuMu 初始目标配置为 900×1600、240 DPI；天天象棋已核实包名为 com.tencent.qqgame.xq，但仍允许用户在设置中覆盖。
7. 标准对局和残局闯关共用棋盘状态、棋子识别和象棋规则；场景流程分别处理。
8. 逻辑棋盘固定为 9 列 × 10 行。图像 row=0 是黑方底线，row=9 是红方底线；ICCS rank 与图像 row 方向相反。
9. 任何自动输入前都必须确认当前画面、行棋方、起点棋子和目标位置；输入后重新截图验证。
10. 预测状态不能覆盖视觉确认状态。识别低置信度、画面动画中、局面非法或输入未确认时，必须暂停并保留诊断信息。
11. 不得将估算坐标、未训练模型、未验证的虚拟显示兼容性或未验证的引擎二进制写成已完成能力。
12. 默认只在离线练习、残局或明确允许自动化的测试场景实现自动输入。
13. 不要把用户原始截图、设备序列号、ADB 日志、签名文件或本机路径提交到仓库。
14. Android App 默认构建 arm64-v8a 与 x86_64，保证 MuMu x86_64 和常见 ARM64 真机都能安装。
15. **所有 Git Commit 标题和说明必须使用中文。**

## 代码规范

- Android：Kotlin、Jetpack Compose、Material 3；优先复用上游现有组件。
- Python：3.10+，类型注解优先，运行 ruff 和 pytest。
- Native 代码：只在 MaaFramework 官方扩展点或 Android 系统 API 确实需要时引入。
- 模型、引擎和 NNUE 大文件不直接提交到 Git；记录版本、SHA-256 和许可证。
