from maa_xiangqi.diagnostics import (
    _classify_root_probe,
    detect_emulator_vendor,
    extract_display_summary,
    parse_getprop_output,
    parse_package_list,
)


def test_parse_getprop_output():
    properties = parse_getprop_output(
        "[ro.product.model]: [MuMu]\n"
        "[ro.build.version.sdk]: [34]\n"
        "[ro.product.cpu.abilist]: [x86_64,arm64-v8a]\n"
    )
    assert properties["ro.product.model"] == "MuMu"
    assert properties["ro.build.version.sdk"] == "34"
    assert properties["ro.product.cpu.abilist"] == "x86_64,arm64-v8a"


def test_detect_mumu_from_build_properties():
    vendor, markers = detect_emulator_vendor(
        {
            "ro.product.manufacturer": "Netease",
            "ro.product.model": "MuMu",
            "ro.hardware": "ranchu",
        }
    )
    assert vendor == "MuMu"
    assert "mumu" in markers or "netease" in markers


def test_detect_mumu_from_display_device_name():
    vendor, markers = detect_emulator_vendor(
        {
            "ro.product.manufacturer": "OPPO",
            "ro.product.model": "PGEM10",
            "ro.product.name": "wukong",
            "ro.hardware": "OPPO",
        },
        "mStaticDisplayInfo=StaticDisplayInfo{deviceProductInfo="
        "DeviceProductInfo{name=mumuscreen000}}",
    )
    assert vendor == "MuMu"
    assert "mumuscreen" in markers


def test_detect_generic_qemu_emulator():
    vendor, markers = detect_emulator_vendor({"ro.kernel.qemu": "1"})
    assert vendor == "Android Emulator (unidentified)"
    assert markers == ("qemu",)


def test_parse_package_list():
    packages = parse_package_list(
        "package:com.tencent.qqgame.xq\n"
        "package:moe.shizuku.privileged.api\n"
        "package:android\n"
    )
    assert "com.tencent.qqgame.xq" in packages
    assert "moe.shizuku.privileged.api" in packages
    assert len(packages) == 3


def test_extract_display_summary():
    summary = extract_display_summary(
        "noise line\n"
        "DisplayDeviceInfo{Built-in Screen, uniqueId=local:0}\n"
        "mDisplayId=0 state=ON\n"
        "another irrelevant line\n"
        "VirtualDisplayAdapter\n"
    )
    assert len(summary) == 3
    assert any("DisplayDeviceInfo" in line for line in summary)
    assert any("VirtualDisplayAdapter" in line for line in summary)


def test_root_probe_classifies_uid_zero_as_available():
    assert _classify_root_probe("uid=0(root) gid=0(root)") is True


def test_root_probe_classifies_non_root_uid_as_unavailable():
    assert _classify_root_probe("uid=2000(shell) gid=2000(shell)") is False


def test_root_probe_permission_denied_is_unavailable():
    assert _classify_root_probe("su: permission denied") is False


def test_root_probe_timeout_remains_unknown():
    assert _classify_root_probe("ADB 命令超时：adb shell su -c id") is None


def test_root_probe_unknown_error_remains_unknown():
    assert _classify_root_probe("ADB server temporarily unavailable") is None


def _mock_diagnostic_device(monkeypatch, root_result="denied"):
    from maa_xiangqi import diagnostics
    from maa_xiangqi.adb import DeviceInfo

    commands = []

    def fake_device_info(serial, adb_path):
        return DeviceInfo(
            serial=serial,
            model="PGEM10",
            android_release="12",
            width=900,
            height=1600,
            density_dpi=240,
        )

    def fake_run_adb(args, adb_path="adb", timeout=20):
        command = " ".join(str(part) for part in args)
        commands.append(command)
        if args[-1] == "getprop":
            return (
                b"[ro.product.manufacturer]: [OPPO]\n"
                b"[ro.product.model]: [PGEM10]\n"
                b"[ro.product.name]: [wukong]\n"
                b"[ro.product.device]: [wukong]\n"
                b"[ro.hardware]: [OPPO]\n"
                b"[ro.build.version.release]: [12]\n"
                b"[ro.build.version.sdk]: [32]\n"
                b"[ro.product.cpu.abi]: [x86_64]\n"
                b"[ro.product.cpu.abilist]: [x86_64,arm64-v8a]\n"
            )
        if "pm" in args and "list" in args:
            return (
                b"package:com.tencent.qqgame.xq\n"
                b"package:moe.shizuku.privileged.api\n"
            )
        if args[-1].startswith("ps -A"):
            return b"USER PID NAME\nshell 123 shizuku_server\n"
        if args[-1] == "id":
            return b"uid=2000(shell) gid=2000(shell)\n"
        if args[-1].startswith("command -v su"):
            return b"/system/xbin/su\n"
        if args[-1] == "su -c id":
            if root_result == "root":
                return b"uid=0(root) gid=0(root)\n"
            if root_result == "timeout":
                raise diagnostics.AdbError("ADB 命令超时：adb shell su -c id")
            if root_result == "denied":
                raise diagnostics.AdbError("su permission denied")
            return b"uid=2000(shell) gid=2000(shell)\n"
        if "dumpsys" in args and "package" in args:
            return b"versionName=4.3.0.2\n"
        if "dumpsys" in args and "display" in args:
            return (
                b"DisplayDeviceInfo{Built-in Screen}\n"
                b"mDisplayId=0 state=ON\n"
                b"mStaticDisplayInfo=StaticDisplayInfo{"
                b"deviceProductInfo=DeviceProductInfo{name=mumuscreen000}}\n"
            )
        raise AssertionError(f"Unexpected ADB command: {command}")

    monkeypatch.setattr(diagnostics, "get_device_info", fake_device_info)
    monkeypatch.setattr(diagnostics, "_run_adb", fake_run_adb)
    return diagnostics, commands


def test_diagnose_device_uses_display_metadata_and_does_not_probe_root_by_default(monkeypatch):
    diagnostics, commands = _mock_diagnostic_device()

    report = diagnostics.diagnose_device("private-test-serial")

    assert report.emulator_vendor == "MuMu"
    assert "mumuscreen" in report.emulator_markers
    assert report.android_api == 32
    assert report.abi_list == ("x86_64", "arm64-v8a")
    assert report.screen_width == 900
    assert report.screen_height == 1600
    assert report.density_dpi == 240
    assert report.shizuku_installed is True
    assert report.shizuku_process_detected is True
    assert report.root_available is None
    assert report.root_probe_requested is False
    assert not any(command.endswith("su -c id") for command in commands)
    assert "private-test-serial" not in str(report.to_dict())


def test_diagnose_device_root_probe_is_explicit(monkeypatch):
    diagnostics, commands = _mock_diagnostic_device(root_result="root")

    report = diagnostics.diagnose_device(
        "private-test-serial",
        probe_root=True,
    )

    assert report.root_probe_requested is True
    assert report.root_available is True
    assert any(command.endswith("su -c id") for command in commands)


def test_diagnose_device_root_timeout_is_unknown(monkeypatch):
    diagnostics, _ = _mock_diagnostic_device(root_result="timeout")

    report = diagnostics.diagnose_device(
        "private-test-serial",
        probe_root=True,
    )

    assert report.root_probe_requested is True
    assert report.root_available is None
    assert "超时" in report.root_probe_output


def test_diagnose_device_root_permission_denied_is_false(monkeypatch):
    diagnostics, _ = _mock_diagnostic_device(root_result="denied")

    report = diagnostics.diagnose_device(
        "private-test-serial",
        probe_root=True,
    )

    assert report.root_available is False
