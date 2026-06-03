"""Window summarization for the RandomForest baseline.

A sequence model consumes the raw window; the RandomForest baseline instead consumes a
fixed-size summary of each window: per-feature min / max / mean / population std
(docs/model-training-plan.md "Baseline: RandomForest").
"""
from __future__ import annotations

import numpy as np


def summarize_window(window) -> dict:
    if not window:
        raise ValueError("cannot summarize an empty window")
    arr = np.asarray(window, dtype=float)  # shape (frames, features)
    return {
        "min": arr.min(axis=0).tolist(),
        "max": arr.max(axis=0).tolist(),
        "mean": arr.mean(axis=0).tolist(),
        "std": arr.std(axis=0).tolist(),  # population std (ddof=0)
    }


def summary_vector(window) -> list:
    """Flatten the summary feature-major: [f0_min, f0_max, f0_mean, f0_std, f1_min, ...]."""
    s = summarize_window(window)
    vec: list = []
    for i in range(len(s["min"])):
        vec.extend([s["min"][i], s["max"][i], s["mean"][i], s["std"][i]])
    return vec
