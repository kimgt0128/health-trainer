"""Artifact contract validation (docs/colab-drive-workflow.md 'Artifact Contract')."""
import pytest

from healthtrainer_ml.contract import (
    validate_feature_config,
    validate_labels,
    validate_metrics_summary,
)

VALID_FEATURE_CONFIG = {
    "sequence_length": 60,
    "landmarks": ["left_shoulder", "right_shoulder", "left_hip", "right_hip"],
    "per_landmark_features": ["x", "y", "z", "visibility"],
    "angle_features": ["left_knee_angle", "body_line_angle"],
    "normalization": {"center": "hip_center", "scale": "shoulder_width"},
}

VALID_METRICS = {
    "exercise_classifier": {"accuracy": 0.9, "macro_f1": 0.88},
    "phase_classifier": {"accuracy": 0.86, "macro_f1": 0.84},
}


def test_valid_feature_config_passes():
    validate_feature_config(VALID_FEATURE_CONFIG)  # must not raise


def test_missing_sequence_length_raises():
    bad = dict(VALID_FEATURE_CONFIG)
    del bad["sequence_length"]
    with pytest.raises(ValueError):
        validate_feature_config(bad)


def test_empty_landmarks_raises():
    bad = dict(VALID_FEATURE_CONFIG, landmarks=[])
    with pytest.raises(ValueError):
        validate_feature_config(bad)


def test_bad_normalization_raises():
    bad = dict(VALID_FEATURE_CONFIG, normalization={"center": "nose"})
    with pytest.raises(ValueError):
        validate_feature_config(bad)


def test_valid_labels_pass():
    validate_labels({"0": "squat", "1": "push_up"})


def test_non_contiguous_labels_raise():
    with pytest.raises(ValueError):
        validate_labels({"0": "a", "2": "b"})


def test_valid_metrics_pass():
    validate_metrics_summary(VALID_METRICS)


def test_out_of_range_accuracy_raises():
    bad = {"exercise_classifier": {"accuracy": 1.4, "macro_f1": 0.5}}
    with pytest.raises(ValueError):
        validate_metrics_summary(bad)
