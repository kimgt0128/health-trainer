"""The shipped push-up form config is a rep-level TABULAR contract (not a sequence
landmark model), so it must match the code's FEATURE_COLUMNS / LABELS exactly. A drift
here would train on a different feature layout than the app :core extractor emits
(ml/LESSONS.md L4)."""
import pathlib

import yaml

from healthtrainer_ml.contract import validate_labels
from healthtrainer_ml.pushup_pose_dataset import FEATURE_COLUMNS, LABELS

CONFIG = pathlib.Path(__file__).resolve().parents[1] / "configs" / "pushup_form_classifier.yaml"


def _load():
    with open(CONFIG) as fh:
        return yaml.safe_load(fh)


def test_config_task_is_pushup_form_classifier():
    assert _load()["task"] == "pushup_form_classifier"


def test_config_features_match_code_layout_and_order():
    # Order must equal :core PushUpFeatureExtractor.FEATURE_NAMES (LESSONS.md L4).
    assert _load()["features"] == FEATURE_COLUMNS


def test_config_labels_match_code_and_are_valid():
    d = _load()
    labels = d["labels"]
    validate_labels(labels)
    assert {int(k): v for k, v in labels.items()} == LABELS
