"""Evaluation metrics for the classifiers and rep counter.

- exercise/phase classification: accuracy, macro F1, confusion matrix.
- rep counting: off-by-one accuracy (|pred - true| <= 1 counts as correct), per
  docs/model-training-plan.md '반복 횟수'.

Implemented in stdlib so the metric math itself is what the tests verify (not sklearn).
"""
from __future__ import annotations


def accuracy(y_true, y_pred) -> float:
    if not y_true:
        raise ValueError("y_true is empty")
    correct = sum(1 for t, p in zip(y_true, y_pred) if t == p)
    return correct / len(y_true)


def confusion_matrix(y_true, y_pred, n_classes: int) -> list:
    cm = [[0] * n_classes for _ in range(n_classes)]
    for t, p in zip(y_true, y_pred):
        cm[t][p] += 1
    return cm


def _f1_for_class(cm, cls: int) -> float:
    tp = cm[cls][cls]
    fp = sum(cm[r][cls] for r in range(len(cm))) - tp
    fn = sum(cm[cls]) - tp
    if tp == 0:
        return 0.0
    precision = tp / (tp + fp)
    recall = tp / (tp + fn)
    if precision + recall == 0:
        return 0.0
    return 2 * precision * recall / (precision + recall)


def macro_f1(y_true, y_pred, n_classes: int) -> float:
    cm = confusion_matrix(y_true, y_pred, n_classes)
    return sum(_f1_for_class(cm, c) for c in range(n_classes)) / n_classes


def off_by_one_accuracy(true_counts, pred_counts) -> float:
    if not true_counts:
        raise ValueError("true_counts is empty")
    within = sum(1 for t, p in zip(true_counts, pred_counts) if abs(t - p) <= 1)
    return within / len(true_counts)
