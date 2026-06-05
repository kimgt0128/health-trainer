"""The Keras-MLP squat trainer must import + wire its CLI WITHOUT tensorflow.

Same lazy-import contract as test_cli_smoke.py (ml/LESSONS.md L3/L4): training itself
is Colab only, but importing the module and constructing the arg parser must work on the
local CPU suite with no tensorflow installed. We also pin the :app-facing artifact
contract (feature order / labels) here so a drift fails loudly.
"""
import importlib
import sys

import pytest

MODULE = "train_squat_form_mlp"


def test_module_imports_without_tensorflow():
    sys.modules.pop("tensorflow", None)
    mod = importlib.import_module(MODULE)
    assert callable(mod.main)
    # Importing the trainer must not drag in Colab-only heavy deps.
    assert "tensorflow" not in sys.modules
    assert "mediapipe" not in sys.modules


def test_arg_parser_wired_with_mlp_options_and_defaults():
    mod = importlib.import_module(MODULE)
    parser = mod.build_arg_parser()
    # Mirrors train_squat_form_classifier.py defaults + the MLP-specific knobs.
    args = parser.parse_args(["--run-dir", "/tmp/run"])
    assert args.run_dir == "/tmp/run"
    assert args.test_size == 0.2
    assert args.random_state == 0
    assert args.hidden == [64, 32]  # default "64,32" parsed to widths
    assert args.epochs == 200


def test_hidden_flag_parses_comma_separated_widths():
    mod = importlib.import_module(MODULE)
    args = mod.build_arg_parser().parse_args(
        ["--run-dir", "/tmp/run", "--hidden", "32,16"]
    )
    assert args.hidden == [32, 16]


def test_hidden_flag_rejects_invalid_spec():
    mod = importlib.import_module(MODULE)
    parser = mod.build_arg_parser()
    with pytest.raises(SystemExit):
        parser.parse_args(["--run-dir", "/tmp/run", "--hidden", "0"])


def test_main_with_no_args_errors_cleanly():
    mod = importlib.import_module(MODULE)
    # argparse exits with SystemExit(2) when required --run-dir is missing.
    with pytest.raises(SystemExit):
        mod.build_arg_parser().parse_args([])


def test_artifact_contract_matches_core_app():
    """input [1,12] raw FEATURE_COLUMNS order; output [1,6] softmax = LABELS order."""
    from healthtrainer_ml.squat_pose_dataset import (
        FEATURE_COLUMNS,
        LABELS,
        feature_config,
    )

    assert len(FEATURE_COLUMNS) == 12
    assert len(LABELS) == 6
    cfg = feature_config()
    # feature_config pins the 12 names/order the model's Normalization layer expects.
    assert cfg["features"] == FEATURE_COLUMNS
    assert cfg["task"] == "squat_form_classifier"
    # labels are contiguous 0..5 in order — same map :app indexes the softmax output by.
    assert [LABELS[i] for i in range(len(LABELS))] == [
        "correct",
        "shallow_squat",
        "forward_lean",
        "knees_caving_in",
        "heels_off_ground",
        "asymmetric_squat",
    ]
