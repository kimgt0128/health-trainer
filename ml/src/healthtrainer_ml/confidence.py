"""Landmark-confidence gating — mirrors :core / docs thresholds.

- A frame is usable only if every *required* joint has visibility >= 0.55.
- A clip is rejected if more than 30% of its frames are unusable
  (docs/model-training-plan.md "프레임 품질 기준").
"""
from __future__ import annotations

REQUIRED_VISIBILITY = 0.55
MAX_DROP_RATIO = 0.30


def frame_is_usable(frame: dict, required_names) -> bool:
    for name in required_names:
        lm = frame.get(name)
        if lm is None or lm[3] < REQUIRED_VISIBILITY:
            return False
    return True


def clip_is_usable(frames, required_names, max_drop_ratio: float = MAX_DROP_RATIO) -> bool:
    if not frames:
        return False
    dropped = sum(1 for f in frames if not frame_is_usable(f, required_names))
    return (dropped / len(frames)) <= max_drop_ratio
