"""CLI: train a squat-form classifier from the Kaggle tabular pose dataset."""
from __future__ import annotations

import argparse


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Train Kaggle squat-form classifier.")
    p.add_argument("--run-dir", required=True)
    p.add_argument("--test-size", type=float, default=0.2)
    p.add_argument("--random-state", type=int, default=0)
    p.add_argument("--n-estimators", type=int, default=100)
    p.add_argument(
        "--model",
        choices=["hgb", "rf"],
        default="hgb",
        help="hgb=HistGradientBoosting (best: acc 0.94 / macroF1 0.94), rf=RandomForest baseline (0.88)",
    )
    args = p.parse_args(argv)

    import json
    import os

    import joblib
    from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
    from sklearn.model_selection import train_test_split

    from healthtrainer_ml.metrics import accuracy, confusion_matrix, macro_f1
    from healthtrainer_ml.squat_pose_dataset import (
        LABELS,
        feature_config,
        load_squat_dataframe,
        split_features_labels,
    )

    os.makedirs(args.run_dir, exist_ok=True)

    df = load_squat_dataframe()
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
    }

    joblib.dump(model, os.path.join(args.run_dir, "squat_form_classifier.joblib"), compress=3)
    with open(os.path.join(args.run_dir, "labels_squat_form.json"), "w") as fh:
        json.dump({str(k): v for k, v in LABELS.items()}, fh, indent=2)
    with open(os.path.join(args.run_dir, "feature_config.json"), "w") as fh:
        json.dump(feature_config(), fh, indent=2)
    with open(os.path.join(args.run_dir, "metrics_summary.json"), "w") as fh:
        json.dump({"squat_form_classifier": metrics}, fh, indent=2)

    print(json.dumps({k: metrics[k] for k in ("accuracy", "macro_f1", "n_train", "n_test")}, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
