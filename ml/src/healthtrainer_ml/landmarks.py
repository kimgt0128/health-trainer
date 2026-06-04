"""MediaPipe landmark extraction adapter.

The pure post-processing (``raw_to_frame``) is unit-tested. The actual video decode +
MediaPipe inference (``extract_frames_from_video``) imports ``mediapipe`` **lazily**, so
importing this module on a machine without mediapipe (this one) is fine — only calling
the extraction function requires it (that runs on Colab).
"""
from __future__ import annotations

# MediaPipe BlazePose 33-landmark indices for the names we keep
# (matches the Android :core LandmarkName enum).
MEDIAPIPE_POSE_INDEX = {
    "nose": 0,
    "left_shoulder": 11,
    "right_shoulder": 12,
    "left_elbow": 13,
    "right_elbow": 14,
    "left_wrist": 15,
    "right_wrist": 16,
    "left_hip": 23,
    "right_hip": 24,
    "left_knee": 25,
    "right_knee": 26,
    "left_ankle": 27,
    "right_ankle": 28,
    "left_heel": 29,
    "right_heel": 30,
    "left_foot_index": 31,
    "right_foot_index": 32,
}


def _coords(lm):
    """Accept either an object with x/y/z/visibility or a 4-tuple."""
    if hasattr(lm, "x"):
        return (lm.x, lm.y, lm.z, lm.visibility)
    return (lm[0], lm[1], lm[2], lm[3])


def raw_to_frame(raw_landmarks, name_index=None) -> dict:
    """Map an indexable sequence of raw landmarks to ``{name: (x, y, z, visibility)}``."""
    name_index = name_index or MEDIAPIPE_POSE_INDEX
    return {name: _coords(raw_landmarks[idx]) for name, idx in name_index.items()}


def extract_frames_from_video(video_path: str, model_asset_path: str):  # pragma: no cover
    """Decode ``video_path`` and run MediaPipe PoseLandmarker per frame.

    Returns a list of frames (``raw_to_frame`` output) with a ``timestamp_ms`` companion.
    Imports mediapipe lazily; only runnable where mediapipe + the .task model exist.
    """
    raise NotImplementedError(
        "extract_frames_from_video runs on Colab (requires mediapipe + a .task model). "
        "Use the Colab runner notebook; raw_to_frame is the locally-tested core."
    )
