import pandas as pd
import pytest

from healthtrainer_ml.squat_pose_dataset import (
    FEATURE_COLUMNS,
    LABELS,
    feature_config,
    split_features_labels,
    validate_squat_dataframe,
)


def _df():
    rows = []
    for label in LABELS:
        row = {col: float(label + i) for i, col in enumerate(FEATURE_COLUMNS)}
        row["video_file"] = f"clip_{label}.mp4"
        row["frame"] = label
        row["label"] = label
        rows.append(row)
    return pd.DataFrame(rows)


def test_validate_accepts_expected_columns_and_labels():
    validate_squat_dataframe(_df())


def test_validate_rejects_missing_feature_column():
    df = _df().drop(columns=[FEATURE_COLUMNS[0]])
    with pytest.raises(ValueError, match="missing columns"):
        validate_squat_dataframe(df)


def test_split_returns_only_numeric_pose_features_and_labels():
    X, y = split_features_labels(_df())
    assert X.shape == (6, len(FEATURE_COLUMNS))
    assert y.tolist() == list(LABELS)


def test_feature_config_describes_kaggle_source_and_labels():
    cfg = feature_config()
    assert cfg["task"] == "squat_form_classifier"
    assert cfg["features"] == FEATURE_COLUMNS
    assert cfg["labels"]["1"] == "shallow_squat"
