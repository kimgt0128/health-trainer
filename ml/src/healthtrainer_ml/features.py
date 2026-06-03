"""Per-frame feature extraction = landmark coords + joint angles.

The emitted layout IS the ``feature_config.json`` contract that the Android app must
also honor (see ml/LESSONS.md L4): for each landmark in ``config.landmarks`` we emit
``(x, y, z, visibility)``, then one value per angle feature.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .geometry import angle_degrees

# The 12-landmark subset used by the model (snake_case == feature_config.json keys).
DEFAULT_LANDMARKS = [
    "left_shoulder", "right_shoulder",
    "left_elbow", "right_elbow",
    "left_wrist", "right_wrist",
    "left_hip", "right_hip",
    "left_knee", "right_knee",
    "left_ankle", "right_ankle",
]

PER_LANDMARK_FEATURES = ["x", "y", "z", "visibility"]

ANGLE_FEATURES = [
    "left_knee_angle",
    "right_knee_angle",
    "left_elbow_angle",
    "right_elbow_angle",
    "body_line_angle",
]

# Each angle feature is the interior angle at the middle joint of a triplet.
ANGLE_TRIPLETS = {
    "left_knee_angle": ("left_hip", "left_knee", "left_ankle"),
    "right_knee_angle": ("right_hip", "right_knee", "right_ankle"),
    "left_elbow_angle": ("left_shoulder", "left_elbow", "left_wrist"),
    "right_elbow_angle": ("right_shoulder", "right_elbow", "right_wrist"),
    "body_line_angle": ("left_shoulder", "left_hip", "left_ankle"),
}


@dataclass
class FeatureConfig:
    landmarks: list = field(default_factory=lambda: list(DEFAULT_LANDMARKS))
    per_landmark_features: list = field(default_factory=lambda: list(PER_LANDMARK_FEATURES))
    angle_features: list = field(default_factory=lambda: list(ANGLE_FEATURES))
    sequence_length: int = 60
    normalization: dict = field(
        default_factory=lambda: {"center": "hip_center", "scale": "shoulder_width"}
    )


def default_feature_config() -> FeatureConfig:
    return FeatureConfig()


def frame_feature_vector(frame: dict, config: FeatureConfig) -> list:
    vec: list = []
    for name in config.landmarks:
        x, y, z, vis = frame[name]
        vec.extend([x, y, z, vis])
    for angle_name in config.angle_features:
        a, b, c = ANGLE_TRIPLETS[angle_name]
        vec.append(angle_degrees(frame[a][:3], frame[b][:3], frame[c][:3]))
    return vec
