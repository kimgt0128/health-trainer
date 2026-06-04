"""Landmark normalization — mirrors :core LandmarkNormalizer.

    hip_center   = midpoint(left_hip, right_hip)
    shoulder_w   = distance(left_shoulder, right_shoulder)
    normalized   = (landmark - hip_center) / shoulder_w

Operates on a *frame*: ``dict[name -> (x, y, z, visibility)]``. Visibility is carried
through unchanged. Raises ``ValueError`` if shoulder width is zero (un-scalable frame).
"""
from __future__ import annotations

from .geometry import distance, midpoint

_HIPS = ("left_hip", "right_hip")
_SHOULDERS = ("left_shoulder", "right_shoulder")


def normalize_frame(frame: dict) -> dict:
    for name in (*_HIPS, *_SHOULDERS):
        if name not in frame:
            raise ValueError(f"frame missing required landmark for normalization: {name}")

    left_hip = frame["left_hip"]
    right_hip = frame["right_hip"]
    hip_center = midpoint(left_hip[:3], right_hip[:3])
    shoulder_w = distance(frame["left_shoulder"][:3], frame["right_shoulder"][:3])
    if shoulder_w == 0.0:
        raise ValueError("shoulder width is zero; cannot normalize frame")

    out: dict = {}
    for name, (x, y, z, vis) in frame.items():
        out[name] = (
            (x - hip_center[0]) / shoulder_w,
            (y - hip_center[1]) / shoulder_w,
            (z - hip_center[2]) / shoulder_w,
            vis,
        )
    return out
