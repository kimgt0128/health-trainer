"""raw_to_frame: convert MediaPipe-style raw landmarks into our frame dict.
Pure post-processing — the mediapipe import lives elsewhere and is lazy."""
import sys
from collections import namedtuple

from healthtrainer_ml.landmarks import MEDIAPIPE_POSE_INDEX, raw_to_frame

FakeLm = namedtuple("FakeLm", "x y z visibility")


def _raw_33(filler=(0.0, 0.0, 0.0, 0.0)):
    raw = [FakeLm(*filler) for _ in range(33)]
    return raw


def test_maps_mediapipe_indices_to_names():
    raw = _raw_33()
    raw[MEDIAPIPE_POSE_INDEX["left_shoulder"]] = FakeLm(0.1, 0.2, 0.3, 0.9)
    raw[MEDIAPIPE_POSE_INDEX["right_hip"]] = FakeLm(0.4, 0.5, 0.6, 0.8)
    frame = raw_to_frame(raw)
    assert frame["left_shoulder"] == (0.1, 0.2, 0.3, 0.9)
    assert frame["right_hip"] == (0.4, 0.5, 0.6, 0.8)


def test_accepts_tuple_landmarks():
    raw = [(0.0, 0.0, 0.0, 0.0)] * 33
    raw[MEDIAPIPE_POSE_INDEX["left_knee"]] = (1.0, 2.0, 3.0, 0.7)
    frame = raw_to_frame(raw)
    assert frame["left_knee"] == (1.0, 2.0, 3.0, 0.7)


def test_frame_has_all_named_landmarks():
    frame = raw_to_frame(_raw_33())
    assert set(frame.keys()) == set(MEDIAPIPE_POSE_INDEX.keys())


def test_importing_landmarks_does_not_import_mediapipe():
    # the heavy dep must stay lazy
    assert "mediapipe" not in sys.modules
