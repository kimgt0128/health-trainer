"""CLI: train a rep-level push-up form classifier (binary correct/incorrect).

The push-up Kaggle dataset (``mohamadashrafsalama/pushup``) is **raw video**, not a CSV
(confirmed in Colab). This script reproduces the app :core pipeline end-to-end so the model
trains on the same distribution it later infers on:

    kagglehub.dataset_download -> raw clips
      -> build_pushup_dataframe (MediaPipe per-frame angles -> segment_reps -> rep_features)
      -> one ROW PER REP (FEATURE_COLUMNS + label)
      -> split -> HGB (default) -> metrics -> 4 artifacts.

Each rep row equals the app :core PushUpFeatureExtractor's inference-time row, so train and
inference share a distribution (rep-level, ml/LESSONS.md L4).

HistGradientBoosting is the default (it decisively beat RF on the squat tabular data,
ml/LESSONS.md L5); RF stays available via ``--model rf``.

Colab-only at runtime (decodes video + runs MediaPipe). All heavy deps — kagglehub, cv2,
mediapipe, sklearn, joblib — are imported INSIDE ``main`` so importing this module never
pulls them (lazy-import contract, test_cli_smoke / ml/LESSONS.md L3/L4).
"""
from __future__ import annotations

import argparse

# MediaPipe pose-landmarker .task (lite). Downloaded on demand if absent (Colab).
POSE_TASK_URL = (
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
    "pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
)
POSE_TASK_FILENAME = "pose_landmarker_lite.task"


def _ensure_pose_task(run_dir):  # pragma: no cover
    """Return a local path to the pose-landmarker .task, downloading it if missing."""
    import os
    import urllib.request

    task_path = os.path.join(run_dir, POSE_TASK_FILENAME)
    if not os.path.exists(task_path):
        print(f"downloading pose-landmarker model -> {task_path}")
        urllib.request.urlretrieve(POSE_TASK_URL, task_path)
    return task_path


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Train Kaggle push-up form classifier (binary).")
    p.add_argument("--run-dir", required=True)
    p.add_argument("--test-size", type=float, default=0.2)
    p.add_argument("--random-state", type=int, default=0)
    p.add_argument("--n-estimators", type=int, default=100)
    p.add_argument(
        "--model",
        choices=["hgb", "rf"],
        default="hgb",
        help="hgb=HistGradientBoosting (default; beat RF on squat, LESSONS.md L5), "
        "rf=RandomForest baseline",
    )
    args = p.parse_args(argv)

    import json
    import os

    import joblib
    import kagglehub
    from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
    from sklearn.model_selection import train_test_split

    from healthtrainer_ml.metrics import accuracy, confusion_matrix, macro_f1
    from healthtrainer_ml.pushup_pose_dataset import (
        DATASET_HANDLE,
        LABELS,
        build_pushup_dataframe,
        feature_config,
        split_features_labels,
    )

    os.makedirs(args.run_dir, exist_ok=True)

    # 1) raw-video dataset + the MediaPipe .task model.
    dataset_root = kagglehub.dataset_download(DATASET_HANDLE)
    task_path = _ensure_pose_task(args.run_dir)

    # 2) video -> per-rep rows (MediaPipe per-frame angles -> segment_reps -> rep_features).
    df = build_pushup_dataframe(dataset_root, task_path)
    X, y = split_features_labels(df)
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=args.test_size,
        random_state=args.random_state,
        stratify=y,
    )

    if args.model == "hgb":
        model = HistGradientBoostingClassifier(random_state=args.random_state)
    else:
        model = RandomForestClassifier(
            n_estimators=args.n_estimators, random_state=args.random_state
        )
    model.fit(X_train, y_train)
    preds = model.predict(X_test).tolist()
    truth = y_test.tolist()

    metrics = {
        "model": args.model,
        "accuracy": accuracy(truth, preds),
        "macro_f1": macro_f1(truth, preds, len(LABELS)),
        "confusion_matrix": confusion_matrix(truth, preds, len(LABELS)),
        "n_train": int(len(y_train)),
        "n_test": int(len(y_test)),
        "n_reps": int(len(y)),
    }

    joblib.dump(model, os.path.join(args.run_dir, "pushup_form_classifier.joblib"), compress=3)
    with open(os.path.join(args.run_dir, "labels_pushup_form.json"), "w") as fh:
        json.dump({str(k): v for k, v in LABELS.items()}, fh, indent=2)
    with open(os.path.join(args.run_dir, "feature_config.json"), "w") as fh:
        json.dump(feature_config(), fh, indent=2)
    with open(os.path.join(args.run_dir, "metrics_summary.json"), "w") as fh:
        json.dump({"pushup_form_classifier": metrics}, fh, indent=2)

    print(json.dumps({k: metrics[k] for k in ("accuracy", "macro_f1", "n_train", "n_test")}, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
