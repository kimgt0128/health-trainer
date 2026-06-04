"""Validation of the model artifact contract shared with the Android app.

Mirrors docs/colab-drive-workflow.md "Artifact Contract". The app and the trainer both
depend on these shapes; a drift here silently breaks inference (ml/LESSONS.md L4), so we
fail loudly with ``ValueError``.
"""
from __future__ import annotations

_FEATURE_CONFIG_REQUIRED = (
    "sequence_length",
    "landmarks",
    "per_landmark_features",
    "angle_features",
    "normalization",
)


def validate_feature_config(cfg: dict) -> None:
    for key in _FEATURE_CONFIG_REQUIRED:
        if key not in cfg:
            raise ValueError(f"feature_config missing required key: {key}")
    if not isinstance(cfg["sequence_length"], int) or cfg["sequence_length"] <= 0:
        raise ValueError("sequence_length must be a positive int")
    for list_key in ("landmarks", "per_landmark_features", "angle_features"):
        if not isinstance(cfg[list_key], list) or not cfg[list_key]:
            raise ValueError(f"{list_key} must be a non-empty list")
    norm = cfg["normalization"]
    if not isinstance(norm, dict) or "center" not in norm or "scale" not in norm:
        raise ValueError("normalization must define 'center' and 'scale'")
    if norm["center"] != "hip_center" or norm["scale"] != "shoulder_width":
        raise ValueError(
            "normalization must be center=hip_center, scale=shoulder_width "
            "to match the app :core contract"
        )


def validate_labels(labels: dict) -> None:
    if not labels:
        raise ValueError("labels map is empty")
    try:
        keys = sorted(int(k) for k in labels)
    except (TypeError, ValueError):
        raise ValueError("label keys must be integers")
    if keys != list(range(len(keys))):
        raise ValueError(f"label keys must be contiguous from 0; got {keys}")
    for v in labels.values():
        if not isinstance(v, str) or not v:
            raise ValueError("label values must be non-empty strings")


def validate_metrics_summary(metrics: dict) -> None:
    if not metrics:
        raise ValueError("metrics summary is empty")
    for model_name, section in metrics.items():
        if not isinstance(section, dict):
            raise ValueError(f"metrics[{model_name}] must be an object")
        for metric in ("accuracy", "macro_f1"):
            if metric not in section:
                raise ValueError(f"metrics[{model_name}] missing {metric}")
            val = section[metric]
            if not isinstance(val, (int, float)) or not (0.0 <= val <= 1.0):
                raise ValueError(
                    f"metrics[{model_name}].{metric} must be in [0, 1]; got {val}"
                )
