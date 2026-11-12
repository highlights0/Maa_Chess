"""Small ADB adapter used for device diagnostics and Android-internal screenshots."""

from __future__ import annotations

import re
import subprocess
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path


class AdbError(RuntimeError):
    """Raised when ADB is unavailable or a device command fails."""


@dataclass(frozen=True)
class AdbDevice:
    serial: str
    state: str
    details: dict[str, str]


@dataclass(frozen=True)
class DeviceInfo:
    serial: str
    model: str | None
    android_release: str | None
    width: int | None
    height: int | None
    density_dpi: int | None


def _run_adb(
    args: Sequence[str],
    adb_path: str | Path = "adb",
    timeout: float = 20,
) -> bytes:
    command = [str(adb_path), *[str(item) for item in args]]
    try:
        completed = subprocess.run(
            command,
            capture_output=True,
            timeout=timeout,
            check=False,
        )
    except FileNotFoundError as exc:
        raise AdbError(
            f"找不到 ADB：{adb_path}。请安装 Android Platform Tools，"
            "或通过 --adb 指定 adb.exe 路径。"
        ) from exc
    except subprocess.TimeoutExpired as exc:
        raise AdbError(f"ADB 命令超时：{' '.join(command)}") from exc

    if completed.returncode != 0:
        stderr = completed.stderr.decode("utf-8", errors="replace").strip()
        raise AdbError(
            f"ADB 命令失败（exit={completed.returncode}）：{' '.join(command)}\n{stderr}"
        )
    return completed.stdout


def parse_devices_output(output: str) -> list[AdbDevice]:
    """Parse adb devices -l; non-device states are retained for diagnostics."""
    devices: list[AdbDevice] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        details: dict[str, str] = {}
        for token in parts[2:]:
            if ":" in token:
                key, value = token.split(":", 1)
                details[key] = value
        devices.append(AdbDevice(serial=parts[0], state=parts[1], details=details))
    return devices


def list_devices(adb_path: str | Path = "adb") -> list[AdbDevice]:
    output = _run_adb(["devices", "-l"], adb_path=adb_path).decode(
        "utf-8", errors="replace"
    )
    return parse_devices_output(output)


def _require_device(serial: str, adb_path: str | Path) -> None:
    devices = list_devices(adb_path)
    match = next((device for device in devices if device.serial == serial), None)
    if match is None:
        raise AdbError(f"ADB 设备列表中不存在序列号：{serial}")
    if match.state != "device":
        raise AdbError(
            f"设备 {serial} 当前状态为 {match.state!r}，请先处理授权或离线问题。"
        )


def _last_size(text: str) -> tuple[int, int] | None:
    matches = re.findall(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", text)
    if not matches:
        return None
    return int(matches[-1][0]), int(matches[-1][1])


def _last_density(text: str) -> int | None:
    matches = re.findall(r"(?:Physical|Override) density:\s*(\d+)", text)
    return int(matches[-1]) if matches else None


def get_device_info(
    serial: str,
    adb_path: str | Path = "adb",
) -> DeviceInfo:
    _require_device(serial, adb_path)
    size_text = _run_adb(["-s", serial, "shell", "wm", "size"], adb_path).decode(
        "utf-8", errors="replace"
    )
    density_text = _run_adb(
        ["-s", serial, "shell", "wm", "density"], adb_path
    ).decode("utf-8", errors="replace")
    model = _run_adb(
        ["-s", serial, "shell", "getprop", "ro.product.model"], adb_path
    ).decode("utf-8", errors="replace").strip()
    release = _run_adb(
        ["-s", serial, "shell", "getprop", "ro.build.version.release"], adb_path
    ).decode("utf-8", errors="replace").strip()
    size = _last_size(size_text)
    return DeviceInfo(
        serial=serial,
        model=model or None,
        android_release=release or None,
        width=size[0] if size else None,
        height=size[1] if size else None,
        density_dpi=_last_density(density_text),
    )


def capture_screenshot(
    serial: str,
    output_path: str | Path,
    adb_path: str | Path = "adb",
) -> Path:
    """Capture the Android framebuffer, excluding the emulator desktop frame."""
    _require_device(serial, adb_path)
    png = _run_adb(
        ["-s", serial, "exec-out", "screencap", "-p"],
        adb_path=adb_path,
        timeout=30,
    )
    if not png.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AdbError(
            "ADB 截图结果不是有效 PNG。请确认设备已解锁且 screencap 命令可用。"
        )
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(png)
    return destination


def launch_app(
    serial: str,
    package_name: str,
    adb_path: str | Path = "adb",
) -> str:
    if not package_name.strip():
        raise ValueError("package_name 不能为空")
    _require_device(serial, adb_path)
    output = _run_adb(
        ["-s", serial, "shell", "monkey", "-p", package_name, "1"],
        adb_path=adb_path,
    )
    return output.decode("utf-8", errors="replace").strip()
