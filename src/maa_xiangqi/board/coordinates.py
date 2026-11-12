"""Conversions between image-grid coordinates and Pikafish ICCS coordinates."""

from __future__ import annotations

FILES = "abcdefghi"


def square_to_uci(col: int, row: int) -> str:
    """Convert image row/column (top-left origin) to Xiangqi ICCS (Red origin)."""
    if not 0 <= col < 9:
        raise ValueError(f"列坐标超出范围：{col}")
    if not 0 <= row < 10:
        raise ValueError(f"行坐标超出范围：{row}")
    return f"{FILES[col]}{9 - row}"


def uci_to_square(square: str) -> tuple[int, int]:
    """Convert a two-character ICCS square to (col, image_row)."""
    if len(square) != 2 or square[0] not in FILES or square[1] not in "0123456789":
        raise ValueError(f"非法 ICCS 坐标：{square!r}")
    return FILES.index(square[0]), 9 - int(square[1])
