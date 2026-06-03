"""Fixed-length sequence windowing for sequence models (default 60 frames, stride 30)."""
import pytest

from healthtrainer_ml.windowing import make_windows


def test_full_windows_with_stride():
    seq = list(range(120))
    windows = make_windows(seq, length=60, stride=30)
    # starts at 0, 30, 60 -> 3 full windows (90+60=150 > 120 stops)
    assert len(windows) == 3
    assert windows[0] == list(range(0, 60))
    assert windows[1] == list(range(30, 90))
    assert windows[2] == list(range(60, 120))


def test_too_short_yields_no_windows():
    assert make_windows(list(range(50)), length=60, stride=30) == []


def test_exact_length_yields_one_window():
    seq = list(range(60))
    assert make_windows(seq, length=60, stride=30) == [seq]


def test_invalid_params_raise():
    with pytest.raises(ValueError):
        make_windows(list(range(10)), length=0, stride=1)
    with pytest.raises(ValueError):
        make_windows(list(range(10)), length=5, stride=0)
