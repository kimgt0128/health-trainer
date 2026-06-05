"""Kaggle Push-Up Pose Dataset adapter (rep-level binary form classifier).

Dataset:
https://www.kaggle.com/datasets/mohamadashrafsalama/pushup

The dataset is **raw video**, not a CSV (confirmed by Colab inspection of
``kagglehub.dataset_download("mohamadashrafsalama/pushup")``). Layout::

    Correct sequence/*.mp4   (~50 clips)  -> label 0 = correct
    Wrong sequence/*.mp4     (~50 clips)  -> label 1 = incorrect
    labels/correct.npy, labels/incorrect.npy   (ignored — the folder IS the label)

Each ``.mp4`` is a multi-rep push-up SEQUENCE; the label is per-folder (= per-sequence).

We train on **rep-level** rows (one row == one rep). The full pipeline (in
``pushup_video_features``) is::

    video -> per-frame angles (extract_pushup_frames, MediaPipe, Colab)
          -> reps             (segment_reps, mirrors :core RepStateMachine)
          -> 10-feature row   (rep_features, mirrors :core PushUpFeatureExtractor)

Training on rep-level features means the model sees exactly the distribution the app's
:core ``PushUpFeatureExtractor`` produces at inference time, so train↔inference match. The
``FEATURE_COLUMNS`` below are the contract with that extractor (``FEATURE_NAMES``, surfaced
as ``feature_config.json``); reordering them silently corrupts inference (ml/LESSONS.md L4).

The deterministic, unit-tested parts (``validate`` / ``split`` / ``feature_config``) do not
touch the network. ``build_pushup_dataframe`` (re-exported from ``pushup_video_features``)
decodes video + runs MediaPipe and is Colab-only (heavy deps imported lazily).
"""
from __future__ import annotations

DATASET_HANDLE = "mohamadashrafsalama/pushup"

# Raw-video dataset: the rows are built per-rep from video, not loaded from a file in the
# archive. Kept for feature_config provenance ("the source is video, processed to rep rows").
DATASET_SOURCE = "raw video: Correct sequence/*.mp4 (label 0), Wrong sequence/*.mp4 (label 1)"

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


def build_pushup_dataframe(dataset_root, task_model_path):  # pragma: no cover
    """Build the rep-level training table from the raw-video dataset.

    Thin re-export of ``pushup_video_features.build_pushup_dataframe`` so callers that already
    import this adapter (the train CLI) get the builder from one place. Colab-only: the
    underlying function decodes video + runs MediaPipe (cv2 / mediapipe imported lazily there),
    so importing THIS module stays network/heavy-dep free.
    """
    from healthtrainer_ml.pushup_video_features import build_pushup_dataframe as _build

    return _build(dataset_root, task_model_path)


def split_features_labels(df):
    validate_pushup_dataframe(df)
    return df[FEATURE_COLUMNS].to_numpy(dtype=float), df["label"].to_numpy(dtype=int)


def feature_config() -> dict:
    return {
        "task": "pushup_form_classifier",
        "source_dataset": DATASET_HANDLE,
        "source": DATASET_SOURCE,
        "granularity": "rep",
        "features": list(FEATURE_COLUMNS),
        "labels": {str(k): v for k, v in LABELS.items()},
    }
