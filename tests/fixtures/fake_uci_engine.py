"""Tiny fake UCI process used by the Pikafish adapter tests."""

from __future__ import annotations

import sys

mode = sys.argv[1] if len(sys.argv) > 1 else "normal"

for raw_line in sys.stdin:
    command = raw_line.strip()
    if command == "uci":
        print("id name MaaXiangqi Fake UCI", flush=True)
        print("option name Hash type spin default 16 min 1 max 1024", flush=True)
        print("uciok", flush=True)
    elif command == "isready":
        print("readyok", flush=True)
    elif command.startswith("go "):
        if mode == "none":
            print("bestmove (none)", flush=True)
        elif mode == "bad":
            print("bestmove j0a1", flush=True)
        else:
            print("info depth 12 score cp 34 nodes 100 pv b0c2 b9c7", flush=True)
            print("bestmove b0c2 ponder b9c7", flush=True)
    elif command == "quit":
        break
