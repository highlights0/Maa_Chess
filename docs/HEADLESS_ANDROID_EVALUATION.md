# 后台虚拟显示 PoC 候选项目评估

## 结论

建议先复用并验证 Apache-2.0 项目 HakaI963/headless-android，而不是立即从零实现虚拟显示、采集和输入注入。

仓库地址：https://github.com/HakaI963/headless-android  
本仓库固定引用：20646ebff1cf19c86c27e001408028dd5dca6d13  
本地路径：experiments/headless-android

该项目的核心能力与本项目的后台 PoC 高度匹配：

- 通过 Shizuku UserService 在 shell / root 身份下创建 Android VirtualDisplay。
- 通过 ImageReader 直接采集虚拟显示器帧。
- 通过 InputManager.injectInputEvent 和 InputEvent.setDisplayId 将触控事件定向到指定 displayId。
- 通过 am start --display 启动原始已安装应用到虚拟显示器。
- 提供 displayId、分辨率、显示状态、帧计数和最近出帧时间等状态信息。
- 使用 wake lock 尝试保持虚拟显示器持续合成。

其 README 声明最低 Android 版本为 Android 10（API 29），使用 Shizuku API 13.1.5，公开测试设备为 Android 16 / HyperOS。**这些条件不代表已在 MuMu 上验证。**

## 与 MAA-Meow 的关系

MAA-Meow 提供完整的 Android 原生 MAA 运行时和后台任务体验，包含传统虚拟显示与 Android 14+ VirtualDevice 路径。

Headless Android 更适合作为本项目当前阶段的独立后台显示 PoC：范围更小，直接提供 VirtualDisplay + ImageReader + displayId 定向输入 + 已安装应用启动。它没有 MAA Core 和象棋识别，需要后续接入本项目。

推荐路线：

1. 用 Headless Android 验证 MuMu 的虚拟显示能力。
2. 若通过，提取并适配必要的显示、采集、输入和 Shizuku 服务边界。
3. 将其接入 Maa_Chess Android App / MaaFramework Runtime。
4. 象棋棋盘识别、Pikafish 和任务状态机继续由 Maa_Chess 自己维护。

## 许可证与复用边界

Headless Android 仓库使用 Apache License 2.0，并声明其显示引擎来源于 Apache-2.0 的 ShadowAuto。

当前通过 Git submodule 固定上游源码，不把代码复制进 Maa_Chess。若后续移植源文件，必须：

- 保留原始版权和许可证声明。
- 在第三方声明中注明 HakaI963/headless-android 与 ShadowAuto 的来源。
- 随分发物附带 Apache License 2.0 文本。
- 记录修改过的文件和修改内容。
- 同时核对 Shizuku API、Android SDK 和其他依赖各自的许可证。

## 明确不使用的功能

Headless Android 同时提供 Clone / APK repackaging / resigning 功能。天天象棋 PoC 不需要这些功能。

**只使用 Launch installed app 路径，直接启动设备上原始安装的天天象棋。不要 Clone，不要修改原 APK，不要重签名。** 克隆会改变包名和签名，可能导致游戏身份校验、登录或完整性检查失败。

## 构建 PoC

先初始化 submodule：

    git submodule update --init --recursive

环境要求：

- JDK 17。
- Android SDK / Build Tools，项目 compileSdk 为 36。
- MuMu 已开启 ADB，且设备可通过 ADB 访问。
- MuMu 内安装 Shizuku，并确认服务已启动。
- 将 Shizuku 授权给 Headless Android PoC。

Windows PowerShell：

    cd experiments/headless-android
    .\gradlew.bat assembleDebug

APK 预期位置：

    app\build\outputs\apk\debug\app-debug.apk

安装：

    adb -s <MuMu设备序列号> install -r app\build\outputs\apk\debug\app-debug.apk

若当前 MuMu 的 ADB 未连接，先参考 MuMu 官方开发者手册。默认常见端口为 127.0.0.1:7555，但应以 MuMu 当前实例显示的 ADB 端口为准。

## MuMu PoC 操作

1. 在 MuMu 内启动 Shizuku，并在 Headless Android 中授予权限。
2. 启动 shell engine。
3. 创建虚拟显示器。首轮可尝试 900×1600；DPI 使用诊断报告中设备实际值，不要随意猜测。
4. 在应用列表选择已安装的天天象棋，使用普通 Launch，不要 Clone。
5. 确认状态页报告的 displayId 不等于主显示器 ID。
6. 观察预览帧、frame count 和 last frame 时间。
7. 使用预览输入功能做一次无风险测试，确认点击只作用于虚拟显示器。
8. 切换 MuMu Android 主显示器上的其他应用，确认目标游戏仍持续出帧。
9. 再最小化 Windows 上的 MuMu 窗口，等待一段时间后恢复窗口，确认游戏画面和帧计数是否继续。

第 8 步和第 9 步是两个不同测试。Android 内部切换主屏应用成功，不代表 Windows 最小化 MuMu 后模拟器仍会持续渲染。

## 后台模式验收标准

只有以下全部通过，才可认为 MuMu 后台虚拟显示 PoC 成功：

- 创建的 displayId 是独立显示器，不是默认主显示器。
- 天天象棋原始安装包实际运行在目标 displayId。
- 预览帧来自虚拟显示器，不是主屏截图。
- 帧计数持续增长，且无持续黑屏。
- 输入注入绑定目标 displayId，不误触主屏。
- Android 主屏切换其他 App 后，游戏继续运行。
- Windows 最小化 MuMu 后，恢复窗口时游戏状态仍正常；若帧被冻结，应单独记录 MuMu 主机窗口的后台渲染限制。
- 释放虚拟显示器后，Surface、ImageReader、WakeLock 和 Shizuku 服务资源能正确回收。

## 失败时的处理

如果 PoC 失败，保存 Headless Android 状态 JSON 和 log tail，并与 maa-xiangqi-diagnose 生成的 MuMu 环境报告一起分析。重点区分：

- Shizuku 服务未启动或未授权。
- shell / root 权限不足，VirtualDisplay flags 被拒绝。
- MuMu Android 镜像不支持创建可用的虚拟显示器。
- MuMu 不允许应用启动到非主 displayId。
- 显示器已创建，但 ImageReader 没有持续帧。
- InputManager 不支持或拒绝 displayId 定向注入。
- MuMu Windows 窗口最小化时暂停虚拟机 GPU / 帧合成。

不要通过静默回退到主屏来伪造后台模式成功。
