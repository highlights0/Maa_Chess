# Codex / Agent 协作约定

在修改本仓库前，先阅读 README.md、AGENTS.md、docs/architecture.md、docs/MAAMEOW_UI_AND_BACKGROUND.md、docs/MUMU_ENVIRONMENT.md、docs/HEADLESS_ANDROID_EVALUATION.md 和 docs/CODEX_TASKS.md。

## 工程原则

1. 当前 Python 包是 Windows 开发 / 诊断工具；最终产品 UI 目标是 Android 原生 Jetpack Compose / Material 3。
2. MuMu 环境诊断命令只收集只读系统信息；Root 探测必须显式启用，诊断报告不得包含设备序列号。
3. Android 前台模式与 Android 后台虚拟显示模式是不同后端。ADB 主机模式不承诺真正后台运行。
4. 后台模式必须先验证 Shizuku / Root、虚拟显示器、displayId 定向截图、displayId 定向输入和游戏进程驻留。
5. 使用 MaaFramework Android Native Control Unit / MaaCore 时，以官方当前文档、发布产物和源码为准，不凭记忆编造接口。
6. MAA-Meow 仅作为公开架构和 UI 交互参考。不要复制其 AGPL-3.0 源码、图片、图标或品牌素材；若决定复用任何代码，必须先完成许可证评估。
7. 标准对局和残局闯关共享棋盘状态、棋子识别和象棋规则；场景状态机分别处理。
8. 逻辑棋盘固定为 9 列 × 10 行。图像 row=0 是黑方底线，row=9 是红方底线；ICCS rank 与图像 row 方向相反。
9. 任何自动输入前都必须确认当前画面、行棋方、起点棋子和目标位置；输入后重新截图验证。
10. 预测状态不能覆盖视觉确认状态。识别低置信度、画面动画中、局面非法或输入未确认时，必须暂停并保留诊断信息。
11. 不得将估算的截图坐标、未训练模型、未验证的虚拟显示兼容性或未验证的引擎二进制写成已完成能力。
12. 默认不启用联网排位或真人竞技自动代打。自动输入仅用于离线练习、残局、测试环境或平台明确允许的场景。
13. 后台虚拟显示优先评估 experiments/headless-android 中固定的 Apache-2.0 PoC；不要重复实现已有的 VirtualDisplay / ImageReader / Shizuku / displayId 输入机制。不得调用 PoC 的 Clone / APK 重签功能处理天天象棋，除非用户另行明确要求并评估风险。
14. 新增功能必须配套测试；不能运行的测试必须如实报告。
15. 不要把用户的原始截图、设备序列号、ADB 日志或本机路径提交到仓库。debug/ 和 data/dataset/ 已加入 gitignore。
16. **所有 Git Commit 标题和说明必须使用中文。** 功能开发、缺陷修复、文档更新、测试、重构和依赖调整均使用中文提交说明；不得使用英文 Commit Message。

## 代码规范

- Python 开发工具：Python 3.10+，类型注解优先，运行 ruff 和 pytest。
- Android App：Kotlin，优先使用 Jetpack Compose / Material 3；系统服务和后台能力需有明确接口边界。
- Native 代码：仅在官方 MaaFramework 扩展接口或 Android 系统 API 确实需要时引入，避免重复造轮子。
- 模型、引擎和 NNUE 大文件不直接提交到 Git；使用独立下载步骤并记录 SHA-256、版本和许可证。
