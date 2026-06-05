"""Pure (no mediapipe/cv2) tests for the push-up rep segmentation + rep-level feature
aggregation. These mirror the app :core RepStateMachine + PushUpFeatureExtractor so the
training distribution equals the inference distribution (ml/LESSONS.md L4)."""
import math

import pytest

from healthtrainer_ml.pushup_video_features import (
    BODY_LINE_MIN_ANGLE,
    BOTTOM_MAX_ELBOW_ANGLE,
    BOTTOM_MIN_ELBOW_ANGLE,
    REP_DESCENT_MAX_ELBOW_ANGLE,
    TOP_MIN_ELBOW_ANGLE,
    rep_features,
    segment_reps,
)
from healthtrainer_ml.pushup_pose_dataset import FEATURE_COLUMNS


def _f(elbow, body_line, ts_ms, visible=True):
    """One frame dict in the contract shape."""
    return {"elbow": elbow, "body_line": body_line, "visible": visible, "ts_ms": ts_ms}


# ---------------------------------------------------------------------------
# Thresholds mirror PushUpRule (drift = silent inference corruption, L4).
# ---------------------------------------------------------------------------
def test_thresholds_mirror_pushup_rule():
    assert TOP_MIN_ELBOW_ANGLE == 155.0
    assert REP_DESCENT_MAX_ELBOW_ANGLE == 130.0
    assert BODY_LINE_MIN_ANGLE == 160.0
    assert BOTTOM_MIN_ELBOW_ANGLE == 70.0
    assert BOTTOM_MAX_ELBOW_ANGLE == 100.0


# ---------------------------------------------------------------------------
# segment_reps — TOP -> descent(open elbow<130) -> TOP(close elbow>=155)
# ---------------------------------------------------------------------------
def test_segment_reps_counts_two_reps():
    # TOP, down(open), bottom, up, TOP(close) == rep1 ; repeat == rep2 ; trailing TOP ignored.
    frames = [
        _f(170, 175, 0),     # TOP
        _f(120, 170, 100),   # descent opens rep1 (elbow<130)
        _f(85, 168, 200),    # bottom
        _f(120, 170, 300),   # rising
        _f(165, 175, 400),   # TOP closes rep1 (elbow>=155)
        _f(120, 170, 500),   # descent opens rep2
        _f(80, 168, 600),    # bottom
        _f(160, 175, 700),   # TOP closes rep2
        _f(170, 175, 800),   # trailing TOP (no descent after) -> not a rep
    ]
    reps = segment_reps(frames)
    assert len(reps) == 2


def test_segment_reps_open_frame_included_close_frame_included():
    frames = [
        _f(170, 175, 0),
        _f(120, 170, 100),   # opens rep
        _f(85, 168, 200),
        _f(160, 175, 300),   # closes rep
    ]
    reps = segment_reps(frames)
    assert len(reps) == 1
    rep = reps[0]
    # Buffer starts at the opening descent frame and ends at the closing TOP frame.
    assert rep[0]["ts_ms"] == 100
    assert rep[-1]["ts_ms"] == 300
    assert len(rep) == 3


def test_segment_reps_unclosed_rep_is_not_emitted():
    frames = [
        _f(170, 175, 0),
        _f(120, 170, 100),   # opens
        _f(85, 168, 200),    # never returns to TOP
    ]
    assert segment_reps(frames) == []


def test_segment_reps_shallow_rep_still_counts():
    # Shallow: elbow dips to ~120 (below descent 130 but above good BOTTOM band) -> still a rep.
    frames = [
        _f(170, 175, 0),
        _f(125, 170, 100),   # opens (elbow<130)
        _f(120, 168, 200),   # shallow bottom
        _f(160, 175, 300),   # closes
    ]
    assert len(segment_reps(frames)) == 1


def test_segment_reps_low_confidence_mid_rep_does_not_split():
    frames = [
        _f(170, 175, 0),
        _f(120, 170, 100),                       # opens
        _f(None, None, 200, visible=False),      # occluded mid-rep -> buffered, no split
        _f(85, 168, 300),
        _f(160, 175, 400),                       # closes
    ]
    reps = segment_reps(frames)
    assert len(reps) == 1
    assert len(reps[0]) == 4  # open + occluded + bottom + close


# ---------------------------------------------------------------------------
# rep_features — same 10 features, same formulas as PushUpFeatureExtractor.
# ---------------------------------------------------------------------------
def test_rep_features_returns_ten_values_in_contract_order():
    rep = [
        _f(160, 175, 0),
        _f(90, 165, 200),
        _f(160, 175, 400),
    ]
    feats = rep_features(rep)
    assert feats is not None
    assert len(feats) == len(FEATURE_COLUMNS) == 10


def test_rep_features_elbow_stats():
    rep = [
        _f(160, 175, 0),
        _f(90, 175, 200),
        _f(120, 175, 400),
    ]
    feats = rep_features(rep)
    # order: min, max, mean, range, ...
    assert feats[0] == 90.0                       # min_elbow_angle
    assert feats[1] == 160.0                      # max_elbow_angle
    assert feats[2] == pytest.approx((160 + 90 + 120) / 3)  # mean_elbow_angle
    assert feats[3] == pytest.approx(160.0 - 90.0)          # elbow_angle_range


def test_rep_features_body_line_stats_and_broken_ratio():
    rep = [
        _f(160, 175, 0),     # body_line ok
        _f(90, 150, 200),    # body_line broken (<160)
        _f(120, 158, 400),   # body_line broken (<160)
    ]
    feats = rep_features(rep)
    assert feats[4] == 150.0                                  # min_body_line_angle
    assert feats[5] == pytest.approx((175 + 150 + 158) / 3)   # mean_body_line_angle
    # broken / body_line-bearing frames = 2/3
    assert feats[6] == pytest.approx(2 / 3)                   # body_line_broken_ratio


def test_rep_features_visible_and_duration_and_down_phase():
    rep = [
        _f(160, 175, 1000),                    # not BOTTOM
        _f(90, 165, 1200),                     # BOTTOM (70<=elbow<=100)
        _f(None, None, 1400, visible=False),   # occluded: no elbow
        _f(155, 170, 1600),                    # not BOTTOM
    ]
    feats = rep_features(rep)
    # visible_frame_ratio: elbow-bearing frames / total = 3/4
    assert feats[7] == pytest.approx(3 / 4)
    # rep_duration_ms = last ts - first ts = 600
    assert feats[8] == pytest.approx(600.0)
    # down_phase_ratio: BOTTOM (elbow in [70,100]) frames / total = 1/4
    assert feats[9] == pytest.approx(1 / 4)


def test_rep_features_none_when_no_elbow_samples():
    rep = [
        _f(None, 175, 0, visible=False),
        _f(None, 170, 200, visible=False),
    ]
    assert rep_features(rep) is None


def test_rep_features_body_line_absent_falls_back_to_zero():
    # Elbows present but every frame lacks body_line -> body-line features 0, not None.
    rep = [
        _f(160, None, 0),
        _f(90, None, 200),
        _f(160, None, 400),
    ]
    feats = rep_features(rep)
    assert feats is not None
    assert feats[4] == 0.0   # min_body_line_angle
    assert feats[5] == 0.0   # mean_body_line_angle
    assert feats[6] == 0.0   # body_line_broken_ratio


def test_segment_then_features_pipeline_is_pure():
    frames = [
        _f(170, 175, 0),
        _f(120, 170, 100),
        _f(85, 150, 200),
        _f(160, 175, 300),
    ]
    reps = segment_reps(frames)
    rows = [rep_features(r) for r in reps]
    assert all(r is not None for r in rows)
    assert all(len(r) == 10 for r in rows)
    assert all(all(math.isfinite(v) for v in r) for r in rows)
