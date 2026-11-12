import pytest

from maa_xiangqi.board.geometry import BoardGeometry


def sample_geometry():
    return BoardGeometry(
        corners=((10, 20), (90, 20), (90, 110), (10, 110)),
        source_width=100,
        source_height=120,
    )


def test_corner_mapping():
    geometry = sample_geometry()
    assert geometry.point(0, 0) == pytest.approx((10, 20), abs=0.01)
    assert geometry.point(8, 9) == pytest.approx((90, 110), abs=0.01)
    assert geometry.point(4, 4.5) == pytest.approx((50, 65), abs=0.01)
    assert len(geometry.grid_points()) == 10
    assert all(len(row) == 9 for row in geometry.grid_points())


def test_scale_geometry_same_aspect_ratio():
    scaled = sample_geometry().scaled_to(200, 240)
    assert scaled.point(0, 0) == pytest.approx((20, 40), abs=0.01)
    assert scaled.point(8, 9) == pytest.approx((180, 220), abs=0.01)


def test_reject_aspect_ratio_change():
    with pytest.raises(ValueError):
        sample_geometry().scaled_to(200, 200)


def test_reject_non_convex_or_bad_corner_order():
    with pytest.raises(ValueError):
        BoardGeometry(
            corners=((10, 20), (90, 110), (90, 20), (10, 110)),
            source_width=100,
            source_height=120,
        )
