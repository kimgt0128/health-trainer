"""Group-aware splits: all rows of a group (e.g. all reps of one video clip) stay on the
same side, so the model can't cheat on sibling rows (group leakage)."""
from healthtrainer_ml.grouped_split import (
    group_kfold_indices,
    group_train_test_split_indices,
)


def test_split_keeps_each_group_on_one_side():
    groups = ["a", "a", "b", "b", "c", "c", "d", "d"]
    tr, te = group_train_test_split_indices(groups, test_size=0.5, random_state=0)
    tr_g = {groups[i] for i in tr}
    te_g = {groups[i] for i in te}
    assert tr_g.isdisjoint(te_g)                 # no clip in both splits = no leakage
    assert tr_g | te_g == set(groups)            # every clip placed
    assert sorted(tr + te) == list(range(len(groups)))  # every row placed once
    assert not (set(tr) & set(te))


def test_split_test_fraction_is_over_groups_not_rows():
    groups = [f"c{i // 2}" for i in range(20)]   # 10 groups, 2 rows each
    _, te = group_train_test_split_indices(groups, test_size=0.2, random_state=0)
    assert len({groups[i] for i in te}) == 2     # 20% of 10 groups


def test_split_is_deterministic_for_a_seed():
    g = list("aabbccddee")
    assert group_train_test_split_indices(g, 0.4, 42) == group_train_test_split_indices(g, 0.4, 42)


def test_kfold_tests_each_group_exactly_once_no_overlap():
    groups = [f"c{i}" for i in range(10) for _ in range(2)]   # 10 groups x2 rows
    folds = group_kfold_indices(groups, n_splits=5, random_state=0)
    assert len(folds) == 5
    tested = []
    for tr, te in folds:
        tr_g = {groups[i] for i in tr}
        te_g = {groups[i] for i in te}
        assert tr_g.isdisjoint(te_g)             # group leakage-free per fold
        assert not (set(tr) & set(te))
        assert set(tr) | set(te) == set(range(len(groups)))
        tested.extend(te_g)
    assert sorted(tested) == sorted(set(groups))  # each group is a test group once
