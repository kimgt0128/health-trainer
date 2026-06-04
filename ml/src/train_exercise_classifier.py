"""CLI: train the exercise classifier (RandomForest baseline locally; LSTM on Colab)."""
from __future__ import annotations

import argparse


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Train exercise classifier.")
    p.add_argument("--features", required=True, help="features .npz from build_features")
    p.add_argument("--run-dir", required=True)
    p.add_argument("--model", choices=["baseline", "sequence"], default="baseline")
    args = p.parse_args(argv)

    import os

    import numpy as np

    os.makedirs(args.run_dir, exist_ok=True)
    data = np.load(args.features)
    X, y = data["X"], data["y"]

    if args.model == "baseline":
        import joblib

        from healthtrainer_ml.train_baseline import train_random_forest

        model = train_random_forest(X.tolist(), y.tolist())
        joblib.dump(model, os.path.join(args.run_dir, "model.joblib"))
    else:  # sequence model lives on Colab (tensorflow lazy)
        from healthtrainer_ml.train_sequence import train_lstm  # noqa: F401  (Colab)

        raise SystemExit("sequence model training runs on Colab (tensorflow required)")
    print(f"trained {args.model} exercise classifier -> {args.run_dir}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
