import math

import pandas as pd
import pytest

from healthtrainer_ml.plank_pose_dataset import (
    FEATURE_COLUMNS,
    KEYPOINT_CONVENTION,
    KEYPOINT_INDICES,
    LABELS,
    SOURCE_COLUMNS,
    feature_config,
    split_features_labels,
    validate_plank_dataframe,
)

# ---- contract ------------------------------------------------------------------------------

def test_label_contract_is_app_facing():
    assert LABELS == {0: "hips_low", 1: "correct", 2: "hips_high"}


def test_feature_contract_order_is_stable():
    # Body-frame contract. Order MUST equal :core PlankFeatureExtractor.featureNames().
    assert FEATURE_COLUMNS == [
        "body_line_angle",
        "hip_perp_offset_signed",
        "hip_perp_offset_abs",
        "hip_axial_ratio",
        "required_visible_ratio",
    ]


def test_source_columns_are_36_coordinates():
    assert SOURCE_COLUMNS == [str(i) for i in range(36)]


def test_keypoint_mapping_is_audited_coco18():
    assert KEYPOINT_INDICES == {
        "left_shoulder": 5,
        "right_shoulder": 2,
        "left_elbow": 6,
        "right_elbow": 3,
        "left_hip": 11,
        "right_hip": 8,
        "left_ankle": 13,
        "right_ankle": 10,
    }
    assert "COCO-18" in KEYPOINT_CONVENTION


def test_validate_requires_source_columns_and_label():
    df = pd.DataFrame({"label": [1]})
    with pytest.raises(ValueError, match="plank dataset missing columns"):
        validate_plank_dataframe(df)


def test_validate_rejects_out_of_range_label():
    row = {str(i): 1.0 for i in range(36)}
    row["label"] = 9
    with pytest.raises(ValueError, match="unexpected plank labels"):
        validate_plank_dataframe(pd.DataFrame([row]))


def test_validate_accepts_single_class_subset():
    row = {str(i): 1.0 for i in range(36)}
    row["label"] = 1
    validate_plank_dataframe(pd.DataFrame([row]))  # does not raise


def test_split_features_labels_returns_expected_shapes():
    row = {str(i): float(i + 1) for i in range(36)}
    row["filename"] = "sample"
    row["label"] = 1
    df = pd.DataFrame([row])

    X, y = split_features_labels(df)

    assert X.shape == (1, len(FEATURE_COLUMNS))
    assert y.tolist() == [1]


# ---- body-frame helpers --------------------------------------------------------------------

def _row(shoulder, hip, ankle, label=1, drop=()):
    """A synthetic source row: L and R of each part collapsed onto one point (so the center is
    exactly that point). Coordinates are image-style, y-DOWN. ``drop`` blanks a body part.
    """
    row = {str(i): float("nan") for i in range(36)}

    def setpair(left_name, right_name, p):
        for name in (left_name, right_name):
            if name in drop:
                continue
            k = KEYPOINT_INDICES[name]
            row[str(2 * k)] = float(p[0])
            row[str(2 * k + 1)] = float(p[1])

    setpair("left_shoulder", "right_shoulder", shoulder)
    setpair("left_hip", "right_hip", hip)
    setpair("left_ankle", "right_ankle", ankle)
    row["label"] = label
    return row


def _feats(shoulder, hip, ankle, **kw):
    X, _ = split_features_labels(pd.DataFrame([_row(shoulder, hip, ankle, **kw)]))
    return X[0]


def _f(vec, name):
    return vec[FEATURE_COLUMNS.index(name)]


def _rot(p, theta):
    c, s = math.cos(theta), math.sin(theta)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c)


# Canonical horizontal plank: shoulder left, ankle right, y-down.
_SHOULDER = (0.0, 0.0)
_ANKLE = (2.0, 0.0)


# ---- sign convention (matches PlankRule: sag/hips_low > 0) ----------------------------------

def test_horizontal_sagging_hip_is_positive():
    # hip below the shoulder->ankle line (larger y, toward ground) = sag = hips_low
    vec = _feats(_SHOULDER, (1.0, 0.3), _ANKLE, label=0)
    assert _f(vec, "hip_perp_offset_signed") > 0


def test_horizontal_piked_hip_is_negative():
    # hip above the line (smaller y) = pike = hips_high
    vec = _feats(_SHOULDER, (1.0, -0.3), _ANKLE, label=2)
    assert _f(vec, "hip_perp_offset_signed") < 0


def test_straight_plank_has_max_body_line_angle_and_zero_offset():
    vec = _feats(_SHOULDER, (1.0, 0.0), _ANKLE)
    assert _f(vec, "body_line_angle") == pytest.approx(180.0, abs=1e-3)
    assert _f(vec, "hip_perp_offset_signed") == pytest.approx(0.0, abs=1e-9)


# ---- scale invariance (10x pixels -> same normalized features) ------------------------------

def test_features_are_scale_invariant_10x():
    base = _feats(_SHOULDER, (1.0, 0.3), _ANKLE)
    scaled = _feats((0.0, 0.0), (10.0, 3.0), (20.0, 0.0))
    for name in FEATURE_COLUMNS:
        assert _f(scaled, name) == pytest.approx(_f(base, name), abs=1e-6)


# ---- rotation invariance (camera tilt -> stable features) -----------------------------------

def test_features_are_rotation_invariant():
    theta = math.radians(37)
    sh, hip, an = _SHOULDER, (1.0, 0.3), _ANKLE
    base = _feats(sh, hip, an)
    rotated = _feats(_rot(sh, theta), _rot(hip, theta), _rot(an, theta))
    for name in ("body_line_angle", "hip_perp_offset_signed", "hip_perp_offset_abs", "hip_axial_ratio"):
        assert _f(rotated, name) == pytest.approx(_f(base, name), abs=1e-4)


def test_sign_survives_rotation():
    # A rotated sag is still a positive offset (rotation cannot turn a sag into a pike).
    theta = math.radians(50)
    sh, hip, an = _SHOULDER, (1.0, 0.3), _ANKLE
    rotated = _feats(_rot(sh, theta), _rot(hip, theta), _rot(an, theta), label=0)
    assert _f(rotated, "hip_perp_offset_signed") > 0


# ---- missing keypoint handling -------------------------------------------------------------

def test_missing_one_ankle_lowers_visible_ratio_but_still_extracts():
    vec = _feats(_SHOULDER, (1.0, 0.3), _ANKLE, drop=("right_ankle",))
    assert _f(vec, "required_visible_ratio") == pytest.approx(5 / 6)
    # left ankle still present -> the body frame is still computed (offset is real, not 0).
    assert _f(vec, "hip_perp_offset_signed") > 0


def test_all_keypoints_missing_returns_zero_vector():
    row = {str(i): float("nan") for i in range(36)}
    row["label"] = 1
    X, _ = split_features_labels(pd.DataFrame([row]))
    assert X.shape == (1, len(FEATURE_COLUMNS))
    assert X[0].tolist() == [0.0] * len(FEATURE_COLUMNS)


# ---- feature_config provenance -------------------------------------------------------------

def test_feature_config_cites_source_and_license_state():
    cfg = feature_config()
    assert cfg["task"] == "plank_form_classifier"
    assert cfg["source_dataset"] == "Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras"
    assert cfg["source_license"] == "none declared in GitHub API"
    assert cfg["features"] == FEATURE_COLUMNS


def test_feature_config_declares_body_frame_and_convention():
    cfg = feature_config()
    assert "COCO-18" in cfg["keypoint_convention"]
    assert "body frame" in cfg["feature_frame"]
