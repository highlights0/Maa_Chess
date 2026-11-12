from __future__ import annotations

import sys
from pathlib import Path

import pytest

from maa_xiangqi.board.fen import START_FEN
from maa_xiangqi.engine import EngineProtocolError, PikafishEngine

FAKE_ENGINE = Path(__file__).parent / "fixtures" / "fake_uci_engine.py"


def make_engine(mode: str = "normal") -> PikafishEngine:
    return PikafishEngine(
        sys.executable,
        args=[str(FAKE_ENGINE), mode],
        options={"Threads": 1, "Hash": 32},
        startup_timeout=2.0,
    )


def test_uci_handshake_and_bestmove_parsing():
    with make_engine() as engine:
        result = engine.analyze(START_FEN, movetime_ms=50)

    assert result.best_move == "b0c2"
    assert result.ponder == "b9c7"
    assert result.depth == 12
    assert result.score_cp == 34
    assert result.score_mate is None
    assert result.principal_variation == ("b0c2", "b9c7")
    assert result.elapsed_ms >= 0


def test_engine_can_report_no_legal_move():
    with make_engine("none") as engine:
        result = engine.analyze(START_FEN, movetime_ms=50)
    assert result.best_move is None


def test_rejects_invalid_history_move_before_starting_engine():
    engine = make_engine()
    with pytest.raises(ValueError, match="ICCS"):
        engine.analyze(START_FEN, moves=["j0a1"])
    engine.close()


def test_rejects_malformed_bestmove():
    with make_engine("bad") as engine:
        with pytest.raises(EngineProtocolError, match="非法 ICCS"):
            engine.analyze(START_FEN, movetime_ms=50)
