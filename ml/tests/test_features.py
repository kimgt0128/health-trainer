"""Per-frame feature vector = landmark coords + joint angles, per feature_config."""
from healthtrainer_ml.features import (
    ANGLE_FEATURES,
    default_feature_config,
    frame_feature_vector,
)
from healthtrainer_ml.geometry import angle_degrees


def _full_frame():
    return {
        "left_shoulder": (0.0, 3.0, 0.0, 0.9),
        "right_shoulder": (2.0, 3.0, 0.0, 0.9),
        "left_elbow": (0.0, 2.0, 0.0, 0.9),
        "right_elbow": (2.0, 2.0, 0.0, 0.9),
        "left_wrist": (1.0, 2.0, 0.0, 0.9),
        "right_wrist": (3.0, 2.0, 0.0, 0.9),
        "left_hip": (0.0, 2.0, 0.0, 0.9),
        "right_hip": (2.0, 2.0, 0.0, 0.9),
        "left_knee": (0.0, 1.0, 0.0, 0.9),
        "right_knee": (2.0, 1.0, 0.0, 0.9),
        "left_ankle": (1.0, 1.0, 0.0, 0.9),
        "right_ankle": (3.0, 1.0, 0.0, 0.9),
    }


def test_angle_feature_names_match_contract():
    assert ANGLE_FEATURES == [
        "left_knee_angle",
        "right_knee_angle",
        "left_elbow_angle",
        "right_elbow_angle",
        "body_line_angle",
    ]


def test_vector_length_is_landmarks_times_4_plus_angles():
    cfg = default_feature_config()
    vec = frame_feature_vector(_full_frame(), cfg)
    assert len(vec) == len(cfg.landmarks) * 4 + len(cfg.angle_features)
    assert len(vec) == 12 * 4 + 5


def test_landmark_block_is_in_config_order():
    cfg = default_feature_config()
    vec = frame_feature_vector(_full_frame(), cfg)
    # first landmark in config -> its (x,y,z,vis) occupy vec[0:4]
    first = cfg.landmarks[0]
    assert tuple(vec[0:4]) == _full_frame()[first]


def test_left_knee_angle_matches_geometry():
    cfg = default_feature_config()
    f = _full_frame()
    vec = frame_feature_vector(f, cfg)
    expected = angle_degrees(f["left_hip"][:3], f["left_knee"][:3], f["left_ankle"][:3])
    idx = len(cfg.landmarks) * 4 + cfg.angle_features.index("left_knee_angle")
    assert abs(vec[idx] - expected) < 0.5
    assert abs(expected - 90.0) < 0.5  # this synthetic frame is a right angle
