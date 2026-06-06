"""CLI: train the rep-level push-up form classifier as a self-contained Keras model -> TFLite.

sklearn (``pushup_form_classifier.joblib``, PR #13) does NOT convert to TFLite, so the app
:core ``FormClassifierRegistry`` PUSH_UP Spec (``models/pushup_form.tflite``, labels
``[correct, incorrect]``) has no model to load. This script is the push-up equivalent of
``train_squat_form_mlp.py``: a tiny Keras model we CAN export to TFLite, trained on the SAME
rep-level rows and the SAME honest, leakage-free methodology as ``train_pushup_form_classifier.py``.

WHY THE MODEL IS TINY (tiny-data discipline)
--------------------------------------------
After honest, no-leakage clip-level segmentation the dataset is only ~54 reps (see
docs/pushup-form-results.md: ~47 of 100 clips yield 0 clean reps). A wide MLP would
memorize 54 points and overfit — the opposite of useful. So the head is deliberately
minimal:

  * default ``--hidden ""`` -> **logistic regression**: Normalization -> Dense(2, softmax).
    Few parameters, hard to overfit, the right capacity for ~54 rows.
  * optional ``--hidden 8`` -> ONE small ``Dense(8)`` with L2 + dropout before the softmax,
    if a tiny bit of non-linearity ever helps as data grows. Still regularized.

This is a WEAK BASELINE ASSIST, not a strong classifier. Rules stay primary; the app
suppresses low-confidence / ``correct`` predictions (CLAUDE.md assist pattern). The durable
deliverable is the EXPORT PATH — it yields a better model automatically as more (self-filmed,
rep-labelled) data arrives. Do not overstate accuracy; the reported CV has high variance
(≈0.835 ± 0.133 with the current data) because n is tiny.

NORMALIZATION-IN-MODEL CONTRACT (mirror of squat)
-------------------------------------------------
A ``keras.layers.Normalization`` layer adapted to the TRAIN features lives INSIDE the model,
so :app feeds RAW ``PushUpFeatureExtractor`` output (``FEATURE_COLUMNS`` order) and the model
normalizes internally. There is no external scaler to drift (ml/LESSONS.md L4).

Artifact contract (matches :app ``assets/models/pushup_form.tflite``):
  - input  ``[1, 10]`` RAW features in ``FEATURE_COLUMNS`` order
  - output ``[1, 2]``  softmax probabilities, labels = ``LABELS`` = ``[correct, incorrect]``

Emits the 4-artifact SET into ``--run-dir`` (same names :app expects):
``pushup_form.tflite``, ``labels_pushup_form.json``, ``feature_config.json``,
``metrics_summary.json``.

All tensorflow/keras imports are LAZY (inside functions) so importing this module needs no
tensorflow — the local CPU test suite stays tensorflow-free (ml/LESSONS.md L3/L4). The data
pipeline decodes video + runs MediaPipe, so actual training + .tflite production is Colab only.
"""
from __future__ import annotations

import argparse

# MediaPipe pose-landmarker .task (lite). Downloaded on demand if absent (Colab). Same source
# as train_pushup_form_classifier.py so the two trainers extract identical per-frame angles.
POSE_TASK_URL = (
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
    "pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
)
POSE_TASK_FILENAME = "pose_landmarker_lite.task"


def _parse_hidden(spec: str) -> list:
    """Parse the optional hidden-layer spec.

    ``""`` (default) -> ``[]`` == logistic-regression head (right capacity for ~54 reps).
    ``"8"`` -> ``[8]`` == one small Dense(8) (L2 + dropout) before the softmax.
    Multiple widths are accepted (``"8,4"``) but discouraged on this tiny dataset.
    """
    sizes = [int(tok) for tok in spec.split(",") if tok.strip()]
    if any(s <= 0 for s in sizes):
        raise argparse.ArgumentTypeError(
            f"--hidden widths must be positive ints (or empty for logistic regression), got {spec!r}"
        )
    return sizes


def build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Train the rep-level push-up form classifier as a tiny Keras model and "
        "export to TFLite (4-artifact set)."
    )
    p.add_argument("--run-dir", required=True)
    p.add_argument("--test-size", type=float, default=0.2)
    p.add_argument("--random-state", type=int, default=0)
    p.add_argument(
        "--hidden",
        type=_parse_hidden,
        default="",
        help="optional small Dense head before softmax, e.g. '8' (one Dense(8)+L2+dropout). "
        "Default '' = logistic regression (Normalization -> Dense(2)), the safe capacity for "
        "tiny (~54-rep) data.",
    )
    p.add_argument(
        "--l2",
        type=float,
        default=1e-3,
        help="L2 weight penalty on Dense layers (regularizes the tiny-data fit).",
    )
    p.add_argument(
        "--dropout",
        type=float,
        default=0.3,
        help="dropout after each hidden Dense layer (ignored when --hidden is empty).",
    )
    p.add_argument("--epochs", type=int, default=150)
    p.add_argument("--batch-size", type=int, default=16)
    p.add_argument(
        "--group-by-clip",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="split/CV by CLIP so reps from one video never straddle train/test (no group "
        "leakage). --no-group-by-clip uses the LEAKY rep-random split (for comparison only).",
    )
    p.add_argument("--cv-folds", type=int, default=5, help="grouped (by-clip) k-fold CV folds")
    p.add_argument(
        "--float16",
        action="store_true",
        help="apply float16 weight quantization to the TFLite model",
    )
    return p


def _ensure_pose_task(run_dir):  # pragma: no cover - Colab only (network)
    """Return a local path to the pose-landmarker .task, downloading it if missing."""
    import os
    import urllib.request

    task_path = os.path.join(run_dir, POSE_TASK_FILENAME)
    if not os.path.exists(task_path):
        print(f"downloading pose-landmarker model -> {task_path}")
        urllib.request.urlretrieve(POSE_TASK_URL, task_path)
    return task_path


def build_pushup_model(hidden, n_features, n_classes, train_features, l2=1e-3, dropout=0.3):  # pragma: no cover - Colab only
    """Self-contained tiny model: Input -> Normalization(adapted to train) -> [small head] -> softmax.

    The ``Normalization`` layer is adapted to ``train_features`` so the exported model consumes
    RAW features (``FEATURE_COLUMNS`` order) and normalizes internally — :app never reproduces a
    scaler, so there is no scaling contract to drift (mirror of squat).

    ``hidden == []`` (default) -> logistic regression (Normalization -> Dense(n_classes)). This is
    the right, overfit-resistant capacity for the tiny (~54-rep) push-up dataset. A non-empty
    ``hidden`` adds small L2-regularized Dense layers + dropout (use sparingly on this data).
    """
    import numpy as np
    import tensorflow as tf  # lazy: Colab only

    keras = tf.keras
    regularizer = keras.regularizers.l2(l2) if l2 else None

    normalizer = keras.layers.Normalization(axis=-1)
    normalizer.adapt(np.asarray(train_features, dtype="float32"))

    layers = [keras.layers.Input(shape=(n_features,), name="features"), normalizer]
    for width in hidden:
        layers.append(
            keras.layers.Dense(width, activation="relu", kernel_regularizer=regularizer)
        )
        if dropout:
            layers.append(keras.layers.Dropout(dropout))
    layers.append(
        keras.layers.Dense(
            n_classes, activation="softmax", name="probs", kernel_regularizer=regularizer
        )
    )
    return keras.Sequential(layers)


def keras_model_to_tflite(model, float16=False):  # pragma: no cover - Colab only
    """Convert an in-memory Keras model to a TFLite float32 (optionally float16) buffer.

    We already hold the model in memory and need the push-up artifact filenames, so this inline
    helper mirrors ``train_squat_form_mlp.keras_model_to_tflite`` (``export_tflite.py`` instead
    loads ``model.keras`` from disk and copies generic filenames).
    """
    import tensorflow as tf  # lazy: Colab only

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    return converter.convert()


def _train_predict(make_model, X_train, y_train, X_test, epochs, batch_size):  # pragma: no cover - Colab only
    """Fit a fresh model on a fold's train split and return argmax predictions on its test split.

    Factored out so the held-out evaluation and every CV fold share one identical fit/predict
    path (no accidental divergence between the reported numbers).
    """
    import numpy as np

    model, compile_fn = make_model(X_train)
    compile_fn(model)
    model.fit(
        np.asarray(X_train, dtype="float32"),
        np.asarray(y_train),
        epochs=epochs,
        batch_size=batch_size,
        verbose=0,
    )
    probs = model.predict(np.asarray(X_test, dtype="float32"), verbose=0)
    return np.argmax(probs, axis=1).tolist()


def main(argv=None) -> int:  # pragma: no cover - exercises tensorflow + MediaPipe (Colab only)
    args = build_arg_parser().parse_args(argv)

    import json
    import os

    import kagglehub
    import numpy as np
    import tensorflow as tf  # lazy: Colab only

    from healthtrainer_ml.grouped_split import (
        group_kfold_indices,
        group_train_test_split_indices,
    )
    from healthtrainer_ml.metrics import accuracy, confusion_matrix, macro_f1
    from healthtrainer_ml.pushup_pose_dataset import (
        DATASET_HANDLE,
        FEATURE_COLUMNS,
        LABELS,
        build_pushup_dataframe,
        feature_config,
        split_features_labels,
    )

    tf.keras.utils.set_random_seed(args.random_state)
    os.makedirs(args.run_dir, exist_ok=True)

    n_features = len(FEATURE_COLUMNS)
    n_classes = len(LABELS)

    # 1) raw-video dataset + the MediaPipe .task model (same as the sklearn push-up trainer).
    dataset_root = kagglehub.dataset_download(DATASET_HANDLE)
    task_path = _ensure_pose_task(args.run_dir)

    # 2) video -> rep-level rows (one row per segmented rep) == the app :core inference-time row.
    df = build_pushup_dataframe(dataset_root, task_path)
    X, y = split_features_labels(df)
    groups = df["clip"].tolist()
    n_clips = len(set(groups))

    # A fresh, self-contained model per fit. Normalization adapts to THAT fold's train features,
    # and the compile step is bundled so every fit (held-out + CV) is identical.
    def make_model(train_features):
        model = build_pushup_model(
            args.hidden,
            n_features=n_features,
            n_classes=n_classes,
            train_features=train_features,
            l2=args.l2,
            dropout=args.dropout,
        )

        def _compile(m):
            m.compile(
                optimizer="adam",
                loss=tf.keras.losses.SparseCategoricalCrossentropy(),
                metrics=["accuracy"],
            )

        return model, _compile

    # Held-out split. By CLIP (default) keeps a clip's reps on one side = no group leakage;
    # --no-group-by-clip reproduces the LEAKY rep-random split for comparison only.
    if args.group_by_clip:
        tr_idx, te_idx = group_train_test_split_indices(groups, args.test_size, args.random_state)
    else:
        from sklearn.model_selection import train_test_split

        tr_idx, te_idx = train_test_split(
            list(range(len(y))),
            test_size=args.test_size,
            random_state=args.random_state,
            stratify=y,
        )

    preds = _train_predict(
        make_model, X[tr_idx], y[tr_idx], X[te_idx], args.epochs, args.batch_size
    )
    truth = y[te_idx].tolist()
    held_out = {
        "accuracy": accuracy(truth, preds),
        "macro_f1": macro_f1(truth, preds, n_classes),
        "confusion_matrix": confusion_matrix(truth, preds, n_classes),
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
            fold_preds = _train_predict(
                make_model, X[f_tr], y[f_tr], X[f_te], args.epochs, args.batch_size
            )
            t = y[f_te].tolist()
            accs.append(accuracy(t, fold_preds))
            f1s.append(macro_f1(t, fold_preds, n_classes))
        if accs:
            cv = {
                "n_splits": len(accs),
                "accuracy_mean": float(np.mean(accs)),
                "accuracy_std": float(np.std(accs)),
                "macro_f1_mean": float(np.mean(f1s)),
                "macro_f1_std": float(np.std(f1s)),
            }

    # Shipped artifact: fit a fresh model on ALL reps (held-out + CV already estimated
    # generalization). Its Normalization adapts to the full feature set.
    final, compile_final = make_model(X)
    compile_final(final)
    final.fit(
        np.asarray(X, dtype="float32"),
        np.asarray(y),
        epochs=args.epochs,
        batch_size=args.batch_size,
        verbose=0,
    )

    # Headline accuracy/macro_f1 == the honest grouped-CV mean when available (falls back to the
    # held-out split otherwise). Keep them at the section top level so contract.validate_metrics_summary
    # (requires accuracy + macro_f1 in [0,1]) passes, with full cv/held_out detail nested.
    headline_acc = cv["accuracy_mean"] if cv else held_out["accuracy"]
    headline_f1 = cv["macro_f1_mean"] if cv else held_out["macro_f1"]
    metrics = {
        "model": "mlp" if args.hidden else "logreg",
        "hidden": list(args.hidden),
        "granularity": "rep",
        "split": "group_by_clip" if args.group_by_clip else "rep_random(LEAKY)",
        "accuracy": headline_acc,
        "macro_f1": headline_f1,
        "metric_source": "grouped_cv_mean" if cv else "held_out",
        "n_reps": int(len(y)),
        "n_clips": int(n_clips),
        "held_out": held_out,
        "cv": cv,
    }

    # Export the 4-artifact SET (filenames :app expects under assets/models/).
    tflite_bytes = keras_model_to_tflite(final, float16=args.float16)
    with open(os.path.join(args.run_dir, "pushup_form.tflite"), "wb") as fh:
        fh.write(tflite_bytes)
    with open(os.path.join(args.run_dir, "labels_pushup_form.json"), "w") as fh:
        json.dump({str(k): v for k, v in LABELS.items()}, fh, indent=2)
    with open(os.path.join(args.run_dir, "feature_config.json"), "w") as fh:
        json.dump(feature_config(), fh, indent=2)
    with open(os.path.join(args.run_dir, "metrics_summary.json"), "w") as fh:
        json.dump({"pushup_form_classifier": metrics}, fh, indent=2)

    print(
        json.dumps(
            {
                "model": metrics["model"],
                "split": metrics["split"],
                "n_reps": metrics["n_reps"],
                "n_clips": metrics["n_clips"],
                "accuracy": headline_acc,
                "macro_f1": headline_f1,
                "metric_source": metrics["metric_source"],
                "cv": cv,
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
