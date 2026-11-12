import numpy as np

from maa_xiangqi.board.geometry import BoardGeometry
from maa_xiangqi.vision.crops import extract_square_crops


def test_export_exactly_90_square_crops(tmp_path):
    image = np.zeros((120, 100, 3), dtype=np.uint8)
    geometry = BoardGeometry(
        corners=((10, 10), (90, 10), (90, 100), (10, 100)),
        source_width=100,
        source_height=120,
    )

    paths = extract_square_crops(image, geometry, tmp_path)

    assert len(paths) == 90
    assert (tmp_path / "r00_c00.png").exists()
    assert (tmp_path / "r09_c08.png").exists()
