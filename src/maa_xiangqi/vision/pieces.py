"""Confidence-gated Xiangqi board recognition and ONNX piece classifier."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import cv2
import numpy as np

from maa_xiangqi.board.geometry import BoardGeometry
from maa_xiangqi.board.state import BoardState

PIECE_SYMBOLS = (".", "K", "A", "B", "N", "R", "C", "P", "k", "a", "b", "n", "r", "c", "p")
PIECE_LABELS = (
    "empty",
    "red_king",
    "red_advisor",
    "red_elephant",
    "red_horse",
    "red_rook",
    "red_cannon",
    "red_pawn",
    "black_king",
    "black_advisor",
    "black_elephant",
    "black_horse",
    "black_rook",
    "black_cannon",
    "black_pawn",
)
DEFAULT_INPUT_SIZE = 64


class PieceClassifier(Protocol):
    """A classifier returns one probability per symbol in PIECE_SYMBOLS order."""

    def predict_proba(self, image: np.ndarray) -> Sequence[float]: ...


@dataclass(frozen=True)
class PiecePrediction:
    row: int
    col: int
    symbol: str
    confidence: float
    runner_up_symbol: str
    runner_up_confidence: float


@dataclass(frozen=True)
class BoardRecognition:
    """A state is emitted only when every square passes confidence checks."""

    state: BoardState | None
    predictions: tuple[PiecePrediction, ...]
    uncertain_squares: tuple[tuple[int, int], ...]
    mean_confidence: float

    @property
    def is_complete(self) -> bool:
        return self.state is not None


@dataclass(frozen=True)
class RecognitionThresholds:
    min_confidence: float = 0.82
    min_margin: float = 0.18
    half_size_ratio: float = 0.42

    def validate(self) -> None:
        if not 0.0 <= self.min_confidence <= 1.0:
            raise ValueError("min_confidence 必须在 0 到 1 之间")
        if not 0.0 <= self.min_margin <= 1.0:
            raise ValueError("min_margin 必须在 0 到 1 之间")
        if not 0.20 <= self.half_size_ratio <= 0.48:
            raise ValueError("half_size_ratio 必须在 0.20 到 0.48 之间")


class OnnxPieceClassifier:
    """Run a 15-class ONNX model using the repository's fixed image contract.

    Input: RGB float32 NCHW, 64x64, values in [0, 1].
    Output: 15 logits in PIECE_SYMBOLS order.
    """

    def __init__(
        self,
        model_path: str | Path,
        *,
        input_size: int = DEFAULT_INPUT_SIZE,
    ) -> None:
        path = Path(model_path)
        if not path.is_file():
            raise FileNotFoundError(f"棋子识别模型不存在：{path}")
        if input_size <= 0:
            raise ValueError("input_size 必须为正数")
        try:
            import onnxruntime as ort
        except ImportError as exc:
            raise RuntimeError(
                "ONNX 推理依赖未安装，请执行 pip install '.[vision]'"
            ) from exc

        self.input_size = input_size
        self._session = ort.InferenceSession(
            str(path),
            providers=["CPUExecutionProvider"],
        )
        inputs = self._session.get_inputs()
        outputs = self._session.get_outputs()
        if len(inputs) != 1 or len(outputs) != 1:
            raise ValueError("棋子 ONNX 模型必须只有一个输入和一个输出")
        self._input_name = inputs[0].name
        self._output_name = outputs[0].name

    def predict_proba(self, image: np.ndarray) -> np.ndarray:
        if image is None or image.size == 0:
            raise ValueError("棋位图像为空")
        if image.ndim == 2:
            bgr = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        elif image.ndim == 3 and image.shape[2] == 4:
            bgr = cv2.cvtColor(image, cv2.COLOR_BGRA2BGR)
        elif image.ndim == 3 and image.shape[2] == 3:
            bgr = image
        else:
            raise ValueError(f"不支持的棋位图像形状：{image.shape}")

        rgb = cv2.cvtColor(
            cv2.resize(bgr, (self.input_size, self.input_size), interpolation=cv2.INTER_AREA),
            cv2.COLOR_BGR2RGB,
        )
        tensor = np.transpose(rgb.astype(np.float32) / 255.0, (2, 0, 1))[None, ...]
        logits = np.asarray(
            self._session.run(
                [self._output_name],
                {self._input_name: tensor},
            )[0],
            dtype=np.float32,
        ).reshape(-1)
        if logits.size != len(PIECE_SYMBOLS) or not np.isfinite(logits).all():
            raise ValueError("棋子 ONNX 模型输出必须是 15 个有限 logits")

        shifted = logits - float(np.max(logits))
        exp = np.exp(shifted)
        return exp / float(exp.sum())


def _minimum_grid_spacing(points: list[list[tuple[float, float]]]) -> float:
    spacings: list[float] = []
    for row in range(10):
        for col in range(8):
            spacings.append(
                float(
                    np.linalg.norm(
                        np.asarray(points[row][col + 1], dtype=np.float32)
                        - np.asarray(points[row][col], dtype=np.float32)
                    )
                )
            )
    for row in range(9):
        for col in range(9):
            spacings.append(
                float(
                    np.linalg.norm(
                        np.asarray(points[row + 1][col], dtype=np.float32)
                        - np.asarray(points[row][col], dtype=np.float32)
                    )
                )
            )
    if not spacings or min(spacings) <= 0:
        raise ValueError("棋盘网格间距无效")
    return min(spacings)


def _crop_intersections(
    image: np.ndarray,
    geometry: BoardGeometry,
    half_size_ratio: float,
) -> list[list[np.ndarray]]:
    height, width = image.shape[:2]
    if (width, height) != (geometry.source_width, geometry.source_height):
        geometry = geometry.scaled_to(width, height)

    points = geometry.grid_points()
    half_size = max(4, round(_minimum_grid_spacing(points) * half_size_ratio))
    crops: list[list[np.ndarray]] = []
    for row in range(10):
        crop_row: list[np.ndarray] = []
        for col in range(9):
            x, y = (round(value) for value in points[row][col])
            left, right = max(0, x - half_size), min(width, x + half_size + 1)
            top, bottom = max(0, y - half_size), min(height, y + half_size + 1)
            crop = image[top:bottom, left:right]
            if crop.size == 0:
                raise ValueError(f"棋位 row={row}, col={col} 的裁剪区域为空")
            crop_row.append(crop)
        crops.append(crop_row)
    return crops


def _normalize_probabilities(values: Sequence[float]) -> np.ndarray:
    probabilities = np.asarray(values, dtype=np.float64).reshape(-1)
    if probabilities.size != len(PIECE_SYMBOLS):
        raise ValueError(
            f"棋子分类器必须返回 {len(PIECE_SYMBOLS)} 个概率，当前为 {probabilities.size}"
        )
    if not np.isfinite(probabilities).all() or np.any(probabilities < 0):
        raise ValueError("棋子分类器概率必须是有限的非负数")
    total = float(probabilities.sum())
    if total <= 0:
        raise ValueError("棋子分类器概率总和必须大于 0")
    return probabilities / total


def recognize_board(
    image: np.ndarray,
    geometry: BoardGeometry,
    classifier: PieceClassifier,
    *,
    side_to_move: str,
    thresholds: RecognitionThresholds | None = None,
) -> BoardRecognition:
    """Recognize 90 intersections without promoting low-confidence guesses.

    The caller must provide the side to move from a trusted source (for example,
    a scene/OCR adapter or a retained move history). This function never guesses it.
    """
    if image is None or image.ndim not in {2, 3} or image.size == 0:
        raise ValueError("输入棋盘截图无效")
    if side_to_move not in {"w", "b"}:
        raise ValueError("side_to_move 必须为 'w'（红方）或 'b'（黑方）")
    thresholds = thresholds or RecognitionThresholds()
    thresholds.validate()

    crops = _crop_intersections(image, geometry, thresholds.half_size_ratio)
    rows: list[list[str]] = []
    predictions: list[PiecePrediction] = []
    uncertain: list[tuple[int, int]] = []

    for row in range(10):
        symbols: list[str] = []
        for col in range(9):
            probabilities = _normalize_probabilities(classifier.predict_proba(crops[row][col]))
            order = np.argsort(probabilities)
            best_index = int(order[-1])
            runner_up_index = int(order[-2])
            confidence = float(probabilities[best_index])
            runner_up_confidence = float(probabilities[runner_up_index])
            symbol = PIECE_SYMBOLS[best_index]
            symbols.append(symbol)
            predictions.append(
                PiecePrediction(
                    row=row,
                    col=col,
                    symbol=symbol,
                    confidence=confidence,
                    runner_up_symbol=PIECE_SYMBOLS[runner_up_index],
                    runner_up_confidence=runner_up_confidence,
                )
            )
            if (
                confidence < thresholds.min_confidence
                or confidence - runner_up_confidence < thresholds.min_margin
            ):
                uncertain.append((row, col))
        rows.append(symbols)

    mean_confidence = float(np.mean([item.confidence for item in predictions]))
    state = None
    if not uncertain:
        state = BoardState(rows=rows, side_to_move=side_to_move)

    return BoardRecognition(
        state=state,
        predictions=tuple(predictions),
        uncertain_squares=tuple(uncertain),
        mean_confidence=mean_confidence,
    )
