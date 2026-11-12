"""Lightweight scene classification from OCR text.

This module consumes OCR text only; it does not perform OCR or infer a board
position. It is a deterministic seam for the future Android / MaaFramework
screen-recognition pipeline.
"""

from __future__ import annotations

import re
from collections.abc import Iterable
from dataclasses import dataclass
from enum import Enum

_LEVEL_RE = re.compile(r"第\s*(\d+|[一二三四五六七八九十百]+)\s*关")
_PUZZLE_MARKERS = ("残局", "闯关")
_PUZZLE_BOARD_ACTIONS = ("悔棋", "提示", "分享")
_STANDARD_MARKERS = ("匹配", "排位", "对局", "认输", "求和", "观战", "红方", "黑方")
_HOME_MARKERS = ("开始游戏", "进入游戏", "快速开始")


class SceneMode(str, Enum):
    STANDARD = "standard"
    PUZZLE = "puzzle"
    UNKNOWN = "unknown"


class ScenePhase(str, Enum):
    GAME_HOME = "game_home"
    STANDARD_MATCH = "standard_match"
    PUZZLE_LIST = "puzzle_list"
    PUZZLE_BOARD = "puzzle_board"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class SceneDetection:
    mode: SceneMode
    phase: ScenePhase
    confidence: float
    evidence: tuple[str, ...]
    puzzle_level: int | None = None
    puzzle_title: str | None = None


def _parse_level(value: str) -> int | None:
    if value.isdigit():
        return int(value)
    digits = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
              "六": 6, "七": 7, "八": 8, "九": 9}
    if value == "十":
        return 10
    if value.startswith("十"):
        return 10 + digits.get(value[1:], 0)
    if value.endswith("十"):
        return digits.get(value[0], 0) * 10
    if "十" in value:
        left, right = value.split("十", maxsplit=1)
        return digits.get(left, 1) * 10 + digits.get(right, 0)
    return digits.get(value)


def classify_scene(ocr_texts: Iterable[str]) -> SceneDetection:
    """Classify a screen from recognized labels without guessing from geometry."""
    texts = tuple(" ".join(str(item).split()) for item in ocr_texts if str(item).strip())
    compact = re.sub(r"\s+", "", " ".join(texts))
    level_match = _LEVEL_RE.search(compact)
    level = _parse_level(level_match.group(1)) if level_match else None

    puzzle_evidence = tuple(
        marker for marker in (*_PUZZLE_MARKERS, *(_PUZZLE_BOARD_ACTIONS if level else ()))
        if marker in compact
    )
    if level is not None or any(marker in compact for marker in _PUZZLE_MARKERS):
        title = None
        for text in texts:
            match = _LEVEL_RE.search(text)
            if match:
                remainder = text[match.end() :].strip()
                if remainder:
                    title = remainder
                    break
        phase = (
            ScenePhase.PUZZLE_BOARD
            if level is not None or any(marker in compact for marker in _PUZZLE_BOARD_ACTIONS)
            else ScenePhase.PUZZLE_LIST
        )
        return SceneDetection(
            mode=SceneMode.PUZZLE,
            phase=phase,
            confidence=0.98 if level is not None else 0.9,
            evidence=puzzle_evidence or ("关卡编号",),
            puzzle_level=level,
            puzzle_title=title,
        )

    standard_evidence = tuple(marker for marker in _STANDARD_MARKERS if marker in compact)
    if standard_evidence:
        in_match = any(marker in compact for marker in ("认输", "求和", "观战", "红方", "黑方"))
        return SceneDetection(
            mode=SceneMode.STANDARD,
            phase=ScenePhase.STANDARD_MATCH if in_match else ScenePhase.GAME_HOME,
            confidence=0.82 if in_match else 0.72,
            evidence=standard_evidence,
        )

    home_evidence = tuple(marker for marker in _HOME_MARKERS if marker in compact)
    if home_evidence:
        return SceneDetection(
            mode=SceneMode.UNKNOWN,
            phase=ScenePhase.GAME_HOME,
            confidence=0.55,
            evidence=home_evidence,
        )

    return SceneDetection(
        mode=SceneMode.UNKNOWN,
        phase=ScenePhase.UNKNOWN,
        confidence=0.0,
        evidence=(),
    )
