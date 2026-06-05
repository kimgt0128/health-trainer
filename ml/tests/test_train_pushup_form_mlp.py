"""The Keras push-up form trainer must import + wire its CLI WITHOUT tensorflow.

Same lazy-import contract as test_cli_smoke / test_train_squat_form_mlp (ml/LESSONS.md L3/L4):
training itself decodes video + runs MediaPipe + TF and is Colab only, but importing the
module and constructing the arg parser must work on the local CPU suite with no tensorflow /
mediapipe installed. We also pin the :app-facing artifact contract (10-feature order / labels
[correct, incorrect]) here so any drift fails loudly.

TF-dependent smoke (model build / TFLite convert) is gated behind ``pytest.importorskip`` so
the local TF-less run still passes and Colab (which has TF) exercises it.
"""
import importlib
import sys

import pytest

MODULE = "train_pushup_form_mlp"


def test_module_imports_without_tensorflow():
    sys.modules.pop("tensorflow", None)
    sys.modules.pop("mediapipe", None)
    mod = importlib.import_module(MODULE)
    assert callable(mod.main)
    # Importing the trainer must not drag in Colab-only heavy deps.
    assert "tensorflow" not in sys.modules
    assert "mediapipe" not in sys.modules


def test_arg_parser_wired_with_defaults():
    mod = importlib.import_module(MODULE)
    parser = mod.build_arg_parser()
    args = parser.parse_args(["--run-dir", "/tmp/run"])
    assert args.run_dir == "/tmp/run"
    assert args.test_size == 0.2
    assert args.random_state == 0
    # Default head is LOGISTIC REGRESSION (empty hidden) — the safe capacity for ~54 reps.
    assert args.hidden == []
    assert args.epochs == 150
    assert args.batch_size == 16
    # Honest, leakage-free split is the DEFAULT (mirrors the sklearn push-up trainer).
    assert args.group_by_clip is True
    assert args.cv_folds == 5


def test_group_by_clip_can_be_disabled_for_comparison():
    mod = importlib.import_module(MODULE)
    args = mod.build_arg_parser().parse_args(["--run-dir", "/tmp/run", "--no-group-by-clip"])
    assert args.group_by_clip is False


def test_hidden_flag_parses_optional_small_head():
    mod = importlib.import_module(MODULE)
    # One small Dense(8) head.
    a = mod.build_arg_parser().parse_args(["--run-dir", "/tmp/run", "--hidden", "8"])
    assert a.hidden == [8]
    # Empty string stays logistic regression (no hidden layers).
    b = mod.build_arg_parser().parse_args(["--run-dir", "/tmp/run", "--hidden", ""])
    assert b.hidden == []


def test_hidden_flag_rejects_nonpositive_widths():
    mod = importlib.import_module(MODULE)
    parser = mod.build_arg_parser()
    with pytest.raises(SystemExit):
        parser.parse_args(["--run-dir", "/tmp/run", "--hidden", "0"])
    with pytest.raises(SystemExit):
        parser.parse_args(["--run-dir", "/tmp/run", "--hidden", "-4"])


def test_main_requires_run_dir():
    mod = importlib.import_module(MODULE)
    # argparse exits with SystemExit(2) when required --run-dir is missing.
    with pytest.raises(SystemExit):
        mod.build_arg_parser().parse_args([])


def test_artifact_contract_matches_core_app():
    """input [1,10] raw FEATURE_COLUMNS order; output [1,2] softmax = LABELS [correct, incorrect]."""
    from healthtrainer_ml.pushup_pose_dataset import (
        FEATURE_COLUMNS,
        LABELS,
        feature_config,
    )

    # 10 rep-level features, exact order == :core PushUpFeatureExtractor.FEATURE_NAMES.
    assert FEATURE_COLUMNS == [
        "min_elbow_angle",
        "max_elbow_angle",
        "mean_elbow_angle",
        "elbow_angle_range",
        "min_body_line_angle",
        "mean_body_line_angle",
        "body_line_broken_ratio",
        "visible_frame_ratio",
        "rep_duration_ms",
        "down_phase_ratio",
    ]
    assert len(FEATURE_COLUMNS) == 10

    cfg = feature_config()
    # feature_config pins the 10 names/order the model's Normalization layer expects.
    assert cfg["features"] == FEATURE_COLUMNS
    assert cfg["task"] == "pushup_form_classifier"
    assert cfg["granularity"] == "rep"

    # Binary labels, contiguous 0..1 in order — the same map :app indexes the softmax output by.
    assert len(LABELS) == 2
    assert [LABELS[i] for i in range(len(LABELS))] == ["correct", "incorrect"]


def test_feature_config_matches_core_feature_names_source_of_truth():
    """feature_config order must equal the :core Kotlin FEATURE_NAMES literal verbatim.

    Cross-checks the ml contract against the app :core source directly (not just the ml mirror),
    so a drift on EITHER side trips this test (ml/LESSONS.md L4: reorder = silent app corruption).
    """
    import pathlib
    import re

    from healthtrainer_ml.pushup_pose_dataset import FEATURE_COLUMNS

    core_kt = (
        pathlib.Path(__file__).resolve().parents[2]
        / "core/src/main/kotlin/com/healthtrainer/core/features/PushUpFeatureExtractor.kt"
    )
    if not core_kt.exists():  # ml/ may be vendored without :core (e.g. Colab clone) — skip then.
        pytest.skip("core PushUpFeatureExtractor.kt not present in this checkout")

    text = core_kt.read_text()
    block = text.split("val FEATURE_NAMES", 1)[1].split("listOf(", 1)[1].split(")", 1)[0]
    core_names = re.findall(r'"([^"]+)"', block)
    assert core_names == FEATURE_COLUMNS


def test_emitted_metrics_summary_shape_satisfies_contract():
    """The metrics_summary the trainer writes must satisfy contract.validate_metrics_summary.

    We don't run TF here; we assert the SHAPE the trainer emits (headline accuracy/macro_f1 at
    the section top level, in [0,1]) is exactly what the app-side contract validator requires, so
    a TF-less machine still guards the artifact contract.
    """
    from healthtrainer_ml.contract import validate_metrics_summary

    # Mirrors the dict main() writes (representative values within [0,1]).
    sample = {
        "pushup_form_classifier": {
            "model": "logreg",
            "hidden": [],
            "granularity": "rep",
            "split": "group_by_clip",
            "accuracy": 0.835,
            "macro_f1": 0.810,
            "metric_source": "grouped_cv_mean",
            "n_reps": 54,
            "n_clips": 53,
            "held_out": {"accuracy": 0.909, "macro_f1": 0.9, "n_train": 40, "n_test": 14},
            "cv": {
                "n_splits": 5,
                "accuracy_mean": 0.835,
                "accuracy_std": 0.133,
                "macro_f1_mean": 0.810,
                "macro_f1_std": 0.157,
            },
        }
    }
    # Must not raise.
    validate_metrics_summary(sample)


# --- TF-dependent smoke (Colab only; skipped on the TF-less local suite) --------------------


def test_build_pushup_model_shapes_when_tf_available():
    tf = pytest.importorskip("tensorflow")  # noqa: F841 - skip locally; Colab exercises this
    import numpy as np

    mod = importlib.import_module(MODULE)
    n_features, n_classes = 10, 2
    train = np.random.RandomState(0).rand(20, n_features).astype("float32")

    # Logistic-regression head (default): Normalization -> Dense(2, softmax).
    logreg = mod.build_pushup_model([], n_features, n_classes, train)
    assert logreg.input_shape == (None, n_features)
    assert logreg.output_shape == (None, n_classes)

    # Optional one small Dense(8) head still produces the same [*, 2] contract output.
    mlp = mod.build_pushup_model([8], n_features, n_classes, train)
    assert mlp.output_shape == (None, n_classes)


def test_keras_model_to_tflite_emits_buffer_when_tf_available():
    tf = pytest.importorskip("tensorflow")  # noqa: F841
    import numpy as np

    mod = importlib.import_module(MODULE)
    train = np.random.RandomState(0).rand(20, 10).astype("float32")
    model = mod.build_pushup_model([], 10, 2, train)
    buf = mod.keras_model_to_tflite(model, float16=False)
    assert isinstance(buf, (bytes, bytearray))
    assert len(buf) > 0
