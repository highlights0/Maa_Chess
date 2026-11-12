"""Canonical Xiangqi board state.

Rows are stored in image/FEN order: row 0 is Black's back rank; row 9 is Red's.
Uppercase pieces belong to Red, lowercase pieces belong to Black, and '.' is empty.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

PIECE_SYMBOLS = frozenset("KAB CNRPkabcnrp".replace(" ", ""))
EMPTY = "."


@dataclass
class BoardState:
    rows: Sequence[Sequence[str]]
    side_to_move: str = "w"
    rule60_count: int = 0
    fullmove_number: int = 1
    move_history: Sequence[str] = ()

    def __post_init__(self) -> None:
        self.rows = tuple(tuple(row) for row in self.rows)
        self.move_history = tuple(self.move_history)
        self.validate()

    def validate(self) -> None:
        if len(self.rows) != 10:
            raise ValueError(f"棋盘必须有 10 行，当前为 {len(self.rows)} 行")
        for row_index, row in enumerate(self.rows):
            if len(row) != 9:
                raise ValueError(
                    f"第 {row_index} 行必须有 9 列，当前为 {len(row)} 列"
                )
            for col_index, piece in enumerate(row):
                if piece != EMPTY and piece not in PIECE_SYMBOLS:
                    raise ValueError(
                        f"非法棋子符号 {piece!r}，位置 row={row_index}, col={col_index}"
                    )
        if self.side_to_move not in {"w", "b"}:
            raise ValueError("side_to_move 必须为 'w'（红方）或 'b'（黑方）")
        if self.rule60_count < 0:
            raise ValueError("rule60_count 不能为负数")
        if self.fullmove_number < 1:
            raise ValueError("fullmove_number 必须大于等于 1")

    def count(self, symbol: str) -> int:
        return sum(piece == symbol for row in self.rows for piece in row)

    def has_both_generals(self) -> bool:
        return self.count("K") == 1 and self.count("k") == 1

    def to_fen(self) -> str:
        from .fen import board_to_fen

        return board_to_fen(self)
