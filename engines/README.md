# 棋类引擎

计划接入 [Pikafish](https://github.com/official-pikafish/Pikafish) 中国象棋引擎。

当前目录不包含引擎二进制或 NNUE 网络文件。请根据目标 CPU 指令集下载或自行构建官方版本，并记录：
- Pikafish 版本 / commit
- 二进制文件名及 SHA-256
- NNUE 网络文件版本及 SHA-256
- 来源和许可证信息

引擎通信使用 UCI。发送 position 前必须完成 FEN 格式和历史走法校验；Pikafish 对非法局面或走法可能以致命错误退出。只提供最终 FEN 会丢失此前的重复、长将和长捉历史，因此需要保留起始局面与走法序列。

## Python UCI 适配器

仓库提供本机调试用的 UCI 进程适配器：

```powershell
maa-xiangqi engine-analyze --engine engines/pikafish.exe --fen "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1" --movetime-ms 1000
```

适配器校验 FEN 和 ICCS 历史走法，执行 UCI 握手、设置 Hash / Threads，并返回 bestmove、ponder、搜索分数和主变化。它不会下载或打包引擎、NNUE 文件。

Android 版本仍需单独构建并验证对应 ABI 的 Pikafish 可执行文件，不能将 Windows 引擎二进制直接放进 APK。

## Android arm64 实机验证（2026-11-12）

已从[官方 2026-09-06 发布页](https://github.com/official-pikafish/Pikafish/releases/tag/Pikafish-2026-09-06)取得 `Pikafish.2026-09-06.7z`，并核对发布资产的 SHA-256：

- 发布压缩包：`41952bbfe2520faceb5902c69e6ab4845cc999841d2b49a95cc1be7867a25e5b`
- `Pikafish-Android-arm64-universal`：`6c06b8752e10c1ed605fa836d2c9bbf885e9c402b216023040ddf4586f4320b1`
- 同包 `pikafish.nnue`：`7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e`

在 Android arm64 手机的 shell 中已验证 UCI 握手、NNUE 加载和两次搜索；Python UCI 适配器经 ADB 调用同一引擎，也对该残局返回 `f8e8`（一步杀）。残局测试的红方行棋及计数 `0 1` 是人工给定的测试值，不能视为从截图自动识别。此测试不代表 Android App 已接入引擎或可以自动落子。

该发布包没有 Android x86_64 可执行文件；MuMu 的 x86_64 兼容性尚未验证。二进制和 NNUE 不提交仓库；打包或分发前需核对官方 GPL-3.0 许可证及第三方声明。
