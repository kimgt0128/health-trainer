"""CLI: train a plank-form classifier as a self-contained Keras MLP -> TFLite.

Plank is a static HOLD, so the model is an OPTIONAL assist (hips_low / correct /
hips_high) layered on the rule engine -- never rep validity. It consumes the
rotation/scale-normalized body-frame features from ``plank_pose_dataset`` (Task 1.5),
which the :core ``PlankFeatureExtractor`` produces identically at inference.

The exported model is SELF-CONTAINED: a ``keras.layers.Normalization`` layer adapted
to the TRAIN features lives INSIDE the model, so :app feeds RAW ``PlankFeatureExtractor``
output (FEATURE_COLUMNS order) and the model normalizes internally -- no external scaling
contract to drift.

Artifact contract (matches :app ``assets/models/plank_form.tflite``):
  - input  ``[1, 5]`` RAW features in ``FEATURE_COLUMNS`` order
  - output ``[1, 3]`` softmax probabilities, labels = ``LABELS`` in order

The source labels are imbalanced (hips_low ~10%), so training uses class weights and
reports stratified 5-fold CV alongside the held-out split. This mirrors
``train_squat_form_mlp.py`` (same data load / split / metrics / artifact filenames). All
tensorflow/keras imports are LAZY (inside functions) so importing this module needs no
tensorflow -- the local CPU test suite stays tensorflow-free. Training + .tflite
production is Colab only.
"""
from __future__ import annotations

import argparse


def _parse_hidden(spec: str) -> list:
    """Parse ``"16"`` -> ``[16]`` (the Dense hidden-layer widths)."""
    sizes = [int(tok) for tok in spec.split(",") if tok.strip()]
    if not sizes or any(s <= 0 for s in sizes):
        raise argparse.ArgumentTypeError(
            f"--hidden must be comma-separated positive ints, got {spec!r}"
        )
    return sizes


def build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Train plank-form classifier as a Keras MLP and export to TFLite."
    )
    p.add_argument("--run-dir", required=True)
    p.add_argument("--test-size", type=float, default=0.2)
    p.add_argument("--random-state", type=int, default=0)
    p.add_argument(
        "--hidden",
        type=_parse_hidden,
        default="16",
        help="comma-separated Dense hidden widths, e.g. '16' (default) or '32,16'",
    )
    p.add_argument("--epochs", type=int, default=100)
    p.add_argument("--batch-size", type=int, default=32)
    p.add_argument("--cv-folds", type=int, default=5)
    p.add_argument(
        "--float16",
        action="store_true",
        help="apply float16 weight quantization to the TFLite model",
    )
    return p


def build_keras_mlp(hidden, n_features: int, n_classes: int, train_features):  # pragma: no cover - Colab only
    """Self-contained MLP: Input -> Normalization(adapted to train) -> Dense.. -> softmax."""
    import numpy as np
    import tensorflow as tf  # lazy: Colab only

    keras = tf.keras
    normalizer = keras.layers.Normalization(axis=-1)
    normalizer.adapt(np.asarray(train_features, dtype="float32"))

    layers = [keras.layers.Input(shape=(n_features,), name="features"), normalizer]
    for width in hidden:
        layers.append(keras.layers.Dense(width, activation="relu"))
    layers.append(keras.layers.Dense(n_classes, activation="softmax", name="probs"))
    return keras.Sequential(layers)


def keras_model_to_tflite(model, float16: bool = False) -> bytes:  # pragma: no cover - Colab only
    """Convert an in-memory Keras model to a TFLite float32 (optionally float16) buffer."""
    import tensorflow as tf  # lazy: Colab only

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    return converter.convert()


def _class_weights(y_train, n_classes: int) -> dict:  # pragma: no cover - Colab only
    """Inverse-frequency class weights (balances the rare hips_low class)."""
    import numpy as np

    counts = np.bincount(np.asarray(y_train, dtype=int), minlength=n_classes).astype(float)
    counts[counts == 0] = 1.0
    weights = counts.sum() / (n_classes * counts)
    return {i: float(w) for i, w in enumerate(weights)}


def _fit_new_model(args, X_train, y_train, n_features, n_classes):  # pragma: no cover - Colab only
    import numpy as np
    import tensorflow as tf  # lazy: Colab only

    model = build_keras_mlp(args.hidden, n_features, n_classes, X_train)
    model.compile(
        optimizer="adam",
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=["accuracy"],
    )
    model.fit(
        np.asarray(X_train, dtype="float32"),
        np.asarray(y_train),
        epochs=args.epochs,
        batch_size=args.batch_size,
        class_weight=_class_weights(y_train, n_classes),
        verbose=0,
    )
    return model


def main(argv=None) -> int:  # pragma: no cover - exercises tensorflow (Colab only)
    args = build_arg_parser().parse_args(argv)

    import json
    import os

    import numpy as np
    import tensorflow as tf  # lazy: Colab only
    from sklearn.model_selection import StratifiedKFold, train_test_split

    from healthtrainer_ml.metrics import accuracy, confusion_matrix, macro_f1
    from healthtrainer_ml.plank_pose_dataset import (
        FEATURE_COLUMNS,
        LABELS,
        feature_config,
        load_plank_dataframe,
        split_features_labels,
    )

    tf.keras.utils.set_random_seed(args.random_state)
    os.makedirs(args.run_dir, exist_ok=True)

    n_features, n_classes = len(FEATURE_COLUMNS), len(LABELS)
    df = load_plank_dataframe()
    X, y = split_features_labels(df)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state, stratify=y,
    )

    model = _fit_new_model(args, X_train, y_train, n_features, n_classes)
    probs = model.predict(np.asarray(X_test, dtype="float32"), verbose=0)
    preds = np.argmax(probs, axis=1).tolist()
    truth = np.asarray(y_test).tolist()

    held_out = {
        "accuracy": accuracy(truth, preds),
        "macro_f1": macro_f1(truth, preds, n_classes),
        "confusion_matrix": confusion_matrix(truth, preds, n_classes),
        "n_train": int(len(y_train)),
        "n_test": int(len(y_test)),
    }

    # Stratified k-fold CV (honest estimate; the source is class-imbalanced).
    cv_acc, cv_f1 = [], []
    skf = StratifiedKFold(n_splits=args.cv_folds, shuffle=True, random_state=args.random_state)
    for tr, te in skf.split(X, y):
        m = _fit_new_model(args, X[tr], y[tr], n_features, n_classes)
        p = np.argmax(m.predict(np.asarray(X[te], dtype="float32"), verbose=0), axis=1).tolist()
        t = np.asarray(y[te]).tolist()
        cv_acc.append(accuracy(t, p))
        cv_f1.append(macro_f1(t, p, n_classes))

    metrics = {
        "model": "mlp",
        "hidden": list(args.hidden),
        "label_distribution": {str(int(c)): int((y == c).sum()) for c in sorted(set(y.tolist()))},
        "held_out": held_out,
        "cv": {
            "n_splits": args.cv_folds,
            "accuracy_mean": float(np.mean(cv_acc)),
            "accuracy_std": float(np.std(cv_acc)),
            "macro_f1_mean": float(np.mean(cv_f1)),
            "macro_f1_std": float(np.std(cv_f1)),
        },
    }

    tflite_bytes = keras_model_to_tflite(model, float16=args.float16)
    with open(os.path.join(args.run_dir, "plank_form.tflite"), "wb") as fh:
        fh.write(tflite_bytes)
    with open(os.path.join(args.run_dir, "labels_plank_form.json"), "w") as fh:
        json.dump({str(k): v for k, v in LABELS.items()}, fh, indent=2)
    with open(os.path.join(args.run_dir, "feature_config.json"), "w") as fh:
        json.dump(feature_config(), fh, indent=2)
    with open(os.path.join(args.run_dir, "metrics_summary.json"), "w") as fh:
        json.dump({"plank_form_classifier": metrics}, fh, indent=2)

    print(json.dumps({"held_out": held_out, "cv": metrics["cv"]}, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
