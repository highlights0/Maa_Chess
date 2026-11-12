from __future__ import annotations

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
