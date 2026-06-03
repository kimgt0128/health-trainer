"""Mirrors :core LandmarkNormalizer: translate by hip center, scale by shoulder width."""
import pytest

from healthtrainer_ml.normalize import normalize_frame


def _frame():
    # hips centered at (1,1,0); shoulders 2 apart -> shoulder width 2.0
    return {
        "left_hip": (0.0, 1.0, 0.0, 0.9),
        "right_hip": (2.0, 1.0, 0.0, 0.9),
        "left_shoulder": (0.0, 3.0, 0.0, 0.9),
        "right_shoulder": (2.0, 3.0, 0.0, 0.9),
        "left_knee": (1.0, 1.0, 0.0, 0.8),
    }


def test_hip_center_maps_to_origin():
    out = normalize_frame(_frame())
    # midpoint of hips is (1,1,0); the knee at (1,1,0) sits exactly on hip center
    kx, ky, kz, _ = out["left_knee"]
    assert abs(kx) < 1e-6 and abs(ky) < 1e-6 and abs(kz) < 1e-6


def test_scaled_by_shoulder_width():
    out = normalize_frame(_frame())
    # left_shoulder is (0,3,0); relative to hip center (1,1,0) -> (-1, 2, 0),
    # divided by shoulder width 2.0 -> (-0.5, 1.0, 0.0)
    sx, sy, sz, _ = out["left_shoulder"]
    assert abs(sx - (-0.5)) < 1e-6
    assert abs(sy - 1.0) < 1e-6
    assert abs(sz) < 1e-6


def test_visibility_is_preserved():
    out = normalize_frame(_frame())
    assert out["left_knee"][3] == 0.8
    assert out["left_hip"][3] == 0.9


def test_zero_shoulder_width_raises():
    f = _frame()
    f["right_shoulder"] = (0.0, 3.0, 0.0, 0.9)  # both shoulders at same point
    with pytest.raises(ValueError):
        normalize_frame(f)
