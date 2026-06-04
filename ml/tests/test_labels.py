"""Label map loading: JSON int-keyed map, contiguous from 0."""
import json

import pytest

from healthtrainer_ml.labels import load_label_map


def test_load_valid_label_map(tmp_path):
    p = tmp_path / "labels_exercise.json"
    p.write_text(json.dumps({"0": "squat", "1": "push_up", "2": "plank", "3": "unknown"}))
    assert load_label_map(str(p)) == {0: "squat", 1: "push_up", 2: "plank", 3: "unknown"}


def test_non_contiguous_keys_raise(tmp_path):
    p = tmp_path / "labels.json"
    p.write_text(json.dumps({"0": "a", "2": "b"}))
    with pytest.raises(ValueError):
        load_label_map(str(p))


def test_not_starting_at_zero_raises(tmp_path):
    p = tmp_path / "labels.json"
    p.write_text(json.dumps({"1": "a", "2": "b"}))
    with pytest.raises(ValueError):
        load_label_map(str(p))


def test_empty_map_raises(tmp_path):
    p = tmp_path / "labels.json"
    p.write_text(json.dumps({}))
    with pytest.raises(ValueError):
        load_label_map(str(p))
