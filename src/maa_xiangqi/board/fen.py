"""Xiangqi FEN helpers compatible with Pikafish's position command."""

from __future__ import annotations

from .state import EMPTY, PIECE_SYMBOLS, BoardState

START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"


def _encode_rank(row: tuple[str, ...] | list[str]) -> str:
    result: list[str] = []
    empty_count = 0
    for piece in row:
        if piece == EMPTY:
            empty_count += 1
            continue
        if empty_count:
            result.append(str(empty_count))
            empty_count = 0
        result.append(piece)
    if empty_count:
        result.append(str(empty_count))
    return "".join(result)


def board_to_fen(state: BoardState) -> str:
    """Serialize a board to six-field Xiangqi FEN.

    Pikafish uses the fifth field as its rule60 counter. A screenshot cannot
    supply the counters; callers must retain them from known move history.
    """
    if state.side_to_move is None:
        raise ValueError("未知行棋方，不能生成完整 FEN")
    if state.rule60_count is None or state.fullmove_number is None:
        raise ValueError("未知 rule60 或回合数，不能生成完整 FEN")
    placement = "/".join(_encode_rank(row) for row in state.rows)
    return (
        f"{placement} {state.side_to_move} - - "
        f"{state.rule60_count} {state.fullmove_number}"
    )


def parse_fen(fen: str) -> BoardState:
    fields = fen.strip().split()
    if len(fields) not in {2, 6}:
        raise ValueError("FEN 必须是仅含布局和行棋方的两字段，或完整的六字段")
    if fields[1] not in {"w", "b"}:
        raise ValueError("FEN 行棋方字段必须为 w 或 b")

    ranks = fields[0].split("/")
    if len(ranks) != 10:
        raise ValueError(f"Xiangqi FEN 必须包含 10 行，当前为 {len(ranks)} 行")

    rows: list[list[str]] = []
    for rank_index, encoded_rank in enumerate(ranks):
        row: list[str] = []
        for char in encoded_rank:
            if char in "123456789":
                row.extend([EMPTY] * int(char))
            elif char in PIECE_SYMBOLS:
                row.append(char)
            else:
                raise ValueError(
                    f"FEN 第 {rank_index} 行包含非法字符：{char!r}"
                )
        if len(row) != 9:
            raise ValueError(
                f"FEN 第 {rank_index} 行解码后应为 9 格，当前为 {len(row)} 格"
            )
        rows.append(row)

    rule60_count = None
    fullmove_number = None
    if len(fields) == 6:
        try:
            rule60_count = int(fields[4])
        except ValueError as exc:
            raise ValueError("FEN 第五字段 rule60 必须是整数") from exc
        try:
            fullmove_number = int(fields[5])
        except ValueError as exc:
            raise ValueError("FEN 第六字段 fullmove 必须是整数") from exc

    return BoardState(
        rows=rows,
        side_to_move=fields[1],
        rule60_count=rule60_count,
        fullmove_number=fullmove_number,
    )
