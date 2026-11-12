"""Read-only Android / MuMu diagnostics used before the background-display PoC."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from pathlib import Path

from maa_xiangqi.adb import AdbError, _run_adb, get_device_info


@dataclass(frozen=True)
class EmulatorDiagnostics:
    """Sanitized report; ADB serials and raw full dumps are intentionally omitted."""

    emulator_vendor: str | None
    emulator_detected: bool
    emulator_markers: tuple[str, ...]
    manufacturer: str | None
    brand: str | None
    model: str | None
    product: str | None
    device_codename: str | None
    hardware: str | None
    android_release: str | None
    android_api: int | None
    cpu_abi: str | None
    abi_list: tuple[str, ...]
    screen_width: int | None
    screen_height: int | None
    density_dpi: int | None
    shell_identity: str
    shell_uid: int | None
    su_binary_path: str | None
    root_probe_requested: bool
    root_probe_output: str | None
    root_available: bool | None
    shizuku_installed: bool
    shizuku_process_detected: bool
    shizuku_authorization: str
    game_packages: tuple[str, ...]
    game_package_versions: tuple[str, ...]
    display_summary: tuple[str, ...]
    virtual_display_status: str

    def to_dict(self) -> dict:
        return asdict(self)


def parse_getprop_output(output: str) -> dict[str, str]:
    properties: dict[str, str] = {}
    for line in output.splitlines():
        match = re.match(r"^\[([^\]]+)\]:\s*\[(.*)\]$", line.strip())
        if match:
            properties[match.group(1)] = match.group(2)
    return properties


def parse_package_list(output: str) -> set[str]:
    packages: set[str] = set()
    for line in output.splitlines():
        value = line.strip()
        if value.startswith("package:"):
            package = value.removeprefix("package:").strip()
            if package:
                packages.add(package)
    return packages


def detect_emulator_vendor(
    properties: dict[str, str],
    display_output: str = "",
) -> tuple[str | None, tuple[str, ...]]:
    """Infer emulator family from build properties and display-device metadata."""
    keys = (
        "ro.product.manufacturer",
        "ro.product.brand",
        "ro.product.model",
        "ro.product.name",
        "ro.product.device",
        "ro.hardware",
        "ro.build.fingerprint",
    )
    searchable = " ".join(properties.get(key, "") for key in keys)
    searchable = f"{searchable} {display_output}".lower()
    signatures = {
        "MuMu": ("mumuscreen", "mumu", "netease", "nemu"),
        "雷电": ("leidian", "ldplayer", "dnplayer"),
        "蓝叠": ("bluestacks", "bst"),
        "夜神": ("nox", "noxplayer"),
        "Genymotion": ("genymotion",),
        "Android Emulator": ("sdk_gphone", "goldfish", "ranchu", "vbox"),
    }
    for vendor, markers in signatures.items():
        found = tuple(marker for marker in markers if marker in searchable)
        if found:
            return vendor, found

    qemu_values = (
        properties.get("ro.kernel.qemu", ""),
        properties.get("ro.boot.qemu", ""),
    )
    if any(value == "1" for value in qemu_values):
        return "Android Emulator (unidentified)", ("qemu",)
    return None, ()


def extract_display_summary(output: str, max_lines: int = 40) -> tuple[str, ...]:
    terms = (
        "DisplayDeviceInfo",
        "mDisplayId",
        "displayId",
        "VirtualDisplay",
        "DisplayInfo",
        "mState",
        "mOwnerPackageName",
        "Display ",
    )
    selected: list[str] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if line and any(term.lower() in line.lower() for term in terms):
            selected.append(line[:500])
            if len(selected) >= max_lines:
                break
    return tuple(selected)


def _decode(output: bytes) -> str:
    return output.decode("utf-8", errors="replace").strip()


def _package_version(serial: str, package: str, adb_path: str | Path) -> str | None:
    try:
        output = _decode(
            _run_adb(
                ["-s", serial, "shell", "dumpsys", "package", package],
                adb_path,
                timeout=15,
            )
        )
    except AdbError:
        return None
    match = re.search(r"\bversionName=([^\s]+)", output)
    return match.group(1) if match else None


def _classify_root_probe(output: str) -> bool | None:
    """Return True/False only for conclusive evidence; timeout remains unknown."""
    if re.search(r"\buid=0\b", output):
        return True
    uid_match = re.search(r"\buid=(\d+)\b", output)
    if uid_match:
        return int(uid_match.group(1)) == 0

    lowered = output.lower()
    if re.search(
        r"permission denied|not found|inaccessible|not permitted|"
        r"operation not permitted|access denied",
        lowered,
    ):
        return False
    if re.search(r"timeout|timed out|超时|temporarily unavailable", lowered):
        return None
    return None


def diagnose_device(
    serial: str,
    adb_path: str | Path = "adb",
    probe_root: bool = False,
) -> EmulatorDiagnostics:
    """Collect device information. Root probing is opt-in and may show an auth prompt."""
    info = get_device_info(serial, adb_path)
    properties = parse_getprop_output(
        _decode(
            _run_adb(
                ["-s", serial, "shell", "getprop"],
                adb_path,
                timeout=15,
            )
        )
    )
    packages = parse_package_list(
        _decode(
            _run_adb(
                ["-s", serial, "shell", "pm", "list", "packages"],
                adb_path,
            )
        )
    )

    try:
        process_output = _decode(
            _run_adb(
                ["-s", serial, "shell", "sh", "-c", "ps -A 2>/dev/null || ps"],
                adb_path,
                timeout=15,
            )
        )
    except AdbError:
        process_output = ""

    shell_identity = _decode(
        _run_adb(["-s", serial, "shell", "id"], adb_path)
    )
    uid_match = re.search(r"\buid=(\d+)", shell_identity)
    shell_uid = int(uid_match.group(1)) if uid_match else None

    su_path = _decode(
        _run_adb(
            [
                "-s",
                serial,
                "shell",
                "sh",
                "-c",
                "command -v su 2>/dev/null || true",
            ],
            adb_path,
        )
    )
    root_probe_output: str | None = None
    root_available: bool | None = True if shell_uid == 0 else None
    if probe_root:
        try:
            root_probe_output = _decode(
                _run_adb(
                    ["-s", serial, "shell", "sh", "-c", "su -c id"],
                    adb_path,
                    timeout=10,
                )
            )
            root_available = _classify_root_probe(root_probe_output)
        except AdbError as exc:
            root_probe_output = str(exc).splitlines()[0]
            root_available = _classify_root_probe(root_probe_output)

    try:
        display_output = _decode(
            _run_adb(
                ["-s", serial, "shell", "dumpsys", "display"],
                adb_path,
                timeout=20,
            )
        )
    except AdbError as exc:
        display_output = f"display diagnostics unavailable: {exc}"

    vendor, vendor_markers = detect_emulator_vendor(properties, display_output)
    emulator_markers = list(vendor_markers)
    for key in ("ro.kernel.qemu", "ro.boot.qemu"):
        if properties.get(key) == "1":
            emulator_markers.append(f"{key}=1")

    shizuku_package = "moe.shizuku.privileged.api"
    shizuku_installed = shizuku_package in packages
    shizuku_process_detected = any(
        "shizuku" in line.lower() for line in process_output.splitlines()
    )

    game_packages = tuple(
        sorted(
            package
            for package in packages
            if any(
                marker in package.lower()
                for marker in ("tencent", "qqgame", "xiangqi", "chess")
            )
        )
    )
    version_records = tuple(
        f"{package}: {_package_version(serial, package, adb_path) or 'unknown'}"
        for package in game_packages
    )

    try:
        android_api = int(properties.get("ro.build.version.sdk", ""))
    except ValueError:
        android_api = None

    abi_text = properties.get("ro.product.cpu.abilist", "")
    abi_list = tuple(item.strip() for item in abi_text.split(",") if item.strip())
    return EmulatorDiagnostics(
        emulator_vendor=vendor,
        emulator_detected=vendor is not None,
        emulator_markers=tuple(emulator_markers),
        manufacturer=properties.get("ro.product.manufacturer") or None,
        brand=properties.get("ro.product.brand") or None,
        model=properties.get("ro.product.model") or info.model,
        product=properties.get("ro.product.name") or None,
        device_codename=properties.get("ro.product.device") or None,
        hardware=properties.get("ro.hardware") or None,
        android_release=properties.get("ro.build.version.release") or info.android_release,
        android_api=android_api,
        cpu_abi=properties.get("ro.product.cpu.abi") or None,
        abi_list=abi_list,
        screen_width=info.width,
        screen_height=info.height,
        density_dpi=info.density_dpi,
        shell_identity=shell_identity,
        shell_uid=shell_uid,
        su_binary_path=su_path or None,
        root_probe_requested=probe_root,
        root_probe_output=root_probe_output,
        root_available=root_available,
        shizuku_installed=shizuku_installed,
        shizuku_process_detected=shizuku_process_detected,
        shizuku_authorization=(
            "无法仅通过普通 ADB shell 可靠确认；需在 Shizuku 管理界面检查"
        ),
        game_packages=game_packages,
        game_package_versions=version_records,
        display_summary=extract_display_summary(display_output),
        virtual_display_status="未测试；需要执行独立虚拟显示 PoC",
    )
