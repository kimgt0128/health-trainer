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
    p.add_argument(
        "--group-by-clip",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="split/CV by CLIP so reps from one video never straddle train/test (no group "
        "leakage). --no-group-by-clip uses the LEAKY rep-random split (for comparison only).",
    )
    p.add_argument("--cv-folds", type=int, default=5, help="grouped (by-clip) k-fold CV folds")
    args = p.parse_args(argv)

    import json
    import os

    import joblib
    import kagglehub
    import numpy as np
    from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier

    from healthtrainer_ml.grouped_split import (
        group_kfold_indices,
        group_train_test_split_indices,
    )
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
    groups = df["clip"].tolist()
    n_clips = len(set(groups))

    def make_model():
        if args.model == "hgb":
            return HistGradientBoostingClassifier(random_state=args.random_state)
        return RandomForestClassifier(
            n_estimators=args.n_estimators, random_state=args.random_state
        )

    # Held-out split. By CLIP (default) keeps a clip's reps on one side = no group leakage;
    # --no-group-by-clip reproduces the old LEAKY rep-random split for comparison.
    if args.group_by_clip:
        tr_idx, te_idx = group_train_test_split_indices(groups, args.test_size, args.random_state)
    else:
        from sklearn.model_selection import train_test_split

        tr_idx, te_idx = train_test_split(
            list(range(len(y))), test_size=args.test_size,
            random_state=args.random_state, stratify=y,
        )

    held = make_model()
    held.fit(X[tr_idx], y[tr_idx])
    preds = held.predict(X[te_idx]).tolist()
    truth = y[te_idx].tolist()
    held_out = {
        "accuracy": accuracy(truth, preds),
        "macro_f1": macro_f1(truth, preds, len(LABELS)),
        "confusion_matrix": confusion_matrix(truth, preds, len(LABELS)),
        "n_train": int(len(tr_idx)),
        "n_test": int(len(te_idx)),
    }

    # Grouped (by-clip) k-fold CV -> the honest, leakage-free headline number (mean ± std).
    cv = None
    if args.group_by_clip and n_clips >= 2:
        accs, f1s = [], []
        for f_tr, f_te in group_kfold_indices(groups, args.cv_folds, args.random_state):
            if not f_tr or not f_te:
                continue
            m = make_model()
            m.fit(X[f_tr], y[f_tr])
            p = m.predict(X[f_te]).tolist()
            t = y[f_te].tolist()
            accs.append(accuracy(t, p))
            f1s.append(macro_f1(t, p, len(LABELS)))
        if accs:
            cv = {
                "n_splits": len(accs),
                "accuracy_mean": float(np.mean(accs)),
                "accuracy_std": float(np.std(accs)),
                "macro_f1_mean": float(np.mean(f1s)),
                "macro_f1_std": float(np.std(f1s)),
            }

    # Shipped artifact: fit on ALL reps (held-out + CV already estimated generalization).
    final = make_model()
    final.fit(X, y)

    metrics = {
        "model": args.model,
        "split": "group_by_clip" if args.group_by_clip else "rep_random(LEAKY)",
        "n_reps": int(len(y)),
        "n_clips": int(n_clips),
        "held_out": held_out,
        "cv": cv,
    }

    joblib.dump(final, os.path.join(args.run_dir, "pushup_form_classifier.joblib"), compress=3)
    with open(os.path.join(args.run_dir, "labels_pushup_form.json"), "w") as fh:
        json.dump({str(k): v for k, v in LABELS.items()}, fh, indent=2)
    with open(os.path.join(args.run_dir, "feature_config.json"), "w") as fh:
        json.dump(feature_config(), fh, indent=2)
    with open(os.path.join(args.run_dir, "metrics_summary.json"), "w") as fh:
        json.dump({"pushup_form_classifier": metrics}, fh, indent=2)

    print(json.dumps({
        "split": metrics["split"],
        "n_reps": metrics["n_reps"],
        "n_clips": metrics["n_clips"],
        "held_out_accuracy": held_out["accuracy"],
        "cv": cv,
    }, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
