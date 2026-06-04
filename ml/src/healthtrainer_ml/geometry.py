"""Pure 3D geometry — mirrors the Android :core AngleCalculator.

A point is a 3-tuple ``(x, y, z)``. Kept dependency-free (stdlib ``math``) so it is
trivially identical to the Kotlin implementation and cheap to test.
"""
from __future__ import annotations

import math

Point = "tuple[float, float, float]"


def distance(a, b) -> float:
    """Euclidean distance between two 3D points."""
    return math.sqrt(
        (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2
    )


def midpoint(a, b):
    """Component-wise midpoint of two 3D points."""
    return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0, (a[2] + b[2]) / 2.0)


def angle_degrees(a, b, c) -> float:
    """Interior angle at vertex ``b`` formed by points a-b-c, in degrees (0..180).

    Matches :core: clamp the cosine to [-1, 1] to stay numerically safe. Degenerate
    zero-length segments return 0.0 rather than NaN.
    """
    ab = (a[0] - b[0], a[1] - b[1], a[2] - b[2])
    cb = (c[0] - b[0], c[1] - b[1], c[2] - b[2])
    ab_len = math.sqrt(ab[0] ** 2 + ab[1] ** 2 + ab[2] ** 2)
    cb_len = math.sqrt(cb[0] ** 2 + cb[1] ** 2 + cb[2] ** 2)
    if ab_len == 0.0 or cb_len == 0.0:
        return 0.0
    dot = ab[0] * cb[0] + ab[1] * cb[1] + ab[2] * cb[2]
    cosine = max(-1.0, min(1.0, dot / (ab_len * cb_len)))
    return math.degrees(math.acos(cosine))
