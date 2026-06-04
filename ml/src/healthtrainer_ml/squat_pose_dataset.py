"""Kaggle Squat Exercise Pose Dataset adapter.

Dataset:
https://www.kaggle.com/datasets/thashmiladewmini/squat-exercise-pose-dataset

This is already a tabular MediaPipe-derived feature dataset, not raw video and not
per-landmark JSON. It should train a squat-form classifier directly.
"""
from __future__ import annotations

DATASET_HANDLE = "thashmiladewmini/squat-exercise-pose-dataset"
DATASET_FILE_PATH = "squat_dataset/squat_features_augmented.csv"

FEATURE_COLUMNS = [
    "left_knee_angle",
    "right_knee_angle",
    "left_hip_angle",
    "right_hip_angle",
    "left_ankle_angle",
    "right_ankle_angle",
    "spine_angle",
    "torso_lean",
    "left_knee_lateral",
    "right_knee_lateral",
    "symmetry_score",
    "hip_depth",
]

LABELS = {
    0: "correct",
    1: "shallow_squat",
    2: "forward_lean",
    3: "knees_caving_in",
    4: "heels_off_ground",
    5: "asymmetric_squat",
}


def validate_squat_dataframe(df) -> None:
    missing = [c for c in (*FEATURE_COLUMNS, "label") if c not in df.columns]
    if missing:
        raise ValueError(f"squat dataset missing columns: {missing}")

    labels = sorted(int(v) for v in df["label"].unique().tolist())
    expected = sorted(LABELS)
    if labels != expected:
        raise ValueError(f"expected labels {expected}, got {labels}")


def load_squat_dataframe():
    """Load the Kaggle dataset as a pandas DataFrame.

    Imports kagglehub lazily so unit tests can import this module without network IO.
    Public datasets download into KaggleHub's local cache when first used.
    """
    import kagglehub
    from kagglehub import KaggleDatasetAdapter

    df = kagglehub.load_dataset(
        KaggleDatasetAdapter.PANDAS,
        DATASET_HANDLE,
        DATASET_FILE_PATH,
    )
    validate_squat_dataframe(df)
    return df


def split_features_labels(df):
    validate_squat_dataframe(df)
    return df[FEATURE_COLUMNS].to_numpy(dtype=float), df["label"].to_numpy(dtype=int)


def feature_config() -> dict:
    return {
        "task": "squat_form_classifier",
        "source_dataset": DATASET_HANDLE,
        "source_file": DATASET_FILE_PATH,
        "features": list(FEATURE_COLUMNS),
        "labels": {str(k): v for k, v in LABELS.items()},
    }
