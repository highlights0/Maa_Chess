from __future__ import annotations

import math

import cv2
import numpy as np
import pytest

from maa_xiangqi.board.geometry import BoardGeometry
from maa_xiangqi.vision.nano_v3 import (
    MODEL_CLASS_SYMBOLS,
    preprocess_nano_v3,
    recognize_nano_v3_board,
)


class FakeNanoV3:
    def __init__(self, uncertain: tuple[int, int] | None = None):
        self.uncertain = uncertain

    def predict_probabilities(self, image, geometry, *, red_at_top=False):
        result = np.zeros((90, 16), dtype=np.float32)
        result[:, 0] = 0.99
        result[4, 9] = 0.99  # black king at e9 in image row 0
        result[85, 2] = 0.99  # red king at e0 in image row 9
        if self.uncertain is not None:
            index = self.uncertain[0] * 9 + self.uncertain[1]
            result[index] = 0
            result[index, 1] = 0.99
        return result


def make_geometry() -> BoardGeometry:
    return BoardGeometry(
        corners=((66, 327), (834, 327), (834, 1191), (66, 1191)),
        source_width=900,
        source_height=1600,
    )


def test_nano_v3_preprocessing_matches_fixed_model_tensor_contract():
    image = np.zeros((1600, 900, 3), dtype=np.uint8)
    image[:, :, 0] = np.arange(900, dtype=np.uint8)[None, :]
    image[:, :, 1] = np.arange(1600, dtype=np.uint8)[:, None]

    tensor = preprocess_nano_v3(image, make_geometry())

    assert tensor.shape == (1, 3, 315, 280)
    assert tensor.dtype == np.float32
    assert np.isfinite(tensor).all()


def test_nano_v3_red_at_top_rotates_board_into_canonical_fen_orientation():
    image = np.zeros((1600, 900, 3), dtype=np.uint8)
    image[:, :, 0] = np.arange(900, dtype=np.uint8)[None, :]
    image[:, :, 1] = np.arange(1600, dtype=np.uint8)[:, None]
    normal = preprocess_nano_v3(image, make_geometry())
    flipped = preprocess_nano_v3(image, make_geometry(), red_at_top=True)
    assert normal.shape == flipped.shape
    assert not np.array_equal(normal, flipped)


def test_nano_v3_prediction_maps_the_16_model_classes_to_board_state():
    result = recognize_nano_v3_board(
        np.zeros((1600, 900, 3), dtype=np.uint8),
        make_geometry(),
        FakeNanoV3(),
        side_to_move="w",
    )

    assert result.is_complete
    assert result.state is not None
    assert result.state.has_both_generals()
    assert result.state.rows[0][4] == "k"
    assert result.state.rows[9][4] == "K"
    assert len(result.predictions) == 90
    assert result.problem is None
    assert MODEL_CLASS_SYMBOLS[1] == "x"


def test_unknown_model_class_never_becomes_an_empty_square():
    result = recognize_nano_v3_board(
        np.zeros((1600, 900, 3), dtype=np.uint8),
        make_geometry(),
        FakeNanoV3(uncertain=(4, 4)),
        side_to_move="w",
    )
    assert result.state is None
    assert (4, 4) in result.uncertain_squares
    assert result.predictions[40].symbol == "x"


def test_low_confidence_empty_square_is_not_accepted():
    class LowConfidence(FakeNanoV3):
        def predict_probabilities(self, image, geometry, *, red_at_top=False):
            result = super().predict_probabilities(image, geometry, red_at_top=red_at_top)
            result[40] = 0
            result[40, 0] = 0.79
            result[40, 1] = 0.20
            return result

    result = recognize_nano_v3_board(
        np.zeros((1600, 900, 3), dtype=np.uint8),
        make_geometry(),
        LowConfidence(),
        side_to_move="w",
    )
    assert result.state is None
    assert (4, 4) in result.uncertain_squares


def test_model_rejects_a_piece_outside_its_legal_region():
    class IllegalKing(FakeNanoV3):
        def predict_probabilities(self, image, geometry, *, red_at_top=False):
            result = super().predict_probabilities(image, geometry, red_at_top=red_at_top)
            result[4] = 0
            result[4, 0] = 0.001
            result[4, 2] = 0.999  # red king incorrectly placed on black back rank
            result[85, 2] = 0.001
            result[85, 0] = 0.999
            return result

    result = recognize_nano_v3_board(
        np.zeros((1600, 900, 3), dtype=np.uint8),
        make_geometry(),
        IllegalKing(),
        side_to_move="w",
    )
    assert result.state is None
    assert (0, 4) in result.uncertain_squares
    assert result.problem == "局面必须且只能识别到一个红帅和一个黑将"


def test_nano_v3_rejects_perspective_geometry_it_cannot_reproduce():
    geometry = BoardGeometry(
        corners=((66, 327), (834, 345), (820, 1191), (66, 1191)),
        source_width=900,
        source_height=1600,
    )
    image = np.zeros((1600, 900, 3), dtype=np.uint8)
    with pytest.raises(ValueError, match="横竖对齐"):
        preprocess_nano_v3(image, geometry)


def _reference_preprocess(image: np.ndarray, geometry: BoardGeometry, *, red_at_top: bool) -> np.ndarray:
    """Scalar numerical oracle matching the upstream C# implementation."""
    rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    height, width = rgb.shape[:2]
    left = (geometry.corners[0][0] + geometry.corners[3][0]) / 2
    right = (geometry.corners[1][0] + geometry.corners[2][0]) / 2
    top = (geometry.corners[0][1] + geometry.corners[1][1]) / 2
    bottom = (geometry.corners[2][1] + geometry.corners[3][1]) / 2
    sx, sy = (right - left) / 350, (bottom - top) / 400
    origin_x, origin_y = left, top
    if red_at_top:
        origin_x, origin_y = right, bottom
        sx, sy = -sx, -sy

    def pixel(x: int, y: int, channel: int) -> float:
        if x < 0 or y < 0 or x >= width or y >= height:
            return 0.0
        return float(rgb[y, x, channel])

    crop = np.zeros((450, 400, 3), dtype=np.uint8)
    for y in range(450):
        py = round((origin_y + (y - 25) * sy) * 32) / 32
        iy, fy = math.floor(py), py - math.floor(py)
        for x in range(400):
            px = round((origin_x + (x - 25) * sx) * 32) / 32
            ix, fx = math.floor(px), px - math.floor(px)
            for channel in range(3):
                top_value = pixel(ix, iy, channel) * (1 - fx) + pixel(ix + 1, iy, channel) * fx
                bottom_value = pixel(ix, iy + 1, channel) * (1 - fx) + pixel(ix + 1, iy + 1, channel) * fx
                value = math.floor(top_value * (1 - fy) + bottom_value * fy + 0.5)
                crop[y, x, channel] = min(255, max(0, value))

    resized = np.zeros((315, 280, 3), dtype=np.uint8)
    for y in range(315):
        py = (y + 0.5) * 450 / 315 - 0.5
        iy, fy = int(py), py - int(py)
        for x in range(280):
            px = (x + 0.5) * 400 / 280 - 0.5
            ix, fx = int(px), px - int(px)
            for channel in range(3):
                top_value = crop[iy, ix, channel] * (1 - fx) + crop[iy, ix + 1, channel] * fx
                bottom_value = crop[iy + 1, ix, channel] * (1 - fx) + crop[iy + 1, ix + 1, channel] * fx
                value = math.floor(top_value * (1 - fy) + bottom_value * fy + 0.5)
                resized[y, x, channel] = min(255, max(0, value))

    mean = np.asarray([123.675, 116.28, 103.53], dtype=np.float64)
    std = np.asarray([58.395, 57.12, 57.375], dtype=np.float64)
    normalized = (resized.astype(np.float64) - mean) / std
    return np.transpose(normalized.astype(np.float32), (2, 0, 1))[None, ...]


@pytest.mark.parametrize("red_at_top", [False, True])
def test_preprocessing_is_bit_exact_against_upstream_scalar_reference(red_at_top: bool):
    image = np.random.default_rng(20261112).integers(
        0, 256, size=(1600, 900, 3), dtype=np.uint8
    )
    geometry = make_geometry()

    expected = _reference_preprocess(image, geometry, red_at_top=red_at_top)
    actual = preprocess_nano_v3(image, geometry, red_at_top=red_at_top)

    assert actual.shape == (1, 3, 315, 280)
    assert np.array_equal(actual, expected)


def test_real_nano_v3_model_loads_and_returns_90_square_probabilities():
    from pathlib import Path

    from maa_xiangqi.vision.nano_v3 import OnnxNanoV3BoardClassifier

    model_path = Path("models/xiangqi-nano-v3.onnx")
    if not model_path.is_file():
        pytest.skip("CI downloads the pinned ONNX model before running this smoke test")

    classifier = OnnxNanoV3BoardClassifier(model_path)
    image = np.full((1600, 900, 3), 225, dtype=np.uint8)
    probabilities = classifier.predict_probabilities(image, make_geometry())

    assert probabilities.shape == (90, 16)
    assert np.isfinite(probabilities).all()
    assert np.all(probabilities >= 0)
    assert np.all(probabilities <= 1.001)
