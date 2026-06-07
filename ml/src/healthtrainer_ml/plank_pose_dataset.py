"""Vollkorn01 plank keypoint dataset adapter (3-class posture form classifier).

Source:
https://github.com/Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras

`fullDataFrame.csv` has an unnamed index column, 36 coordinate columns named
``"0".."35"`` (18 keypoints as ``0x,0y, 1x,1y, ... 17x,17y``), ``filename``, and
``label``. Original images were removed by the source authors for privacy. The
GitHub API reports no declared license, so training reads this data in Colab/Drive
and cites it -- the dataset is NOT vendored into this repo.

KEYPOINT CONVENTION (audited, Task 1 gate)
------------------------------------------
The 36 predicted coordinates are **OpenPose / COCO-18** output (the source used
tf-pose-estimation -- images are named ``*.humans.jpeg``). Confirmed empirically
from `fullDataFrame.csv`: per-keypoint mean y (image coords, y-down) lays out a
body head->feet -- nose/eyes/ears ~24-28, shoulders(2,5) ~41, elbows(3,6) ~44-48,
hips(8,11) ~93, knees(9,12) ~134, ankles(10,13) ~172. The plan's draft indices
(5,6,7,8,11,12,15,16) would read elbow/wrist/eye/ear coords as hips/ankles, so this
adapter uses the audited COCO-18 indices below.

FEATURE FRAME (Task 1.5 -- rotation/scale-normalized body frame)
----------------------------------------------------------------
The source coords are raw PIXELS and the source bodies are oriented head-up/feet-down
in frame, while the app's MediaPipe planks are normalized ([0,1]) and typically
horizontal. To make train and inference share ONE distribution, every offset/length
feature is computed in a **body frame** that is invariant to rotation and scale:

* body axis ``A = ankle_center - shoulder_center``; ``L = |A|`` (Euclidean
  shoulder<->ankle length) is the single SCALE unit. No pixel-unit floor.
* ``body_line_angle`` is the shoulder-hip-ankle angle (already rotation/scale free).
* hip offset is the SIGNED PERPENDICULAR distance of the hip from axis ``A``,
  normalized by ``L`` -- via the 2D cross product ``A.x*dy - A.y*dx`` (``d = hip -
  shoulder``) divided by ``L*L``. The cross product is rotation invariant; the
  ``/L^2`` makes it scale invariant.

Every feature uses only the 6 REQUIRED landmarks (L/R shoulder, hip, ankle). The
draft's elbow feature was dropped: elbows are optional in MediaPipe and missing
17-27% of the time in the source (a domain-divergent missingness that would add
drift), and elbow alignment is a separate ``PlankRule`` advisory, not one of this
model's 3 hip classes.

SIGN: for a CANONICAL horizontal plank (shoulder left, ankle right, y-down) a SAG
(hip toward the ground) yields a POSITIVE offset and a PIKE a negative one -- matching
``PlankRule`` (``hipDrop = hip.y - lineY > 0 -> HIPS_LOW``). The :core
``PlankFeatureExtractor`` (Task 2) will compute the identical formula on MediaPipe
landmarks, so ``featureNames()`` must equal ``FEATURE_COLUMNS`` here exactly.

(Residual: the perpendicular sign is a reflection pseudoscalar, so a left/right MIRROR
of the camera setup flips low<->high. Data collection should keep a consistent facing
or mirror-normalize; ``body_line_angle`` and the ``*_abs`` magnitudes are unaffected.)

Labels are renamed to app language so UI maps straight onto ``PLANK_HIPS_LOW`` /
``PLANK_HIPS_HIGH``: source ``0 too low / 1 correct / 2 too high`` ->
``hips_low / correct / hips_high``.

The deterministic, unit-tested parts (``validate`` / ``split`` / ``feature_config``)
do not touch the network. ``load_plank_dataframe`` reads the source CSV over the
network and is Colab/audit-only (``pandas`` imported lazily inside the function).
"""
from __future__ import annotations

DATASET_HANDLE = "Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras"
DATASET_URL = (
    "https://raw.githubusercontent.com/Vollkorn01/"
    "Deep-Learning-Fitness-Exercise-Correction-Keras/master/fullDataFrame.csv"
)

# 18 keypoints x (x,y) -> 36 source columns named "0".."35".
SOURCE_COLUMNS = [str(i) for i in range(36)]

# OpenPose / COCO-18 keypoint indices (audited Task 1 -- see module docstring).
# Body-part -> keypoint index. Keypoint k occupies source columns str(2k), str(2k+1).
KEYPOINT_INDICES = {
    "left_shoulder": 5,
    "right_shoulder": 2,
    "left_elbow": 6,
    "right_elbow": 3,
    "left_hip": 11,
    "right_hip": 8,
    "left_ankle": 13,
    "right_ankle": 10,
}
KEYPOINT_CONVENTION = "OpenPose/COCO-18 (tf-pose-estimation output)"

# Order MUST match :core PlankFeatureExtractor.featureNames() and feature_config.json.
# The model consumes positional features, so reordering silently corrupts inference.
# All offset/length features are body-frame, rotation+scale invariant (see module docstring).
FEATURE_COLUMNS = [
    "body_line_angle",         # shoulder-hip-ankle angle (deg); higher = straighter
    "hip_perp_offset_signed",  # signed hip perpendicular dist / L; sag(hips_low) > 0, pike < 0
    "hip_perp_offset_abs",     # |hip_perp_offset_signed|; magnitude of the break
    "hip_axial_ratio",         # hip projection along the axis / L (0=shoulder .. 1=ankle)
    "required_visible_ratio",  # fraction of the 6 required landmarks present
]

LABELS = {
    0: "hips_low",
    1: "correct",
    2: "hips_high",
}


def validate_plank_dataframe(df) -> None:
    """Require the 36 source coordinate columns + ``label``; reject out-of-range labels.

    Label membership is a SUBSET check (observed labels must be within ``{0,1,2}``),
    not equality, so a small slice that happens to contain only one class still
    validates -- only an unexpected label value is an error.
    """
    missing = [c for c in (*SOURCE_COLUMNS, "label") if c not in df.columns]
    if missing:
        raise ValueError(f"plank dataset missing columns: {missing}")

    labels = sorted({int(v) for v in df["label"].dropna().unique().tolist()})
    unexpected = [v for v in labels if v not in LABELS]
    if unexpected:
        raise ValueError(f"unexpected plank labels {unexpected}, allowed {sorted(LABELS)}")


def _xy(row, keypoint: int):
    """``(x, y)`` for COCO-18 keypoint ``keypoint`` from columns ``2k`` / ``2k+1``; ``None`` if NaN."""
    x = row[str(keypoint * 2)]
    y = row[str(keypoint * 2 + 1)]
    if x != x or y != y:  # NaN
        return None
    return float(x), float(y)


def _angle_degrees(a, b, c) -> float:
    """Interior angle at ``b`` of ``a-b-c`` in degrees (0 if a side has zero length)."""
    import math

    bax, bay = a[0] - b[0], a[1] - b[1]
    bcx, bcy = c[0] - b[0], c[1] - b[1]
    dot = bax * bcx + bay * bcy
    mag1 = math.hypot(bax, bay)
    mag2 = math.hypot(bcx, bcy)
    if mag1 == 0 or mag2 == 0:
        return 0.0
    cosv = max(-1.0, min(1.0, dot / (mag1 * mag2)))
    return math.degrees(math.acos(cosv))


def _midpoint(a, b):
    if a is None:
        return b
    if b is None:
        return a
    return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)


def _body_frame_features(shoulder, hip, ankle, visible_ratio: float) -> list[float]:
    """The 5-feature body-frame vector (rotation + scale invariant). See module docstring.

    ``shoulder``/``hip``/``ankle`` are L/R-averaged centers. Sign of
    ``hip_perp_offset_signed``: canonical horizontal plank (shoulder left, ankle right,
    y-down) -> sag(hips_low) positive.
    """
    import math

    ax, ay = ankle[0] - shoulder[0], ankle[1] - shoulder[1]  # body axis A
    length = math.hypot(ax, ay)
    if length < 1e-9:  # degenerate (shoulder == ankle); cannot define a body frame
        return [0.0] * (len(FEATURE_COLUMNS) - 1) + [visible_ratio]
    l2 = length * length

    hx, hy = hip[0] - shoulder[0], hip[1] - shoulder[1]
    # 2D cross product A x (hip-shoulder): rotation-invariant signed area.
    # /L gives signed perpendicular distance; /L^2 makes it dimensionless (scale invariant).
    hip_perp_signed = (ax * hy - ay * hx) / l2
    # hip projection along the axis, as a fraction of L (rotation/scale invariant).
    hip_axial_ratio = (hx * ax + hy * ay) / l2
    body_line_angle = _angle_degrees(shoulder, hip, ankle)

    return [
        float(body_line_angle),
        float(hip_perp_signed),
        float(abs(hip_perp_signed)),
        float(hip_axial_ratio),
        float(visible_ratio),
    ]


def _row_to_features(row) -> list[float]:
    """Map one source row to the fixed body-frame feature vector (always length 5)."""
    kp = KEYPOINT_INDICES
    left_shoulder = _xy(row, kp["left_shoulder"])
    right_shoulder = _xy(row, kp["right_shoulder"])
    left_hip = _xy(row, kp["left_hip"])
    right_hip = _xy(row, kp["right_hip"])
    left_ankle = _xy(row, kp["left_ankle"])
    right_ankle = _xy(row, kp["right_ankle"])

    required = [left_shoulder, right_shoulder, left_hip, right_hip, left_ankle, right_ankle]
    visible_ratio = sum(p is not None for p in required) / len(required)
    if visible_ratio == 0:
        return [0.0] * len(FEATURE_COLUMNS)

    shoulder = _midpoint(left_shoulder, right_shoulder)
    hip = _midpoint(left_hip, right_hip)
    ankle = _midpoint(left_ankle, right_ankle)
    if shoulder is None or hip is None or ankle is None:
        return [0.0] * (len(FEATURE_COLUMNS) - 1) + [visible_ratio]

    return _body_frame_features(shoulder, hip, ankle, visible_ratio)


def load_plank_dataframe():  # pragma: no cover - network/Colab path
    """Read the source ``fullDataFrame.csv`` (drops the unnamed index col) and validate it."""
    import pandas as pd

    df = pd.read_csv(DATASET_URL, index_col=0)
    validate_plank_dataframe(df)
    return df


def split_features_labels(df):
    """``(X, y)`` numpy arrays: ``X`` is ``(n, len(FEATURE_COLUMNS))``, ``y`` int labels."""
    import numpy as np

    validate_plank_dataframe(df)
    X = np.asarray([_row_to_features(row) for _, row in df.iterrows()], dtype=float)
    y = df["label"].to_numpy(dtype=int)
    return X, y


def feature_config() -> dict:
    return {
        "task": "plank_form_classifier",
        "source_dataset": DATASET_HANDLE,
        "source_url": DATASET_URL,
        "source_license": "none declared in GitHub API",
        "keypoint_convention": KEYPOINT_CONVENTION,
        "keypoint_indices": dict(KEYPOINT_INDICES),
        "feature_frame": "rotation+scale-normalized body frame (axis=shoulder->ankle, scale=|axis|)",
        "granularity": "frame_or_static_image",
        "features": list(FEATURE_COLUMNS),
        "labels": {str(k): v for k, v in LABELS.items()},
    }
