"""Inference adapter for the MIT-licensed Chinese Chess Recognition nano-v3 model."""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Protocol

import cv2
import numpy as np

from maa_xiangqi.board.geometry import BoardGeometry
from maa_xiangqi.board.state import BoardState
from .pieces import BoardRecognition, PiecePrediction

MODEL_SHA256 = "da66ba9809f15127f8ae729b1755e42ee61c100c4f9979ce0ef13602ac471298"
MODEL_SIZE_BYTES = 31_101_356
MODEL_CLASS_SYMBOLS = tuple(".xKABNRCPkabnrcp")
MODEL_INPUT_SHAPE = (1, 3, 315, 280)
MODEL_OUTPUT_SHAPE = (1, 90, 16)
DEFAULT_MIN_CONFIDENCE = 0.80
DEFAULT_MIN_MARGIN = 0.0
_MEAN = np.asarray([123.675, 116.28, 103.53], dtype=np.float64)
_STD = np.asarray([58.395, 57.12, 57.375], dtype=np.float64)


class BoardProbabilityModel(Protocol):
    def predict_probabilities(
        self,
        image: np.ndarray,
        geometry: BoardGeometry,
        *,
        red_at_top: bool = False,
    ) -> np.ndarray: ...


def preprocess_nano_v3(
    image: np.ndarray,
    geometry: BoardGeometry,
    *,
    red_at_top: bool = False,
) -> np.ndarray:
    """Match the upstream 450x500 warp, 400x450 centre crop and 280x315 resize.

    The upstream release accepts an axis-aligned 9x10 grid. The initial MuMu
    profile is axis-aligned; a skewed or perspective-distorted board is rejected
    instead of silently feeding a different geometry to the pretrained model.
    """
    if image is None or image.size == 0 or image.ndim not in {2, 3}:
        raise ValueError("输入棋盘截图无效")
    height, width = image.shape[:2]
    if (width, height) != (geometry.source_width, geometry.source_height):
        geometry = geometry.scaled_to(width, height)

    if image.ndim == 2:
        bgr = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
    elif image.shape[2] == 4:
        bgr = cv2.cvtColor(image, cv2.COLOR_BGRA2BGR)
    elif image.shape[2] == 3:
        bgr = image
    else:
        raise ValueError(f"不支持的截图通道数：{image.shape}")
    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)

    top_left, top_right, bottom_right, bottom_left = geometry.corners
    left = (top_left[0] + bottom_left[0]) / 2.0
    right = (top_right[0] + bottom_right[0]) / 2.0
    top = (top_left[1] + top_right[1]) / 2.0
    bottom = (bottom_left[1] + bottom_right[1]) / 2.0
    spacing_x = (right - left) / 8.0
    spacing_y = (bottom - top) / 9.0
    tolerance = max(2.0, min(spacing_x, spacing_y) * 0.03)
    if (
        spacing_x <= 0
        or spacing_y <= 0
        or abs(top_left[1] - top_right[1]) > tolerance
        or abs(bottom_left[1] - bottom_right[1]) > tolerance
        or abs(top_left[0] - bottom_left[0]) > tolerance
        or abs(top_right[0] - bottom_right[0]) > tolerance
    ):
        raise ValueError(
            "nano-v3 当前适配器要求棋盘横竖对齐；请重新校准四个最外侧交叉点。"
        )

    origin_x, origin_y = left, top
    scale_x, scale_y = (right - left) / 350.0, (bottom - top) / 400.0
    if red_at_top:
        origin_x, origin_y = right, bottom
        scale_x, scale_y = -scale_x, -scale_y

    source_x = np.round(
        (origin_x + (np.arange(400, dtype=np.float64) - 25.0) * scale_x) * 32.0
    ) / 32.0
    source_y = np.round(
        (origin_y + (np.arange(450, dtype=np.float64) - 25.0) * scale_y) * 32.0
    ) / 32.0
    x0 = np.floor(source_x).astype(np.int64)
    y0 = np.floor(source_y).astype(np.int64)
    fx = source_x - x0
    fy = source_y - y0

    def sample(xs: np.ndarray, ys: np.ndarray) -> np.ndarray:
        xx = np.broadcast_to(xs[None, :], (450, 400))
        yy = np.broadcast_to(ys[:, None], (450, 400))
        valid = (xx >= 0) & (xx < width) & (yy >= 0) & (yy < height)
        result = np.zeros((450, 400, 3), dtype=np.float64)
        result[valid] = rgb[yy[valid], xx[valid]].astype(np.float64)
        return result

    top_row = sample(x0, y0) * (1.0 - fx[None, :, None]) + sample(
        x0 + 1, y0
    ) * fx[None, :, None]
    bottom_row = sample(x0, y0 + 1) * (1.0 - fx[None, :, None]) + sample(
        x0 + 1, y0 + 1
    ) * fx[None, :, None]
    crop = np.floor(
        top_row * (1.0 - fy[:, None, None])
        + bottom_row * fy[:, None, None]
        + 0.5
    ).clip(0, 255).astype(np.uint8)

    resize_x = (np.arange(280, dtype=np.float64) + 0.5) * 400.0 / 280.0 - 0.5
    resize_y = (np.arange(315, dtype=np.float64) + 0.5) * 450.0 / 315.0 - 0.5
    ix = resize_x.astype(np.int64)
    iy = resize_y.astype(np.int64)
    wx, wy = resize_x - ix, resize_y - iy

    top_row = crop[iy[:, None], ix[None, :]].astype(np.float64) * (
        1.0 - wx[None, :, None]
    ) + crop[iy[:, None], ix[None, :] + 1].astype(np.float64) * wx[None, :, None]
    bottom_row = crop[iy[:, None] + 1, ix[None, :]].astype(np.float64) * (
        1.0 - wx[None, :, None]
    ) + crop[iy[:, None] + 1, ix[None, :] + 1].astype(np.float64) * wx[None, :, None]
    resized = np.floor(
        top_row * (1.0 - wy[:, None, None])
        + bottom_row * wy[:, None, None]
        + 0.5
    ).clip(0, 255).astype(np.float64)

    normalized = (resized - _MEAN) / _STD
    return np.transpose(normalized.astype(np.float32), (2, 0, 1))[None, ...]


class OnnxNanoV3BoardClassifier:
    """CPU ONNX Runtime adapter for the upstream 90-square nano-v3 model."""

    def __init__(
        self,
        model_path: str | Path,
        *,
        verify_checksum: bool = True,
    ) -> None:
        path = Path(model_path)
        if not path.is_file():
            raise FileNotFoundError(
                f"棋子识别模型不存在：{path}；先运行 maa-xiangqi-download-model。"
            )
        if verify_checksum:
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest != MODEL_SHA256:
                raise ValueError(
                    f"nano-v3 模型 SHA-256 不匹配：{digest}；拒绝加载未经核验的模型。"
                )
        try:
            import onnxruntime as ort
        except ImportError as exc:
            raise RuntimeError(
                "ONNX Runtime 未安装，请执行 pip install '.[vision]'。"
            ) from exc

        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 1
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self._session = ort.InferenceSession(
            str(path),
            sess_options=options,
            providers=["CPUExecutionProvider"],
        )
        inputs, outputs = self._session.get_inputs(), self._session.get_outputs()
        if len(inputs) != 1 or len(outputs) != 1:
            raise ValueError("nano-v3 ONNX 模型必须只有一个输入和一个输出")
        self._input_name = inputs[0].name
        self._output_name = outputs[0].name
        input_shape = tuple(inputs[0].shape)
        output_shape = tuple(outputs[0].shape)
        if all(isinstance(value, int) for value in input_shape) and input_shape != MODEL_INPUT_SHAPE:
            raise ValueError(f"nano-v3 输入形状不匹配：{input_shape}")
        if all(isinstance(value, int) for value in output_shape) and output_shape != MODEL_OUTPUT_SHAPE:
            raise ValueError(f"nano-v3 输出形状不匹配：{output_shape}")

    def predict_probabilities(
        self,
        image: np.ndarray,
        geometry: BoardGeometry,
        *,
        red_at_top: bool = False,
    ) -> np.ndarray:
        tensor = preprocess_nano_v3(image, geometry, red_at_top=red_at_top)
        output = np.asarray(
            self._session.run(
                [self._output_name],
                {self._input_name: tensor},
            )[0],
            dtype=np.float32,
        )
        if output.shape != MODEL_OUTPUT_SHAPE:
            raise ValueError(
                f"nano-v3 输出形状应为 {MODEL_OUTPUT_SHAPE}，实际为 {output.shape}"
            )
        probabilities = output[0]
        if (
            not np.isfinite(probabilities).all()
            or np.any(probabilities < 0)
            or np.any(probabilities > 1.001)
        ):
            raise ValueError("nano-v3 模型返回了无效概率")
        return probabilities


def _can_occupy(symbol: str, row: int, col: int) -> bool:
    if symbol in {"K", "A"}:
        return 7 <= row <= 9 and 3 <= col <= 5
    if symbol in {"k", "a"}:
        return 0 <= row <= 2 and 3 <= col <= 5
    if symbol == "B":
        return 5 <= row <= 9
    if symbol == "b":
        return 0 <= row <= 4
    if symbol == "P":
        return 0 <= row <= 6
    if symbol == "p":
        return 3 <= row <= 9
    return symbol in {".", "N", "R", "C", "n", "r", "c"}


def _validate_position(rows: list[list[str]]) -> tuple[set[tuple[int, int]], str | None]:
    uncertain: set[tuple[int, int]] = set()
    locations: dict[str, list[tuple[int, int]]] = {}
    for row in range(10):
        for col in range(9):
            symbol = rows[row][col]
            if symbol == ".":
                continue
            locations.setdefault(symbol, []).append((row, col))
            if not _can_occupy(symbol, row, col):
                uncertain.add((row, col))

    maximum = {
        "K": 1, "A": 2, "B": 2, "N": 2, "R": 2, "C": 2, "P": 5,
        "k": 1, "a": 2, "b": 2, "n": 2, "r": 2, "c": 2, "p": 5,
    }
    for symbol, squares in locations.items():
        if len(squares) > maximum[symbol]:
            uncertain.update(squares)

    if len(locations.get("K", ())) != 1 or len(locations.get("k", ())) != 1:
        return uncertain, "局面必须且只能识别到一个红帅和一个黑将"
    return uncertain, None


def recognize_nano_v3_board(
    image: np.ndarray,
    geometry: BoardGeometry,
    classifier: BoardProbabilityModel,
    *,
    side_to_move: str,
    red_at_top: bool = False,
    min_confidence: float = DEFAULT_MIN_CONFIDENCE,
    min_margin: float = DEFAULT_MIN_MARGIN,
) -> BoardRecognition:
    """Decode all 90 squares; unknown/low-confidence predictions never become empty."""
    if image is None or image.size == 0:
        raise ValueError("输入棋盘截图无效")
    if side_to_move not in {"w", "b"}:
        raise ValueError("side_to_move 必须为 'w'（红方）或 'b'（黑方）")
    if not 0.0 <= min_confidence <= 1.0 or not 0.0 <= min_margin <= 1.0:
        raise ValueError("识别阈值必须在 0 到 1 之间")

    probabilities = np.asarray(
        classifier.predict_probabilities(image, geometry, red_at_top=red_at_top),
        dtype=np.float64,
    )
    if probabilities.shape != (90, len(MODEL_CLASS_SYMBOLS)):
        raise ValueError(
            f"分类器输出应为 (90, {len(MODEL_CLASS_SYMBOLS)})，实际为 {probabilities.shape}"
        )
    if not np.isfinite(probabilities).all() or np.any(probabilities < 0):
        raise ValueError("分类器输出包含无效概率")

    rows = [["." for _ in range(9)] for _ in range(10)]
    predictions: list[PiecePrediction] = []
    uncertain: set[tuple[int, int]] = set()
    for index in range(90):
        row, col = divmod(index, 9)
        scores = probabilities[index]
        order = np.argsort(scores)
        best, runner_up = int(order[-1]), int(order[-2])
        symbol = MODEL_CLASS_SYMBOLS[best]
        confidence = float(scores[best])
        runner_up_confidence = float(scores[runner_up])
        predictions.append(
            PiecePrediction(
                row=row,
                col=col,
                symbol=symbol,
                confidence=confidence,
                runner_up_symbol=MODEL_CLASS_SYMBOLS[runner_up],
                runner_up_confidence=runner_up_confidence,
            )
        )
        if (
            symbol == "x"
            or confidence < min_confidence
            or confidence - runner_up_confidence < min_margin
        ):
            uncertain.add((row, col))
            continue
        rows[row][col] = symbol

    position_issues, problem = _validate_position(rows)
    uncertain.update(position_issues)
    mean_confidence = float(np.mean([item.confidence for item in predictions]))
    state = None
    if not uncertain and problem is None:
        state = BoardState(rows=rows, side_to_move=side_to_move)

    return BoardRecognition(
        state=state,
        predictions=tuple(predictions),
        uncertain_squares=tuple(sorted(uncertain)),
        mean_confidence=mean_confidence,
        problem=problem,
    )
