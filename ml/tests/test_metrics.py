"""Eval metrics per docs/model-training-plan.md '평가 지표'."""
from healthtrainer_ml.metrics import (
    accuracy,
    confusion_matrix,
    macro_f1,
    off_by_one_accuracy,
)

Y_TRUE = [0, 1, 1, 0]
Y_PRED = [0, 1, 0, 0]


def test_accuracy():
    assert abs(accuracy(Y_TRUE, Y_PRED) - 0.75) < 1e-9


def test_confusion_matrix():
    cm = confusion_matrix(Y_TRUE, Y_PRED, n_classes=2)
    # rows = true, cols = pred
    assert cm == [[2, 0], [1, 1]]


def test_macro_f1():
    # class0 f1 = 0.8, class1 f1 = 2/3 -> macro = 0.7333...
    assert abs(macro_f1(Y_TRUE, Y_PRED, n_classes=2) - (0.8 + 2.0 / 3.0) / 2) < 1e-9


def test_macro_f1_handles_absent_class():
    # class 2 never appears -> its f1 is 0, no division error
    f1 = macro_f1([0, 1], [0, 1], n_classes=3)
    assert abs(f1 - (1.0 + 1.0 + 0.0) / 3) < 1e-9


def test_off_by_one_accuracy():
    # diffs 0,1,2 -> within +-1 for first two -> 2/3
    assert abs(off_by_one_accuracy([10, 10, 10], [10, 9, 8]) - 2.0 / 3.0) < 1e-9


def test_off_by_one_perfect():
    assert off_by_one_accuracy([5, 8], [6, 7]) == 1.0
