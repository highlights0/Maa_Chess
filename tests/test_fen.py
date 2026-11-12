import pytest

from maa_xiangqi.board.fen import START_FEN, board_to_fen, parse_fen


def test_start_fen_round_trip():
    state = parse_fen(START_FEN)
    assert state.side_to_move == "w"
    assert state.has_both_generals()
    assert board_to_fen(state) == START_FEN


def test_parse_minimal_fen_and_default_counters():
    state = parse_fen("4k4/9/9/9/9/9/9/9/9/4K4 b")
    assert state.side_to_move == "b"
    assert state.rule60_count == 0
    assert state.fullmove_number == 1
    assert state.to_fen() == "4k4/9/9/9/9/9/9/9/9/4K4 b - - 0 1"


@pytest.mark.parametrize(
    "fen",
    [
        "",
        "9/9 w",
        "10/9/9/9/9/9/9/9/9/9 w",
        "4k4/9/9/9/9/9/9/9/9/4K3 w",
        "4k4/9/9/9/9/9/9/9/9/4K4 x",
        "4k4/9/9/9/9/9/9/9/9/4K4 w - - nope 1",
    ],
)
def test_reject_invalid_fen(fen):
    with pytest.raises(ValueError):
        parse_fen(fen)
