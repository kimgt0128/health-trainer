"""CLI entrypoints must be importable WITHOUT pulling heavy Colab-only deps.
This guards the lazy-import contract (ml/LESSONS.md L3/L4)."""
import importlib
import sys

import pytest

CLI_MODULES = [
    "extract_landmarks",
    "build_features",
    "train_exercise_classifier",
    "train_phase_classifier",
    "train_squat_form_classifier",
    "train_squat_form_mlp",
    "train_pushup_form_classifier",
    "export_tflite",
    "evaluate",
]


@pytest.mark.parametrize("mod_name", CLI_MODULES)
def test_cli_module_imports_and_exposes_main(mod_name):
    mod = importlib.import_module(mod_name)
    assert callable(mod.main)


def test_importing_clis_does_not_import_mediapipe_or_tensorflow():
    for mod_name in CLI_MODULES:
        importlib.import_module(mod_name)
    assert "mediapipe" not in sys.modules
    assert "tensorflow" not in sys.modules


def test_main_with_no_args_errors_cleanly():
    mod = importlib.import_module("build_features")
    # argparse exits with SystemExit(2) when required args are missing
    with pytest.raises(SystemExit):
        mod.main([])
