"""Group-aware train/test splitting.

Rows that share a ``group`` (e.g. every rep extracted from one video clip) are highly
correlated, so a plain random split leaks: a test rep whose sibling reps are in train is
trivially classified, inflating the score. These helpers keep each group entirely on one
side. Pure stdlib (deterministic, no sklearn) so the leakage-free guarantee is unit-tested.
"""
from __future__ import annotations

import random


def _shuffled_unique_groups(groups, random_state: int):
    uniq = sorted(set(groups))
    random.Random(random_state).shuffle(uniq)
    return uniq


def group_train_test_split_indices(groups, test_size: float, random_state: int = 0):
    """Row indices ``(train, test)`` with every group kept on a single side.

    ``test_size`` is a fraction of the *groups* (not rows); at least one group is held out.
    """
    uniq = _shuffled_unique_groups(groups, random_state)
    n_test = max(1, round(len(uniq) * test_size))
    test_groups = set(uniq[:n_test])
    train = [i for i, g in enumerate(groups) if g not in test_groups]
    test = [i for i, g in enumerate(groups) if g in test_groups]
    return train, test


def group_kfold_indices(groups, n_splits: int, random_state: int = 0):
    """``n_splits`` ``(train, test)`` folds; each group is the test group in exactly one fold."""
    uniq = _shuffled_unique_groups(groups, random_state)
    n_splits = min(n_splits, len(uniq))
    fold_groups = [set(uniq[i::n_splits]) for i in range(n_splits)]
    folds = []
    for test_groups in fold_groups:
        train = [i for i, g in enumerate(groups) if g not in test_groups]
        test = [i for i, g in enumerate(groups) if g in test_groups]
        folds.append((train, test))
    return folds
