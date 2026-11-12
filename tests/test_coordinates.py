import pytest

from maa_xiangqi.board.coordinates import square_to_uci, uci_to_square


def test_image_grid_to_iccs_and_back():
    assert square_to_uci(0, 9) == "a0"
    assert square_to_uci(8, 0) == "i9"
    assert uci_to_square("a0") == (0, 9)
    assert uci_to_square("i9") == (8, 0)
    assert uci_to_square("e4") == (4, 5)


@pytest.mark.parametrize("col,row", [(-1, 0), (9, 0), (0, -1), (0, 10)])
def test_reject_out_of_range_grid_coordinates(col, row):
    with pytest.raises(ValueError):
        square_to_uci(col, row)


def test_reject_invalid_iccs_square():
    with pytest.raises(ValueError):
        uci_to_square("j0")
