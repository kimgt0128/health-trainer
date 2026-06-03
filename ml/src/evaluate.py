"""CLI: evaluate predictions and write a metrics report (light deps only)."""
from __future__ import annotations

import argparse


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Evaluate classifier predictions.")
    p.add_argument("--features", required=True, help="features .npz (held-out split)")
    p.add_argument("--run-dir", required=True, help="dir with model.joblib")
    p.add_argument("--n-classes", type=int, required=True)
    args = p.parse_args(argv)

    import json
    import os

    import joblib
    import numpy as np

    from healthtrainer_ml.metrics import accuracy, macro_f1, confusion_matrix

    data = np.load(args.features)
    X, y = data["X"], data["y"]
    model = joblib.load(os.path.join(args.run_dir, "model.joblib"))
    preds = model.predict(X).tolist()
    y = y.tolist()

    summary = {
        "accuracy": accuracy(y, preds),
        "macro_f1": macro_f1(y, preds, args.n_classes),
        "confusion_matrix": confusion_matrix(y, preds, args.n_classes),
    }
    with open(os.path.join(args.run_dir, "metrics.json"), "w") as fh:
        json.dump(summary, fh, indent=2)
    print(json.dumps({k: summary[k] for k in ("accuracy", "macro_f1")}, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
