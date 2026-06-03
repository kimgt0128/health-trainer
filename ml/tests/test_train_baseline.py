"""RandomForest baseline trainer (smoke): separable data must fit well."""
import numpy as np

from healthtrainer_ml.train_baseline import train_random_forest
from healthtrainer_ml.metrics import accuracy


def _separable():
    rng = np.random.default_rng(0)
    n = 50
    x0 = rng.normal(0.0, 0.5, size=(n, 4))
    x1 = rng.normal(5.0, 0.5, size=(n, 4))
    X = np.vstack([x0, x1]).tolist()
    y = [0] * n + [1] * n
    return X, y


def test_fits_separable_classes_above_90pct():
    X, y = _separable()
    model = train_random_forest(X, y, random_state=0)
    preds = model.predict(np.asarray(X, dtype=float)).tolist()
    assert accuracy(y, preds) > 0.9


def test_model_knows_both_classes():
    X, y = _separable()
    model = train_random_forest(X, y, random_state=0)
    assert sorted(model.classes_.tolist()) == [0, 1]
