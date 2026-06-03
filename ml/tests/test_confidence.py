"""Mirrors :core confidence gate: required-joint visibility < 0.55 -> drop frame.
Clip dropped if >30% of frames are unusable (docs/model-training-plan.md)."""
from healthtrainer_ml.confidence import (
    REQUIRED_VISIBILITY,
    frame_is_usable,
    clip_is_usable,
)

REQ = ("left_knee", "left_hip", "left_ankle")


def _frame(vis):
    return {n: (0.0, 0.0, 0.0, vis) for n in REQ}


def test_threshold_constant_is_055():
    assert REQUIRED_VISIBILITY == 0.55


def test_below_threshold_is_unusable():
    assert frame_is_usable(_frame(0.54), REQ) is False


def test_at_threshold_is_usable():
    assert frame_is_usable(_frame(0.55), REQ) is True


def test_only_required_joints_count():
    f = _frame(0.9)
    f["nose"] = (0.0, 0.0, 0.0, 0.1)  # low-vis but not required
    assert frame_is_usable(f, REQ) is True


def test_missing_required_joint_is_unusable():
    f = _frame(0.9)
    del f["left_ankle"]
    assert frame_is_usable(f, REQ) is False


def test_clip_usable_when_drop_ratio_within_30pct():
    frames = [_frame(0.9)] * 8 + [_frame(0.1)] * 2  # 20% dropped
    assert clip_is_usable(frames, REQ) is True


def test_clip_unusable_when_drop_ratio_exceeds_30pct():
    frames = [_frame(0.9)] * 6 + [_frame(0.1)] * 4  # 40% dropped
    assert clip_is_usable(frames, REQ) is False
