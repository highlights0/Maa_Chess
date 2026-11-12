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
