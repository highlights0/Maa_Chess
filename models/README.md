# 棋子识别模型

## 主识别模型：nano-v3

本项目优先复用 MIT 许可的 Chinese Chess Recognition nano-v3 模型，不从零训练棋子分类网络。

- 来源、SHA-256、输入输出形状和预处理：`models/recognition/SOURCE.md`
- 模型文件：`models/xiangqi-nano-v3.onnx`（不提交到 Git）
- 下载和校验：`maa-xiangqi-download-model`
- Python 推理依赖：`pip install -e ".[vision]"`

模型一次推理 90 个交叉点，每个交叉点输出 16 类：空位、未知、红方 7 类、黑方 7 类。未知类、低置信度、非法落子位置、超额棋子和缺失将帅都不会被自动转换成合法局面。

模型预处理严格沿用上游：450×500 网格映射、中心裁剪 400×450、双线性缩放到 280×315、RGB 均值方差归一化。当前实现要求棋盘横竖对齐；MuMu 900×1600 初始配置满足此条件。若棋盘有明显透视或倾斜，需要先做经验证的透视校正，不能直接套用该模型。

## 备用扩展

`piece_classifier_contract.json` 保留为未来自训练的逐棋位分类模型契约；它不是 nano-v3 的模型格式，也不是当前默认识别模型。当前没有训练数据和自训练权重。
