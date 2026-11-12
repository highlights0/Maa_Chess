from pathlib import Path

import yaml

from maa_xiangqi.board.geometry import BoardGeometry


def test_mumu_900x1600_profile_has_90_board_intersections():
    profile_path = (
        Path(__file__).parents[1]
        / "config"
        / "tiantian"
        / "mumu_900x1600.yaml"
    )
    profile = yaml.safe_load(profile_path.read_text(encoding="utf-8"))
    calibration = profile["board"]["calibration"]
    geometry = BoardGeometry.from_values(
        calibration["corners_tl_tr_br_bl"],
        calibration["source_width"],
        calibration["source_height"],
    )

    points = geometry.grid_points()
    assert len(points) == 10
    assert all(len(row) == 9 for row in points)
    assert points[0][0] == (66.0, 363.0)
    assert points[9][8] == (834.0, 1227.0)
    assert profile["device"]["emulator_vendor"] == "MuMu"
