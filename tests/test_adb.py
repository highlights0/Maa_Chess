from maa_xiangqi.adb import parse_devices_output


def test_parse_adb_devices_output():
    output = (
        "List of devices attached\n"
        "emulator-5554 device product:sdk_gphone64_x86_64 model:Pixel_7\n"
        "emulator-5556 unauthorized usb:1-1\n"
    )
    devices = parse_devices_output(output)
    assert len(devices) == 2
    assert devices[0].serial == "emulator-5554"
    assert devices[0].state == "device"
    assert devices[0].details["model"] == "Pixel_7"
    assert devices[1].state == "unauthorized"
