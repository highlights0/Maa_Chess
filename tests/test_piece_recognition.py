from __future__ import annotations

import numpy as np
import pytest

from maa_xiangqi.board.geometry import BoardGeometry
from maa_xiangqi.vision.pieces import PIECE_SYMBOLS, recognize_board


class PixelLabelClassifier:
    def predict_proba(self, image: np.ndarray) -> np.ndarray:
        index = round(float(image[image.shape[0] // 2, image.shape[1] // 2, 0]))
        probabilities = np.full(len(PIECE_SYMBOLS), 0.001, dtype=np.float32)
        probabilities[index] = 0.986
        return probabilities


class AmbiguousClassifier(PixelLabelClassifier):
    def predict_proba(self, image: np.ndarray) -> np.ndarray:
        index = round(float(image[image.shape[0] // 2, image.shape[1] // 2, 0]))
        probabilities = np.full(len(PIECE_SYMBOLS), 0.001, dtype=np.float32)
        if index == 1:
            probabilities[1] = 0.48
            probabilities[2] = 0.47
        else:
            probabilities[index] = 0.986
        return probabilities


def make_board_image() -> tuple[np.ndarray, BoardGeometry]:
    image = np.full((551, 501, 3), 255, dtype=np.uint8)
    geometry = BoardGeometry(
        corners=((50, 50), (450, 50), (450, 500), (50, 500)),
        source_width=501,
        source_height=551,
    )
    labels = [[0 for _ in range(9)] for _ in range(10)]
    labels[0][4] = 8  # black king
    labels[9][4] = 1  # red king

    for row in range(10):
        for col in range(9):
            x = 50 + col * 50
            y = 50 + row * 50
            image[y - 12 : y + 13, x - 12 : x + 13] = labels[row][col]
    return image, geometry


def test_recognize_board_emits_state_when_all_squares_are_confident():
    image, geometry = make_board_image()

    result = recognize_board(
        image,
        geometry,
        PixelLabelClassifier(),
        side_to_move="w",
    )

    assert result.is_complete
    assert result.state is not None
    assert result.state.has_both_generals()
    assert result.state.rows[0][4] == "k"
    assert result.state.rows[9][4] == "K"
    assert len(result.predictions) == 90
    assert result.uncertain_squares == ()
    assert result.mean_confidence > 0.95


def test_uncertain_square_prevents_fen_ready_board_state():
    image, geometry = make_board_image()

    result = recognize_board(
        image,
        geometry,
        AmbiguousClassifier(),
        side_to_move="w",
    )

    assert result.state is None
    assert (9, 4) in result.uncertain_squares


def test_invalid_classifier_output_is_rejected():
    image, geometry = make_board_image()

    class InvalidClassifier:
        def predict_proba(self, crop: np.ndarray) -> list[float]:
            return [1.0] * 14

    with pytest.raises(ValueError, match="15 个概率"):
        recognize_board(image, geometry, InvalidClassifier(), side_to_move="w")


def test_invalid_side_to_move_is_rejected_before_recognition():
    image, geometry = make_board_image()

    with pytest.raises(ValueError, match="side_to_move"):
        recognize_board(image, geometry, PixelLabelClassifier(), side_to_move="?")
