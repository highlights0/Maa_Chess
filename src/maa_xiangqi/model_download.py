"""Download and verify the pinned MIT-licensed nano-v3 Xiangqi model."""

from __future__ import annotations

import argparse
import hashlib
import os
import tempfile
import urllib.request
from pathlib import Path

MODEL_URL = (
    "https://huggingface.co/spaces/yolo12138/Chinese_Chess_Recognition/"
    "resolve/4bcaf91777effa645040ee76d008d45c2ffc03f3/"
    "onnx/layout_recognition/nano_v3-0319.onnx"
)
MODEL_SHA256 = "da66ba9809f15127f8ae729b1755e42ee61c100c4f9979ce0ef13602ac471298"
MODEL_SIZE_BYTES = 31_101_356
DEFAULT_DESTINATION = Path(__file__).resolve().parents[2] / "models" / "xiangqi-nano-v3.onnx"


def verify_model(path: Path) -> None:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if path.stat().st_size != MODEL_SIZE_BYTES or digest != MODEL_SHA256:
        raise ValueError(
            "下载的模型与已核验版本不一致；"
            f"size={path.stat().st_size}, sha256={digest}"
        )


def download_model(destination: Path = DEFAULT_DESTINATION, *, force: bool = False) -> Path:
    destination = destination.expanduser().resolve()
    if destination.exists() and not force:
        verify_model(destination)
        return destination

    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=destination.name + ".",
        suffix=".download",
        dir=destination.parent,
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        request = urllib.request.Request(
            MODEL_URL,
            headers={"User-Agent": "Maa_Chess-model-setup/1.0"},
        )
        digest = hashlib.sha256()
        size = 0
        with urllib.request.urlopen(request, timeout=180) as response, temporary.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                size += len(chunk)
                digest.update(chunk)
                output.write(chunk)
        if size != MODEL_SIZE_BYTES or digest.hexdigest() != MODEL_SHA256:
            raise ValueError(
                f"模型校验失败：size={size}, sha256={digest.hexdigest()}"
            )
        temporary.replace(destination)
        return destination
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="下载并校验 nano-v3 象棋识别模型")
    parser.add_argument("--output", type=Path, default=DEFAULT_DESTINATION)
    parser.add_argument("--force", action="store_true", help="覆盖已有模型")
    args = parser.parse_args()
    path = download_model(args.output, force=args.force)
    print(f"模型已就绪：{path}")
    print(f"SHA-256：{MODEL_SHA256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
