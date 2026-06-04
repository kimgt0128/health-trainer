"""RandomForest baseline summarizes a window into per-feature min/max/mean/std."""
import math

import pytest

from healthtrainer_ml.summarize import summarize_window, summary_vector


WINDOW = [[0.0, 10.0], [2.0, 20.0], [4.0, 30.0]]  # 3 frames, 2 features


def test_min_max_mean_per_feature():
    s = summarize_window(WINDOW)
    assert s["min"] == [0.0, 10.0]
    assert s["max"] == [4.0, 30.0]
    assert s["mean"] == [2.0, 20.0]


def test_population_std_per_feature():
    s = summarize_window(WINDOW)
    assert abs(s["std"][0] - math.sqrt(8.0 / 3.0)) < 1e-9
    assert abs(s["std"][1] - math.sqrt(200.0 / 3.0)) < 1e-9


def test_summary_vector_is_feature_major_min_max_mean_std():
    v = summary_vector(WINDOW)
    assert len(v) == 2 * 4
    # feature 0 block: min,max,mean,std
    assert v[0:3] == [0.0, 4.0, 2.0]
    assert abs(v[3] - math.sqrt(8.0 / 3.0)) < 1e-9


def test_empty_window_raises():
    with pytest.raises(ValueError):
        summarize_window([])
