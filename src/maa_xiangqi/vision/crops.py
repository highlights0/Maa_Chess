"""Export 90 board-intersection crops for visual inspection and dataset building."""

from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from maa_xiangqi.board.geometry import BoardGeometry


def extract_square_crops(
    image: np.ndarray,
    geometry: BoardGeometry,
    output_dir: str | Path,
    half_size_ratio: float = 0.42,
) -> list[Path]:
    if image is None or image.ndim not in {2, 3}:
        raise ValueError("输入图像无效")
    if not 0.2 <= half_size_ratio <= 0.48:
        raise ValueError("half_size_ratio 应在 0.20 到 0.48 之间")

    height, width = image.shape[:2]
    if (width, height) != (geometry.source_width, geometry.source_height):
        geometry = geometry.scaled_to(width, height)

    points = geometry.grid_points()
    spacings: list[float] = []
    for row in range(10):
        for col in range(8):
            spacings.append(
                float(
                    np.linalg.norm(
                        np.asarray(points[row][col + 1])
                        - np.asarray(points[row][col])
                    )
                )
            )
    for row in range(9):
        for col in range(9):
            spacings.append(
                float(
                    np.linalg.norm(
                        np.asarray(points[row + 1][col])
                        - np.asarray(points[row][col])
                    )
                )
            )
    half_size = max(4, round(min(spacings) * half_size_ratio))

    destination = Path(output_dir)
    destination.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []

    for row in range(10):
        for col in range(9):
            x_float, y_float = points[row][col]
            x, y = round(x_float), round(y_float)
            left, right = max(0, x - half_size), min(width, x + half_size + 1)
            top, bottom = max(0, y - half_size), min(height, y + half_size + 1)
            crop = image[top:bottom, left:right]
            if crop.size == 0:
                raise ValueError(f"棋位 row={row}, col={col} 的裁剪区域为空")
            path = destination / f"r{row:02d}_c{col:02d}.png"
            if not cv2.imwrite(str(path), crop):
                raise OSError(f"写入棋位图块失败：{path}")
            written.append(path)

    return written
