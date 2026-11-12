"""Reject an APK whose ONNX JNI and core libraries require different symbol versions."""

from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

SYMBOL = re.compile(r"\bOrtGetApiBase@{1,2}(VERS_[0-9.]+)\b")


def symbol_version(binary: bytes) -> str:
    with tempfile.NamedTemporaryFile(suffix=".so") as library:
        library.write(binary)
        library.flush()
        symbols = subprocess.check_output(
            [os.environ.get("READELF", "readelf"), "--dyn-syms", "--wide", library.name], text=True
        )
    match = SYMBOL.search(symbols)
    if match is None:
        raise ValueError("OrtGetApiBase symbol version is missing")
    return match.group(1)


def main(apk: Path) -> None:
    with zipfile.ZipFile(apk) as package:
        for abi in ("arm64-v8a", "x86_64"):
            core = symbol_version(package.read(f"lib/{abi}/libonnxruntime.so"))
            jni = symbol_version(package.read(f"lib/{abi}/libonnxruntime4j_jni.so"))
            if core != jni:
                raise ValueError(f"ONNX {abi} symbol mismatch: core={core}, JNI={jni}")
            print(f"ONNX {abi}: {core}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
