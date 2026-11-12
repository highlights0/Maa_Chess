"""MaaFramework Agent entry point."""

from __future__ import annotations

import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from maa.agent.agent_server import AgentServer
from maa.toolkit import Toolkit

import capture_action  # noqa: F401 - importing registers the custom action


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python agent/main.py <socket_id>")
        return 2

    Toolkit.init_option(str(PROJECT_ROOT))
    socket_id = sys.argv[-1]
    AgentServer.start_up(socket_id)
    try:
        AgentServer.join()
    finally:
        AgentServer.shut_down()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
