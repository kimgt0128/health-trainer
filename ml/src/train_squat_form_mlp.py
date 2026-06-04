"""CLI: train a squat-form classifier as a self-contained Keras MLP -> TFLite.

Option 1 (on-device): replaces the sklearn HistGradientBoosting squat model with a
tiny Keras MLP that we export to TFLite so it runs in :app. Measured on the cached
Kaggle data MLP(32,16)=0.951 / (64,32)=0.963 >= HistGBT 0.943, so no accuracy loss.

The exported model is SELF-CONTAINED: a ``keras.layers.Normalization`` layer adapted
to the TRAIN features lives INSIDE the model, so :app feeds RAW ``SquatFeatureExtractor``
output (FEATURE_COLUMNS order) and the model normalizes internally. There is no external
scaling contract to drift.

Artifact contract (matches :app ``assets/models/squat_form.tflite``):
  - input  ``[1, 12]`` RAW features in ``FEATURE_COLUMNS`` order
  - output ``[1, 6]``  softmax probabilities, labels = ``LABELS`` in order

This mirrors ``train_squat_form_classifier.py`` (same data load / split / metrics /
artifact filenames). All tensorflow/keras imports are LAZY (inside ``main``) so importing
this module needs no tensorflow — keeping the local CPU test suite tensorflow-free
(ml/LESSONS.md L3/L4). Actual training + .tflite production is Colab only.
"""
from __future__ import annotations

import argparse


def _parse_hidden(spec: str) -> list:
    """Parse ``"64,32"`` -> ``[64, 32]`` (the Dense hidden-layer widths)."""
    sizes = [int(tok) for tok in spec.split(",") if tok.strip()]
    if not sizes or any(s <= 0 for s in sizes):
        raise argparse.ArgumentTypeError(
            f"--hidden must be comma-separated positive ints, got {spec!r}"
        )
    return sizes


def build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Train squat-form classifier as a Keras MLP and export to TFLite."
    )
    p.add_argument("--run-dir", required=True)
    p.add_argument("--test-size", type=float, default=0.2)
    p.add_argument("--random-state", type=int, default=0)
    p.add_argument(
        "--hidden",
        type=_parse_hidden,
        default="64,32",
        help="comma-separated Dense hidden widths, e.g. '64,32' (default) or '32,16'",
    )
    p.add_argument("--epochs", type=int, default=200)
    p.add_argument("--batch-size", type=int, default=32)
    p.add_argument(
        "--float16",
        action="store_true",
        help="apply float16 weight quantization to the TFLite model",
    )
    return p


def build_keras_mlp(hidden, n_features: int, n_classes: int, train_features):  # pragma: no cover - Colab only
    """Self-contained MLP: Input -> Normalization(adapted to train) -> Dense.. -> softmax.

    The ``Normalization`` layer is adapted to ``train_features`` so the exported model
    consumes RAW features (FEATURE_COLUMNS order) and normalizes internally — :app never
    has to reproduce a scaler, so there is no scaling contract to drift.
    """
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
    """Convert an in-memory Keras model to a TFLite float32 (optionally float16) buffer.

    ``export_tflite.py`` loads a model from ``model.keras`` on disk and copies different
    filenames; here we already hold the model in memory and need the squat artifact
    filenames, so this minimal helper keeps the conversion inline.
    """
    import tensorflow as tf  # lazy: Colab only

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    return converter.convert()


def main(argv=None) -> int:  # pragma: no cover - exercises tensorflow (Colab only)
    args = build_arg_parser().parse_args(argv)

    import json
    import os

    import numpy as np
    import tensorflow as tf  # lazy: Colab only
    from sklearn.model_selection import train_test_split

    from healthtrainer_ml.metrics import accuracy, confusion_matrix, macro_f1
    from healthtrainer_ml.squat_pose_dataset import (
        FEATURE_COLUMNS,
        LABELS,
        feature_config,
        load_squat_dataframe,
        split_features_labels,
    )

    tf.keras.utils.set_random_seed(args.random_state)
    os.makedirs(args.run_dir, exist_ok=True)

    df = load_squat_dataframe()
    X, y = split_features_labels(df)
    # Same split contract as the sklearn trainer: stratified, identical kwargs.
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=args.test_size,
        random_state=args.random_state,
        stratify=y,
    )

    model = build_keras_mlp(
        args.hidden,
        n_features=len(FEATURE_COLUMNS),
        n_classes=len(LABELS),
        train_features=X_train,
    )
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
        verbose=0,
    )

    probs = model.predict(np.asarray(X_test, dtype="float32"), verbose=0)
    preds = np.argmax(probs, axis=1).tolist()
    truth = np.asarray(y_test).tolist()

    metrics = {
        "model": "mlp",
        "hidden": list(args.hidden),
        "accuracy": accuracy(truth, preds),
        "macro_f1": macro_f1(truth, preds, len(LABELS)),
        "confusion_matrix": confusion_matrix(truth, preds, len(LABELS)),
        "n_train": int(len(y_train)),
        "n_test": int(len(y_test)),
    }

    # Export the artifact SET (filenames match train_squat_form_classifier.py / :app).
    tflite_bytes = keras_model_to_tflite(model, float16=args.float16)
    with open(os.path.join(args.run_dir, "squat_form.tflite"), "wb") as fh:
        fh.write(tflite_bytes)
    with open(os.path.join(args.run_dir, "labels_squat_form.json"), "w") as fh:
        json.dump({str(k): v for k, v in LABELS.items()}, fh, indent=2)
    with open(os.path.join(args.run_dir, "feature_config.json"), "w") as fh:
        json.dump(feature_config(), fh, indent=2)
    with open(os.path.join(args.run_dir, "metrics_summary.json"), "w") as fh:
        json.dump({"squat_form_classifier": metrics}, fh, indent=2)

    print(
        json.dumps(
            {k: metrics[k] for k in ("accuracy", "macro_f1", "n_train", "n_test")},
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
