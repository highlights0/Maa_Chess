import pytest

from maa_xiangqi.board.fen import START_FEN, board_to_fen, parse_fen
from maa_xiangqi.board.state import BoardState


def test_start_fen_round_trip():
    state = parse_fen(START_FEN)
    assert state.side_to_move == "w"
    assert state.has_both_generals()
    assert board_to_fen(state) == START_FEN


def test_unknown_history_cannot_be_serialized_as_full_fen():
    state = BoardState(rows=parse_fen(START_FEN).rows, side_to_move="w")

    assert state.rule60_count is None
    with pytest.raises(ValueError, match="未知 rule60"):
        board_to_fen(state)


def test_unknown_side_cannot_be_serialized_as_full_fen():
    state = BoardState(rows=parse_fen(START_FEN).rows, rule60_count=0, fullmove_number=1)

    assert state.side_to_move is None
    with pytest.raises(ValueError, match="未知行棋方"):
        board_to_fen(state)


def test_parse_minimal_fen_preserves_unknown_counters():
    state = parse_fen("4k4/9/9/9/9/9/9/9/9/4K4 b")
    assert state.side_to_move == "b"
    assert state.rule60_count is None
    assert state.fullmove_number is None
    with pytest.raises(ValueError, match="未知 rule60"):
        state.to_fen()


@pytest.mark.parametrize(
    "fen",
    [
        "",
        "9/9 w",
        "10/9/9/9/9/9/9/9/9/9 w",
        "4k4/9/9/9/9/9/9/9/9/4K3 w",
        "4k4/9/9/9/9/9/9/9/9/4K4 x",
        "4k4/9/9/9/9/9/9/9/9/4K4 w - - nope 1",
        "4k4/9/9/9/9/9/9/9/9/4K4 w - - 0",
    ],
)
def test_reject_invalid_fen(fen):
    with pytest.raises(ValueError):
        parse_fen(fen)
