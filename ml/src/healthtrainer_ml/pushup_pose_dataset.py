"""Kaggle Push-Up Pose Dataset adapter (rep-level binary form classifier).

Dataset:
https://www.kaggle.com/datasets/mohamadashrafsalama/pushup

Mirrors ``squat_pose_dataset`` but for the push-up *assist* model. The push-up model
is rep-level (one row == one rep): the app's :core ``PushUpFeatureExtractor`` aggregates
a rep's per-frame elbow/body-line metrics into the ``FEATURE_COLUMNS`` below at inference
time, so the training table must carry exactly these columns in exactly this order
(ml/LESSONS.md L4 — feature-order drift silently corrupts inference).

⚠️ SCHEMA UNVERIFIED — READ BEFORE TRAINING ⚠️
The actual schema of ``mohamadashrafsalama/pushup`` (file path inside the archive,
column names, label encoding, and crucially whether rows are per-FRAME raw landmarks or
already rep-AGGREGATED) could NOT be verified locally: this machine has no Kaggle auth and
``kagglehub`` requires network + credentials. ``DATASET_FILE_PATH`` and the column mapping
below are a reasonable PLACEHOLDER only.
Before any real training run (in Colab, where kagglehub auth exists):
  1. Print the archive's real contents / columns with kagglehub (see load_pushup_dataframe
     TODO) and fix ``DATASET_FILE_PATH`` + the column names to match.
  2. If the source is per-FRAME raw landmarks (NOT rep-aggregated), this adapter is
     INSUFFICIENT: insert a rep-aggregation preprocessing step (group frames into reps,
     then compute min/max/mean elbow angle, elbow_angle_range, body-line min/mean,
     body_line_broken_ratio, visible_frame_ratio, rep_duration_ms, down_phase_ratio)
     so the emitted columns equal :core ``PushUpFeatureExtractor.FEATURE_NAMES``.
  3. Re-confirm the label encoding really is {0: correct, 1: incorrect}.
The deterministic, unit-tested parts (validate / split / feature_config) do not touch the
network and stay valid regardless; only the placeholders above need real-data confirmation.
"""
from __future__ import annotations

DATASET_HANDLE = "mohamadashrafsalama/pushup"

# ⚠️ PLACEHOLDER — unverified. Confirm the real path inside the Kaggle archive in Colab
# (see load_pushup_dataframe TODO). The handle's archive layout is unknown locally.
DATASET_FILE_PATH = "pushup/pushup_features.csv"

# Order == app :core PushUpFeatureExtractor.FEATURE_NAMES (rep-level). DO NOT reorder:
# the model consumes positional features, so a reorder breaks inference (LESSONS.md L4).
FEATURE_COLUMNS = [
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

LABELS = {
    0: "correct",
    1: "incorrect",
}


def validate_pushup_dataframe(df) -> None:
    missing = [c for c in (*FEATURE_COLUMNS, "label") if c not in df.columns]
    if missing:
        raise ValueError(f"pushup dataset missing columns: {missing}")

    labels = sorted(int(v) for v in df["label"].unique().tolist())
    expected = sorted(LABELS)
    if labels != expected:
        raise ValueError(f"expected labels {expected}, got {labels}")


def load_pushup_dataframe():
    """Load the Kaggle push-up dataset as a pandas DataFrame.

    Imports kagglehub lazily so unit tests can import this module without network IO.

    ⚠️ SCHEMA UNVERIFIED (see module docstring). ``DATASET_FILE_PATH`` and the resulting
    columns are UNCONFIRMED locally (no Kaggle auth on this machine). In Colab, before
    trusting this, inspect the real archive/columns, e.g.::

        import kagglehub
        from kagglehub import KaggleDatasetAdapter
        df = kagglehub.load_dataset(KaggleDatasetAdapter.PANDAS, DATASET_HANDLE, "<real_file>")
        print(df.columns.tolist()); print(df.head())

    then set ``DATASET_FILE_PATH`` and align column names to ``FEATURE_COLUMNS``. If the
    archive is per-FRAME raw landmarks rather than rep-AGGREGATED rows, add a rep-aggregation
    step here (group frames -> reps -> compute the FEATURE_COLUMNS) before validate.
    """
    import kagglehub
    from kagglehub import KaggleDatasetAdapter

    df = kagglehub.load_dataset(
        KaggleDatasetAdapter.PANDAS,
        DATASET_HANDLE,
        DATASET_FILE_PATH,
    )
    # TODO(unverified): if the loaded frame is per-frame raw landmarks, aggregate to
    # rep-level FEATURE_COLUMNS here before validating. See module docstring.
    validate_pushup_dataframe(df)
    return df


def split_features_labels(df):
    validate_pushup_dataframe(df)
    return df[FEATURE_COLUMNS].to_numpy(dtype=float), df["label"].to_numpy(dtype=int)


def feature_config() -> dict:
    return {
        "task": "pushup_form_classifier",
        "source_dataset": DATASET_HANDLE,
        "source_file": DATASET_FILE_PATH,
        "features": list(FEATURE_COLUMNS),
        "labels": {str(k): v for k, v in LABELS.items()},
    }
