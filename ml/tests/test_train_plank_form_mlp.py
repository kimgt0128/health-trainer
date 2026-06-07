"""The Keras-MLP plank trainer must import + wire its CLI WITHOUT tensorflow.

Same lazy-import contract as test_cli_smoke.py: training itself is Colab only, but
importing the module and constructing the arg parser must work on the local CPU suite
with no tensorflow installed. The :app-facing artifact contract (feature order / labels)
is pinned here so a drift fails loudly.
"""
import importlib
import sys

import pytest

MODULE = "train_plank_form_mlp"


def test_module_imports_without_tensorflow():
    sys.modules.pop("tensorflow", None)
    mod = importlib.import_module(MODULE)
    assert callable(mod.main)
    assert "tensorflow" not in sys.modules
    assert "mediapipe" not in sys.modules


def test_arg_parser_defaults():
    mod = importlib.import_module(MODULE)
    args = mod.build_arg_parser().parse_args(["--run-dir", "/tmp/run"])
    assert args.run_dir == "/tmp/run"
    assert args.test_size == 0.2
    assert args.random_state == 0
    assert args.hidden == [16]  # default "16" parsed to widths
    assert args.epochs == 100
    assert args.cv_folds == 5


def test_hidden_flag_parses_comma_separated_widths():
    mod = importlib.import_module(MODULE)
    args = mod.build_arg_parser().parse_args(["--run-dir", "/tmp/run", "--hidden", "32,16"])
    assert args.hidden == [32, 16]


def test_hidden_flag_rejects_invalid_spec():
    mod = importlib.import_module(MODULE)
    parser = mod.build_arg_parser()
    with pytest.raises(SystemExit):
        parser.parse_args(["--run-dir", "/tmp/run", "--hidden", "0"])


def test_main_with_no_args_errors_cleanly():
    mod = importlib.import_module(MODULE)
    with pytest.raises(SystemExit):
        mod.build_arg_parser().parse_args([])


def test_artifact_contract_matches_core_app():
    """input [1,5] raw FEATURE_COLUMNS order; output [1,3] softmax = LABELS order."""
    from healthtrainer_ml.plank_pose_dataset import FEATURE_COLUMNS, LABELS, feature_config

    assert len(FEATURE_COLUMNS) == 8
    assert len(LABELS) == 3
    cfg = feature_config()
    assert cfg["features"] == FEATURE_COLUMNS
    assert cfg["task"] == "plank_form_classifier"
    # labels are contiguous 0..2 in order — same map :app indexes the softmax output by.
    assert [LABELS[i] for i in range(len(LABELS))] == ["hips_low", "correct", "hips_high"]
