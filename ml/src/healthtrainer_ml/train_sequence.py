"""Sequence models (LSTM / 1D-CNN) — Colab only (tensorflow imported lazily).

Importing this module is cheap; calling ``train_lstm`` requires tensorflow and a GPU/CPU
Colab runtime. Architecture per docs/model-training-plan.md ('Sequence Model: LSTM').
"""
from __future__ import annotations


def build_lstm(feature_dim: int, sequence_length: int, n_classes: int):  # pragma: no cover
    import tensorflow as tf  # lazy: Colab only

    return tf.keras.Sequential([
        tf.keras.layers.Input(shape=(sequence_length, feature_dim)),
        tf.keras.layers.LSTM(64),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dense(n_classes, activation="softmax"),
    ])


def train_lstm(X, y, sequence_length, n_classes, epochs=20):  # pragma: no cover
    import numpy as np
    import tensorflow as tf  # lazy: Colab only

    X = np.asarray(X, dtype=float)
    feature_dim = X.shape[-1]
    model = build_lstm(feature_dim, sequence_length, n_classes)
    model.compile(
        optimizer="adam",
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=["accuracy"],
    )
    model.fit(X, np.asarray(y), epochs=epochs, verbose=0)
    return model
