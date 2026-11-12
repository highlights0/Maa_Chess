"""Command-line tools for ADB capture and manual board calibration."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
import yaml

from maa_xiangqi.adb import (
    AdbError,
    capture_screenshot,
    get_device_info,
    launch_app,
    list_devices,
)
from maa_xiangqi.board.geometry import BoardGeometry
from maa_xiangqi.engine import EngineError, PikafishEngine
from maa_xiangqi.model_download import download_model
from maa_xiangqi.vision.crops import extract_square_crops
from maa_xiangqi.vision.nano_v3 import OnnxNanoV3BoardClassifier, recognize_nano_v3_board

DEFAULT_PROFILE = Path("config/tiantian/mumu_900x1600.yaml")
DEFAULT_PACKAGE = "com.tencent.qqgame.xq"


def _resolve_serial(requested: str | None, adb_path: str) -> str:
    devices = [device for device in list_devices(adb_path) if device.state == "device"]
    if requested:
        if not any(device.serial == requested for device in devices):
            raise AdbError(
                f"设备 {requested} 未处于可用的 device 状态。请执行 adb devices -l 检查授权。"
            )
        return requested
    if len(devices) == 1:
        return devices[0].serial
    if not devices:
        raise AdbError("没有可用的 ADB 设备。请启动模拟器并开启 ADB 调试。")
    serials = ", ".join(device.serial for device in devices)
    raise AdbError(f"检测到多个设备，请使用 --serial 指定：{serials}")


def _read_image(path: str | Path) -> np.ndarray:
    image = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if image is None:
        raise FileNotFoundError(f"无法读取图片：{path}")
    return image


def _load_profile(path: str | Path) -> dict:
    profile_path = Path(path)
    if not profile_path.exists():
        raise FileNotFoundError(f"配置文件不存在：{profile_path}")
    data = yaml.safe_load(profile_path.read_text(encoding="utf-8")) or {}
    if not isinstance(data, dict):
        raise TypeError("棋盘配置文件根节点必须是 YAML 对象")
    return data


def _save_profile(path: str | Path, profile: dict) -> None:
    profile_path = Path(path)
    profile_path.parent.mkdir(parents=True, exist_ok=True)
    profile_path.write_text(
        yaml.safe_dump(profile, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )


def _geometry_from_profile(
    profile: dict,
    image_width: int,
    image_height: int,
) -> BoardGeometry:
    calibration = profile.get("board", {}).get("calibration", {})
    corners = calibration.get("corners_tl_tr_br_bl")
    if not corners:
        raise ValueError(
            "棋盘尚未校准。请先运行 calibrate，并提供最外侧四个交叉点。"
        )
    source_width = calibration.get("source_width")
    source_height = calibration.get("source_height")
    if not source_width or not source_height:
        raise ValueError("校准配置缺少 source_width/source_height，请重新校准")
    geometry = BoardGeometry.from_values(corners, source_width, source_height)
    if (image_width, image_height) != (geometry.source_width, geometry.source_height):
        geometry = geometry.scaled_to(image_width, image_height)
    return geometry


def _parse_corners(value: str) -> list[tuple[float, float]]:
    try:
        points = []
        for pair in value.split(";"):
            x_text, y_text = pair.split(",", 1)
            points.append((float(x_text), float(y_text)))
    except (ValueError, TypeError) as exc:
        raise ValueError(
            '角点格式错误。示例： "xTL,yTL;xTR,yTR;xBR,yBR;xBL,yBL"'
        ) from exc
    if len(points) != 4:
        raise ValueError("必须且只能提供四个角点：TL;TR;BR;BL")
    return points


def command_devices(args: argparse.Namespace) -> int:
    devices = list_devices(args.adb)
    if not devices:
        print("未发现 ADB 设备。")
        return 0
    for device in devices:
        details = " ".join(f"{key}:{value}" for key, value in device.details.items())
        print(f"{device.serial}\t{device.state}\t{details}".rstrip())
    return 0


def command_device_info(args: argparse.Namespace) -> int:
    serial = _resolve_serial(args.serial, args.adb)
    info = get_device_info(serial, args.adb)
    print(json.dumps(info.__dict__, ensure_ascii=False, indent=2))
    return 0


def command_launch(args: argparse.Namespace) -> int:
    serial = _resolve_serial(args.serial, args.adb)
    package = args.package
    if not package:
        profile = _load_profile(args.profile)
        package = profile.get("game", {}).get("package_name", DEFAULT_PACKAGE)
    print(launch_app(serial, package, args.adb))
    return 0


def command_capture(args: argparse.Namespace) -> int:
    serial = _resolve_serial(args.serial, args.adb)
    output = args.output or (
        Path("debug/captures")
        / f"tiantian_{datetime.now().strftime('%Y%m%d_%H%M%S')}.png"
    )
    path = capture_screenshot(serial, output, args.adb)
    image = _read_image(path)
    height, width = image.shape[:2]
    print(f"截图已保存：{path.resolve()}")
    print(f"Android 内部画面尺寸：{width}x{height}")
    return 0


def command_calibrate(args: argparse.Namespace) -> int:
    image = _read_image(args.image)
    height, width = image.shape[:2]
    geometry = BoardGeometry.from_values(
        _parse_corners(args.corners),
        source_width=width,
        source_height=height,
    )
    profile = _load_profile(args.profile) if Path(args.profile).exists() else {}
    profile.setdefault("board", {})
    profile["board"]["calibration"] = {
        **geometry.to_dict(),
        "method": "manual_four_corner",
        "coordinate_space": "adb_screenshot_pixels",
    }
    _save_profile(args.profile, profile)
    print(f"校准已写入：{Path(args.profile).resolve()}")
    print(f"截图尺寸：{width}x{height}")
    print("角点顺序：左上、右上、右下、左下（均为最外侧网格交叉点）")
    return 0


def command_overlay(args: argparse.Namespace) -> int:
    image = _read_image(args.image)
    height, width = image.shape[:2]
    geometry = _geometry_from_profile(_load_profile(args.profile), width, height)
    points = geometry.grid_points()

    for row in range(10):
        line = np.asarray(
            [[round(x), round(y)] for x, y in points[row]],
            dtype=np.int32,
        )
        cv2.polylines(image, [line], False, (40, 220, 40), 1, cv2.LINE_AA)

    for col in range(9):
        line = np.asarray(
            [
                [round(points[row][col][0]), round(points[row][col][1])]
                for row in range(10)
            ],
            dtype=np.int32,
        )
        cv2.polylines(image, [line], False, (40, 220, 40), 1, cv2.LINE_AA)

    for row in range(10):
        for col in range(9):
            x, y = points[row][col]
            cv2.circle(image, (round(x), round(y)), 3, (0, 0, 255), -1)
            if row == 0 or col == 0:
                cv2.putText(
                    image,
                    f"{col},{row}",
                    (round(x) + 3, round(y) - 4),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.28,
                    (255, 0, 0),
                    1,
                    cv2.LINE_AA,
                )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(output), image):
        raise OSError(f"无法写入叠加图：{output}")
    print(f"棋盘网格叠加图已保存：{output.resolve()}")
    return 0


def command_crops(args: argparse.Namespace) -> int:
    image = _read_image(args.image)
    height, width = image.shape[:2]
    geometry = _geometry_from_profile(_load_profile(args.profile), width, height)
    paths = extract_square_crops(
        image,
        geometry,
        args.output_dir,
        half_size_ratio=args.half_size_ratio,
    )
    print(f"已导出 {len(paths)} 个棋位图块：{Path(args.output_dir).resolve()}")
    return 0


def command_engine_analyze(args: argparse.Namespace) -> int:
    options = {"Threads": args.threads, "Hash": args.hash}
    try:
        with PikafishEngine(args.engine, options=options) as engine:
            result = engine.analyze(
                args.fen,
                movetime_ms=args.movetime_ms,
                moves=args.moves,
                timeout=args.timeout,
            )
    except EngineError as exc:
        raise ValueError(str(exc)) from exc
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))
    return 0


def command_download_model(args: argparse.Namespace) -> int:
    path = (
        download_model(force=args.force)
        if args.output is None
        else download_model(args.output, force=args.force)
    )
    print(f"象棋识别模型已就绪：{path}")
    return 0


def command_recognize(args: argparse.Namespace) -> int:
    image = _read_image(args.image)
    height, width = image.shape[:2]
    profile = _load_profile(args.profile)
    geometry = _geometry_from_profile(profile, width, height)
    red_side = args.red_side or profile.get("orientation", {}).get("red_side", "bottom")
    if red_side not in {"top", "bottom"}:
        raise ValueError("棋盘方向 red_side 必须为 top 或 bottom")

    classifier = OnnxNanoV3BoardClassifier(args.model)
    result = recognize_nano_v3_board(
        image,
        geometry,
        classifier,
        side_to_move=args.side_to_move,
        red_at_top=red_side == "top",
        min_confidence=args.min_confidence,
        min_margin=args.min_margin,
    )
    predictions = {(item.row, item.col): item for item in result.predictions}
    payload = {
        "complete": result.is_complete,
        "fen": result.state.to_fen() if result.state is not None else None,
        "side_to_move": args.side_to_move,
        "mean_confidence": round(result.mean_confidence, 5),
        "problem": result.problem,
        "uncertain_squares": [
            {
                "row": row,
                "col": col,
                "symbol": predictions[(row, col)].symbol,
                "confidence": round(predictions[(row, col)].confidence, 5),
            }
            for row, col in result.uncertain_squares
        ],
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if result.is_complete else 3


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="maa-xiangqi",
        description="天天象棋 Android ADB 截图与棋盘校准工具",
    )
    parser.add_argument("--adb", default="adb", help="adb 可执行文件路径，默认从 PATH 查找")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("devices", help="列出 ADB 设备")

    info = subparsers.add_parser("device-info", help="读取设备分辨率、密度和型号")
    info.add_argument("--serial", required=True)

    launch = subparsers.add_parser("launch", help="启动天天象棋")
    launch.add_argument("--serial")
    launch.add_argument("--package", help="Android 包名；默认读取设备配置")
    launch.add_argument("--profile", default=str(DEFAULT_PROFILE))

    capture = subparsers.add_parser("capture", help="获取 Android 内部 PNG 截图")
    capture.add_argument("--serial")
    capture.add_argument("--output")

    calibrate = subparsers.add_parser("calibrate", help="用四个棋盘交叉点校准透视映射")
    calibrate.add_argument("--image", required=True)
    calibrate.add_argument("--corners", required=True, help="TL;TR;BR;BL，每点格式为 x,y")
    calibrate.add_argument("--profile", default=str(DEFAULT_PROFILE))

    overlay = subparsers.add_parser("overlay", help="输出 9x10 棋盘网格叠加图")
    overlay.add_argument("--image", required=True)
    overlay.add_argument("--profile", default=str(DEFAULT_PROFILE))
    overlay.add_argument("--output", required=True)

    crops = subparsers.add_parser("crops", help="导出 90 个棋位图块")
    crops.add_argument("--image", required=True)
    crops.add_argument("--profile", default=str(DEFAULT_PROFILE))
    crops.add_argument("--output-dir", required=True)
    crops.add_argument("--half-size-ratio", type=float, default=0.42)

    engine = subparsers.add_parser("engine-analyze", help="使用 Pikafish UCI 引擎分析给定局面")
    engine.add_argument("--engine", required=True, help="Pikafish 可执行文件路径")
    engine.add_argument("--fen", required=True, help="中国象棋 FEN")
    engine.add_argument("--moves", nargs="*", default=[], help="从 FEN 开始的 ICCS 历史走法")
    engine.add_argument("--movetime-ms", type=int, default=1000)
    engine.add_argument("--threads", type=int, default=1)
    engine.add_argument("--hash", type=int, default=128, help="Hash 大小，单位 MB")
    engine.add_argument("--timeout", type=float, help="搜索总超时秒数")

    model_download = subparsers.add_parser(
        "download-model",
        help="下载并校验 MIT 许可的 nano-v3 象棋识别模型",
    )
    model_download.add_argument("--output", type=Path)
    model_download.add_argument("--force", action="store_true")

    recognize = subparsers.add_parser(
        "recognize",
        help="使用 nano-v3 模型识别截图中的 90 个棋位",
    )
    recognize.add_argument("--image", required=True)
    recognize.add_argument("--profile", default=str(DEFAULT_PROFILE))
    recognize.add_argument("--model", default="models/xiangqi-nano-v3.onnx")
    recognize.add_argument("--side-to-move", choices=["w", "b"], required=True)
    recognize.add_argument("--red-side", choices=["top", "bottom"])
    recognize.add_argument("--min-confidence", type=float, default=0.80)
    recognize.add_argument("--min-margin", type=float, default=0.0)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        if args.command == "devices":
            return command_devices(args)
        if args.command == "device-info":
            return command_device_info(args)
        if args.command == "launch":
            return command_launch(args)
        if args.command == "capture":
            return command_capture(args)
        if args.command == "calibrate":
            return command_calibrate(args)
        if args.command == "overlay":
            return command_overlay(args)
        if args.command == "crops":
            return command_crops(args)
        if args.command == "engine-analyze":
            return command_engine_analyze(args)
        if args.command == "download-model":
            return command_download_model(args)
        if args.command == "recognize":
            return command_recognize(args)
        parser.error(f"未知命令：{args.command}")
    except (AdbError, FileNotFoundError, TypeError, ValueError, OSError, RuntimeError) as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
