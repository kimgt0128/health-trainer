"""Label-map loading for the classifier artifacts (labels_exercise.json, etc.)."""
from __future__ import annotations

import json


def load_label_map(path: str) -> dict:
    """Load a ``{"0": "name", ...}`` JSON map into ``{0: "name", ...}``.

    Keys must be contiguous integers starting at 0 (the model's output indices).
    """
    with open(path, "r", encoding="utf-8") as fh:
        raw = json.load(fh)
    if not raw:
        raise ValueError("label map is empty")
    try:
        as_int = {int(k): v for k, v in raw.items()}
    except (TypeError, ValueError):
        raise ValueError("label map keys must be integers")
    expected = set(range(len(as_int)))
    if set(as_int) != expected:
        raise ValueError(
            f"label keys must be contiguous from 0; got {sorted(as_int)}"
        )
    return as_int
