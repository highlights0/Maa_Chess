"""Standalone command for MuMu / Android device diagnostics."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from maa_xiangqi.diagnostics import diagnose_device


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="maa-xiangqi-diagnose",
        description="收集 MuMu / Android 环境信息，报告不包含设备序列号",
    )
    parser.add_argument("--serial", required=True, help="adb devices 中的设备序列号")
    parser.add_argument("--adb", default="adb", help="adb 可执行文件路径")
    parser.add_argument(
        "--probe-root",
        action="store_true",
        help="显式执行 su -c id，可能触发 Root 授权提示",
    )
    parser.add_argument("--output", help="保存 JSON 报告的本地路径")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    report = diagnose_device(
        args.serial,
        adb_path=args.adb,
        probe_root=args.probe_root,
    )
    output = json.dumps(report.to_dict(), ensure_ascii=False, indent=2)
    if args.output:
        destination = Path(args.output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(output + "\n", encoding="utf-8")
        print(f"诊断报告已保存：{destination.resolve()}")
        print("报告不包含 ADB 设备序列号。")
    else:
        print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
