"""Perspective mapping between a 9x10 Xiangqi grid and screenshot pixels."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

import cv2
import numpy as np

Point = tuple[float, float]
Corners = tuple[Point, Point, Point, Point]


@dataclass(frozen=True)
class BoardGeometry:
    """Four outermost grid intersections in TL, TR, BR, BL order."""

    corners: Corners
    source_width: int
    source_height: int

    def __post_init__(self) -> None:
        self.validate()

    def validate(self) -> None:
        if self.source_width <= 0 or self.source_height <= 0:
            raise ValueError("source_width 和 source_height 必须为正数")
        if len(self.corners) != 4:
            raise ValueError("棋盘校准必须提供 TL、TR、BR、BL 四个角点")
        points = np.asarray(self.corners, dtype=np.float32)
        if points.shape != (4, 2) or not np.isfinite(points).all():
            raise ValueError("棋盘角点必须是四组有限的 (x, y) 坐标")
        for x, y in self.corners:
            if not (0 <= x < self.source_width and 0 <= y < self.source_height):
                raise ValueError(
                    f"角点 {(x, y)} 超出截图范围 "
                    f"{self.source_width}x{self.source_height}"
                )
        contour = points.reshape((-1, 1, 2))
        if not cv2.isContourConvex(contour):
            raise ValueError("四角必须按左上、右上、右下、左下顺序构成凸四边形")
        if abs(cv2.contourArea(contour)) < 100:
            raise ValueError("棋盘校准区域过小或角点顺序错误")

    @property
    def homography(self) -> np.ndarray:
        source = np.asarray(
            [[0, 0], [8, 0], [8, 9], [0, 9]],
            dtype=np.float32,
        )
        destination = np.asarray(self.corners, dtype=np.float32)
        matrix = cv2.getPerspectiveTransform(source, destination)
        if not np.isfinite(matrix).all():
            raise ValueError("无法根据校准角点计算透视映射")
        return matrix

    def point(self, col: float, row: float) -> Point:
        if not 0 <= col <= 8 or not 0 <= row <= 9:
            raise ValueError(f"棋盘坐标超出范围：col={col}, row={row}")
        source = np.asarray([[[col, row]]], dtype=np.float32)
        mapped = cv2.perspectiveTransform(source, self.homography)[0][0]
        return float(mapped[0]), float(mapped[1])

    def grid_points(self) -> list[list[Point]]:
        return [[self.point(col, row) for col in range(9)] for row in range(10)]

    def scaled_to(self, width: int, height: int) -> "BoardGeometry":
        """Scale calibration only when the screenshot aspect ratio is unchanged."""
        if width <= 0 or height <= 0:
            raise ValueError("目标截图尺寸必须为正数")
        old_ratio = self.source_width / self.source_height
        new_ratio = width / height
        if abs(old_ratio - new_ratio) / old_ratio > 0.01:
            raise ValueError(
                "新截图宽高比与校准截图不同，不能直接缩放角点，请重新校准棋盘。"
            )
        scale_x = width / self.source_width
        scale_y = height / self.source_height
        corners = tuple(
            (x * scale_x, y * scale_y) for x, y in self.corners
        )
        return BoardGeometry(
            corners=corners,  # type: ignore[arg-type]
            source_width=width,
            source_height=height,
        )

    def to_dict(self) -> dict:
        return {
            "corners_tl_tr_br_bl": [[x, y] for x, y in self.corners],
            "source_width": self.source_width,
            "source_height": self.source_height,
        }

    @classmethod
    def from_values(
        cls,
        corners: Iterable[Iterable[float]],
        source_width: int,
        source_height: int,
    ) -> "BoardGeometry":
        values = tuple((float(point[0]), float(point[1])) for point in corners)
        if len(values) != 4:
            raise ValueError("必须提供四个棋盘角点")
        return cls(
            corners=values,  # type: ignore[arg-type]
            source_width=int(source_width),
            source_height=int(source_height),
        )
