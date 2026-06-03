"""RandomForest baseline classifier (docs/model-training-plan.md 'Baseline: RandomForest').

Consumes per-window summary feature vectors (see summarize.summary_vector). Kept tiny:
the real tuning happens on Colab; this is the CPU-runnable, feature-importance-friendly
first model.
"""
from __future__ import annotations

import numpy as np
from sklearn.ensemble import RandomForestClassifier


def train_random_forest(X, y, n_estimators: int = 100, random_state: int = 0):
    clf = RandomForestClassifier(n_estimators=n_estimators, random_state=random_state)
    clf.fit(np.asarray(X, dtype=float), list(y))
    return clf
