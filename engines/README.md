# 棋类引擎

计划接入 [Pikafish](https://github.com/official-pikafish/Pikafish) 中国象棋引擎。

当前目录不包含引擎二进制或 NNUE 网络文件。请根据目标 CPU 指令集下载或自行构建官方版本，并记录：
- Pikafish 版本 / commit
- 二进制文件名及 SHA-256
- NNUE 网络文件版本及 SHA-256
- 来源和许可证信息

引擎通信使用 UCI。发送 position 前必须完成 FEN 格式和历史走法校验；Pikafish 对非法局面或走法可能以致命错误退出。只提供最终 FEN 会丢失此前的重复、长将和长捉历史，因此需要保留起始局面与走法序列。
