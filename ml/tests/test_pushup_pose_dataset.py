import pandas as pd
import pytest

from healthtrainer_ml.pushup_pose_dataset import (
    FEATURE_COLUMNS,
    LABELS,
    feature_config,
    split_features_labels,
    validate_pushup_dataframe,
)


def _df():
    """Synthetic rep-aggregated DataFrame; no kagglehub / network IO."""
    rows = []
    for label in LABELS:
        row = {col: float(label + i) for i, col in enumerate(FEATURE_COLUMNS)}
        row["rep_id"] = label
        row["clip"] = f"clip_{label}.mp4"
        row["label"] = label
        rows.append(row)
    return pd.DataFrame(rows)


def test_feature_columns_match_core_contract_order():
    # Order is the :core PushUpFeatureExtractor.FEATURE_NAMES contract (rep-level plan).
    assert FEATURE_COLUMNS == [
        "min_elbow_angle",
        "max_elbow_angle",
        "mean_elbow_angle",
        "elbow_angle_range",
        "min_body_line_angle",
        "mean_body_line_angle",
        "body_line_broken_ratio",
        "visible_frame_ratio",
        "rep_duration_ms",
        "down_phase_ratio",
    ]


def test_labels_are_binary_correct_incorrect():
    assert LABELS == {0: "correct", 1: "incorrect"}


def test_validate_accepts_expected_columns_and_labels():
    validate_pushup_dataframe(_df())


def test_validate_rejects_missing_feature_column():
    df = _df().drop(columns=[FEATURE_COLUMNS[0]])
    with pytest.raises(ValueError, match="missing columns"):
        validate_pushup_dataframe(df)


def test_validate_rejects_unexpected_label_set():
    df = _df()
    df.loc[df.index[-1], "label"] = 2  # binary task only has {0, 1}
    with pytest.raises(ValueError, match="expected labels"):
        validate_pushup_dataframe(df)


def test_split_returns_only_numeric_pose_features_and_labels():
    X, y = split_features_labels(_df())
    assert X.shape == (2, len(FEATURE_COLUMNS))
    assert y.tolist() == list(LABELS)


def test_feature_config_describes_kaggle_source_and_labels():
    cfg = feature_config()
    assert cfg["task"] == "pushup_form_classifier"
    assert cfg["features"] == FEATURE_COLUMNS
    assert cfg["labels"]["0"] == "correct"
    assert cfg["labels"]["1"] == "incorrect"
