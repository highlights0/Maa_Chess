"""Install the pinned official Pikafish release into ignored Android assets."""

from __future__ import annotations

import argparse
import hashlib
import shutil
import tempfile
import urllib.request
from pathlib import Path

import py7zr

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / ".maa-cache" / "Pikafish.2026-09-06.7z"
URL = (
    "https://github.com/official-pikafish/Pikafish/releases/download/"
    "Pikafish-2026-09-06/Pikafish.2026-09-06.7z"
)
FILES = {
    "Pikafish-Android-arm64-universal": (
        ROOT / "app/src/main/jniLibs/arm64-v8a/libpikafish.so",
        "6c06b8752e10c1ed605fa836d2c9bbf885e9c402b216023040ddf4586f4320b1",
    ),
    "pikafish.nnue": (
        ROOT / "app/src/main/assets/pikafish.nnue",
        "7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e",
    ),
    "Copying.txt": (
        ROOT / "app/src/main/assets/pikafish/COPYING",
        "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986",
    ),
    "NNUE-License.md": (
        ROOT / "app/src/main/assets/pikafish/NNUE-License.md",
        "7f3320aa61ceefecec1d5cd158d6d1d2a8adde93728e83a79448df50903c367b",
    ),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="Use an already downloaded release archive")
    args = parser.parse_args()
    archive = args.archive or ARCHIVE
    if not archive.is_file():
        archive.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(URL, archive)
    if sha256(archive) != "41952bbfe2520faceb5902c69e6ab4845cc999841d2b49a95cc1be7867a25e5b":
        raise ValueError("Pikafish release archive SHA-256 mismatch")

    with tempfile.TemporaryDirectory() as directory:
        with py7zr.SevenZipFile(archive) as release:
            release.extract(path=directory, targets=list(FILES))
        for name, (destination, expected_hash) in FILES.items():
            source = Path(directory) / name
            if sha256(source) != expected_hash:
                raise ValueError(f"Pikafish asset SHA-256 mismatch: {name}")
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            print(f"Installed {name} -> {destination.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
