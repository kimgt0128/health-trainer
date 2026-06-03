"""The shipped YAML configs must satisfy the artifact contract + match the code defaults."""
import pathlib

import yaml

from healthtrainer_ml.contract import validate_feature_config, validate_labels
from healthtrainer_ml.features import DEFAULT_LANDMARKS, ANGLE_FEATURES

CONFIG_DIR = pathlib.Path(__file__).resolve().parents[1] / "configs"
CONFIGS = ["exercise_classifier.yaml", "phase_classifier.yaml"]


def _load(name):
    with open(CONFIG_DIR / name) as fh:
        return yaml.safe_load(fh)


def test_configs_satisfy_feature_contract():
    for name in CONFIGS:
        validate_feature_config(_load(name))


def test_configs_have_valid_label_maps():
    for name in CONFIGS:
        validate_labels(_load(name)["labels"])


def test_configs_match_code_feature_layout():
    # The config landmark + angle layout must equal what features.py emits,
    # or training features won't match app inference (LESSONS.md L4).
    for name in CONFIGS:
        d = _load(name)
        assert d["landmarks"] == DEFAULT_LANDMARKS
        assert d["angle_features"] == ANGLE_FEATURES
