"""Mirrors the Android :core AngleCalculator contract (angle tolerance 0.5 deg)."""
from healthtrainer_ml.geometry import angle_degrees, distance, midpoint


def test_right_angle_returns_90():
    assert abs(angle_degrees((1, 0, 0), (0, 0, 0), (0, 1, 0)) - 90.0) < 0.5


def test_straight_line_returns_180():
    assert abs(angle_degrees((-1, 0, 0), (0, 0, 0), (1, 0, 0)) - 180.0) < 0.5


def test_collapsed_point_returns_0():
    # a and c on the same ray from b -> 0 degrees
    assert abs(angle_degrees((1, 0, 0), (0, 0, 0), (2, 0, 0)) - 0.0) < 0.5


def test_distance_is_euclidean():
    assert abs(distance((0, 0, 0), (3, 4, 0)) - 5.0) < 1e-6


def test_midpoint_averages_components():
    assert midpoint((0, 0, 0), (2, 4, 6)) == (1.0, 2.0, 3.0)


def test_zero_length_segment_does_not_crash():
    # degenerate: b == a. Should clamp, not raise/NaN.
    angle = angle_degrees((0, 0, 0), (0, 0, 0), (1, 0, 0))
    assert angle == angle  # not NaN
