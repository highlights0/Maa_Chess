# 棋子识别模型

当前不包含预训练模型。

## 分类目标

15 类分类：

| ID | 标签 | FEN 符号 |
|---:|---|---|
| 0 | empty | . |
| 1–7 | red king / advisor / elephant / horse / rook / cannon / pawn | K A B N R C P |
| 8–14 | black king / advisor / elephant / horse / rook / cannon / pawn | k a b n r c p |

## ONNX 推理契约

模型输入和输出以 `piece_classifier_contract.json` 为准：

- 输入：`float32 [1, 3, 64, 64])，NCHW、RGB、像素除以 255。
- 输出：`float32 [1, 15]` logits，类别顺序与上表一致。
- Python 推理端使用 ONNX Runtime CPU provider；Android 端后续使用 ONNX Runtime Android，并必须复用同一预处理和类别顺序。

安装 Python 推理依赖：

```powershell
pip install -e ".[vision]"
```

## 数据要求

训练数据应覆盖不同棋盘皮肤、分辨率、缩放、选中态、动画前后帧和明暗效果。每个棋位必须人工核验标签；不能把单张截图导出的 90 个棋位图块当成完整训练集。

棋盘识别器采用置信度门控：只要 90 个交叉点中有一个低置信度或 top-1/top-2 差距不足，就不会生成可送入引擎的 BoardState/FEN。此时应重新采样或请求人工确认，不能把猜测当成已确认局面。

目前已实现识别接口与 ONNX 推理适配器，但仓库没有训练数据和模型权重，因此尚未达到可直接识别实战棋子的状态。
