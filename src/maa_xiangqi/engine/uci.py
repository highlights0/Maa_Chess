"""Small synchronous UCI client for a local Pikafish process."""

from __future__ import annotations

import queue
import re
import shutil
import subprocess
import threading
import time
from collections.abc import Mapping, Sequence
from dataclasses import asdict, dataclass
from pathlib import Path

from maa_xiangqi.board.fen import board_to_fen, parse_fen

_MOVE_RE = re.compile(r"^[a-i][0-9][a-i][0-9]$")


class EngineError(RuntimeError):
    """Base class for UCI engine failures."""


class EngineTimeoutError(EngineError):
    """The engine did not answer before the deadline."""


class EngineProtocolError(EngineError):
    """The engine returned a malformed response."""


@dataclass(frozen=True)
class EngineResult:
    best_move: str | None
    ponder: str | None
    depth: int | None
    score_cp: int | None
    score_mate: int | None
    principal_variation: tuple[str, ...]
    elapsed_ms: int
    info_lines: tuple[str, ...]

    def to_dict(self) -> dict:
        return asdict(self)


def _parse_info(line: str) -> tuple[int | None, int | None, int | None, tuple[str, ...]]:
    tokens = line.split()
    depth = score_cp = score_mate = None
    pv: tuple[str, ...] = ()
    for index, token in enumerate(tokens):
        if token == "depth" and index + 1 < len(tokens):
            try:
                depth = int(tokens[index + 1])
            except ValueError:
                pass
        elif token == "score" and index + 2 < len(tokens):
            try:
                value = int(tokens[index + 2])
            except ValueError:
                continue
            if tokens[index + 1] == "cp":
                score_cp, score_mate = value, None
            elif tokens[index + 1] == "mate":
                score_cp, score_mate = None, value
        elif token == "pv":
            pv = tuple(tokens[index + 1 :])
            break
    return depth, score_cp, score_mate, pv


class PikafishEngine:
    """Own one UCI process; calls are serialized and bounded by timeouts."""

    def __init__(
        self,
        engine_path: str | Path,
        *,
        args: Sequence[str] = (),
        cwd: str | Path | None = None,
        options: Mapping[str, str | int] | None = None,
        startup_timeout: float = 15.0,
        command_timeout: float = 15.0,
        stop_timeout: float = 1.0,
    ) -> None:
        candidate = Path(engine_path).expanduser()
        located = candidate if candidate.is_file() else Path(shutil.which(str(engine_path)) or "")
        if not located.is_file():
            raise FileNotFoundError(f"找不到 Pikafish 可执行文件：{engine_path}")
        self.engine_path = located.resolve()
        self.args = tuple(str(value) for value in args)
        self.cwd = Path(cwd).expanduser().resolve() if cwd else self.engine_path.parent
        self.options = dict(options or {})
        self.startup_timeout = startup_timeout
        self.command_timeout = command_timeout
        self.stop_timeout = stop_timeout
        self._process: subprocess.Popen[str] | None = None
        self._lines: queue.Queue[str | None] = queue.Queue()
        self._reader_thread: threading.Thread | None = None
        self._lock = threading.RLock()

    def _reader(self) -> None:
        process = self._process
        if process is None or process.stdout is None:
            self._lines.put(None)
            return
        try:
            for line in process.stdout:
                self._lines.put(line.rstrip("\r\n"))
        finally:
            self._lines.put(None)

    def _send(self, command: str) -> None:
        process = self._process
        if process is None or process.poll() is not None or process.stdin is None:
            raise EngineError("Pikafish 进程未运行")
        try:
            process.stdin.write(command + "\n")
            process.stdin.flush()
        except OSError as exc:
            raise EngineError(f"向 Pikafish 发送命令失败：{command}") from exc

    def _wait_for(self, predicate, timeout: float, command: str) -> tuple[list[str], str]:
        deadline = time.monotonic() + timeout
        lines: list[str] = []
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise EngineTimeoutError(f"等待 Pikafish 响应超时：{command}")
            try:
                line = self._lines.get(timeout=remaining)
            except queue.Empty as exc:
                raise EngineTimeoutError(f"等待 Pikafish 响应超时：{command}") from exc
            if line is None:
                raise EngineError("Pikafish 进程提前退出")
            if predicate(line):
                return lines, line
            lines.append(line)

    def start(self) -> PikafishEngine:
        with self._lock:
            if self._process is not None and self._process.poll() is None:
                return self
            self._lines = queue.Queue()
            try:
                self._process = subprocess.Popen(
                    [str(self.engine_path), *self.args],
                    cwd=self.cwd,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    bufsize=1,
                )
            except OSError as exc:
                raise EngineError(f"启动 Pikafish 失败：{self.engine_path}") from exc
            self._reader_thread = threading.Thread(
                target=self._reader,
                name="pikafish-stdout",
                daemon=True,
            )
            self._reader_thread.start()
            try:
                self._send("uci")
                self._wait_for(lambda line: line == "uciok", self.startup_timeout, "uci")
                for name, value in self.options.items():
                    if any(char in name + str(value) for char in "\r\n"):
                        raise ValueError("UCI option 名称和值不能包含换行符")
                    self._send(f"setoption name {name} value {value}")
                self._send("isready")
                self._wait_for(lambda line: line == "readyok", self.startup_timeout, "isready")
            except Exception:
                self.close()
                raise
            return self

    def analyze(
        self,
        fen: str,
        *,
        moves: Sequence[str] = (),
        movetime_ms: int = 1000,
        timeout: float | None = None,
    ) -> EngineResult:
        if movetime_ms <= 0:
            raise ValueError("movetime_ms 必须大于 0")
        state = parse_fen(fen)
        normalized_fen = board_to_fen(state)
        history = tuple(moves)
        for move in history:
            if not _MOVE_RE.fullmatch(move):
                raise ValueError(f"非法 ICCS 走法：{move!r}")
        deadline_seconds = (
            timeout
            if timeout is not None
            else max(self.command_timeout, movetime_ms / 1000 + 5.0)
        )
        if deadline_seconds <= 0:
            raise ValueError("timeout 必须大于 0")

        self.start()
        with self._lock:
            position = f"position fen {normalized_fen}"
            if history:
                position += " moves " + " ".join(history)
            self._send(position)
            started = time.monotonic()
            self._send(f"go movetime {movetime_ms}")
            try:
                lines, best_line = self._wait_for(
                    lambda line: line.startswith("bestmove "),
                    deadline_seconds,
                    "go",
                )
            except EngineTimeoutError:
                try:
                    self._send("stop")
                    self._wait_for(
                        lambda line: line.startswith("bestmove "),
                        self.stop_timeout,
                        "stop",
                    )
                except EngineError:
                    self.close()
                raise

            tokens = best_line.split()
            if len(tokens) < 2:
                raise EngineProtocolError(f"非法 bestmove 响应：{best_line!r}")
            best_move = tokens[1]
            if best_move in {"(none)", "0000"}:
                best_move = None
            elif not _MOVE_RE.fullmatch(best_move):
                raise EngineProtocolError(f"Pikafish 返回非法 ICCS 走法：{best_move!r}")

            ponder = None
            if len(tokens) >= 4 and tokens[2] == "ponder":
                ponder = tokens[3]
                if not _MOVE_RE.fullmatch(ponder):
                    raise EngineProtocolError(f"Pikafish 返回非法 ponder 走法：{ponder!r}")

            info_lines = tuple(line for line in lines if line.startswith("info "))
            depth = score_cp = score_mate = None
            pv: tuple[str, ...] = ()
            for line in reversed(info_lines):
                parsed = _parse_info(line)
                if any(value is not None for value in parsed[:3]) or parsed[3]:
                    depth, score_cp, score_mate, pv = parsed
                    break
            return EngineResult(
                best_move,
                ponder,
                depth,
                score_cp,
                score_mate,
                pv,
                round((time.monotonic() - started) * 1000),
                info_lines,
            )

    def close(self) -> None:
        with self._lock:
            process = self._process
            if process is None:
                return
            if process.poll() is None:
                try:
                    self._send("quit")
                    process.wait(timeout=2.0)
                except (EngineError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=2.0)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=2.0)
            if process.stdin is not None:
                process.stdin.close()
            if self._reader_thread is not None:
                self._reader_thread.join(timeout=1.0)
            if process.stdout is not None:
                process.stdout.close()
            self._process = None

    def __enter__(self) -> PikafishEngine:  # noqa: PYI034
        return self.start()

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()
