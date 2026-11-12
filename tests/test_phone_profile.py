from pathlib import Path

import yaml

from maa_xiangqi.board.geometry import BoardGeometry


def test_observed_phone_profile_has_90_board_intersections():
    path = Path(__file__).parents[1] / "config" / "tiantian" / "phone_1080x2400.yaml"
    profile = yaml.safe_load(path.read_text(encoding="utf-8"))
    calibration = profile["board"]["calibration"]
    geometry = BoardGeometry.from_values(
        calibration["corners_tl_tr_br_bl"],
        calibration["source_width"],
        calibration["source_height"],
    )

    points = geometry.grid_points()
    assert len(points) == 10
    assert all(len(row) == 9 for row in points)
    assert points[0][0] == (80.0, 665.0)
    assert points[9][8] == (1000.0, 1690.0)
