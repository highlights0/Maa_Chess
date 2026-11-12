from maa_xiangqi.diagnostics import (
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
