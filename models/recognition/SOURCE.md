# nano-v3 象棋棋盘识别模型来源

- 模型：Chinese Chess Recognition / Swin Transformer V2 multi-region classifier
- 上游代码作者：[TheOne1006/chinese-chess-recognition](https://github.com/TheOne1006/chinese-chess-recognition)
- 发布页：[yolo12138/Chinese_Chess_Recognition](https://huggingface.co/spaces/yolo12138/Chinese_Chess_Recognition)
- 固定版本：`4bcaf91777effa645040ee76d008d45c2ffc03f3`
- 文件：`onnx/layout_recognition/nano_v3-0319.onnx`
- 大小：31,101,356 bytes
- SHA-256：`da66ba9809f15127f8ae729b1755e42ee61c100c4f9979ce0ef13602ac471298`
- 许可：上游 Hugging Face Space 的 README 元数据声明 MIT；本目录保留 MIT 条款和归属说明。

模型一次输出 90 个交叉点、每点 16 类，类别顺序为 `.xKABNRCPkabnrcp`：
- `.`：空位
- `x`：未知 / 遮挡
- `KABNRCP`：红帅、仕、相、马、车、炮、兵
- `kabnrcp`：黑将、士、象、马、车、炮、卒

预处理沿用上游发布实现：450×500 对齐棋盘映射、中心裁剪 400×450、双线性缩放 280×315、RGB mean/std 归一化。模型输入为 float32 NCHW `[1,3,315,280]`，输出为 `[1,90,16]`。红方在上方时，采样坐标按 180° 反向读取，使模型输出仍为黑方在上、红方在下。

当前适配器只接受横竖对齐的棋盘四角；不对透视或倾斜画面伪装为已支持。MuMu 900×1600 配置满足该条件。

下载后必须通过文件大小与 SHA-256 双重校验。模型文件不提交到 Git；由 `maa-xiangqi-download-model` 下载到本地。
