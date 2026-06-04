"""Sequence windowing: slice a frame sequence into fixed-length windows.

Only full-length windows are emitted (a trailing partial window is dropped), matching
the "sequence windowing" step in docs/model-training-plan.md.
"""
from __future__ import annotations


def make_windows(seq, length: int = 60, stride: int = 30) -> list:
    if length <= 0:
        raise ValueError("length must be positive")
    if stride <= 0:
        raise ValueError("stride must be positive")
    windows = []
    start = 0
    while start + length <= len(seq):
        windows.append(list(seq[start:start + length]))
        start += stride
    return windows
