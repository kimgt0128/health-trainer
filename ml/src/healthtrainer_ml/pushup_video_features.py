"""Push-up *rep-level* feature pipeline from raw video.

The Kaggle ``mohamadashrafsalama/pushup`` dataset is raw video (``Correct sequence/*.mp4``,
``Wrong sequence/*.mp4``) — each clip is a multi-rep push-up SEQUENCE, labelled by folder.
To train the *assist* model on the SAME distribution the app infers on, we reproduce the
app pipeline end-to-end here:

  video -> per-frame {elbow, body_line, visible, ts_ms}   (extract_pushup_frames, Colab)
        -> reps (list of frame lists)                      (segment_reps, pure)
        -> 10-feature rep row                              (rep_features, pure)

``segment_reps`` mirrors :core ``RepStateMachine`` (TOP -> descent -> TOP) and
``rep_features`` mirrors :core ``PushUpFeatureExtractor`` formula-for-formula, so a rep row
emitted here is numerically the row the app builds at inference time. Drift between the two
silently corrupts inference (ml/LESSONS.md L4) — the thresholds below are pinned to
``PushUpRule`` and asserted in ``test_pushup_video_features``.

The pure parts (``segment_reps`` / ``rep_features``) import nothing heavy and are unit-tested
on this machine. ``extract_pushup_frames`` / ``build_pushup_dataframe`` import cv2 / mediapipe
**lazily** (inside the function) so importing this module never pulls Colab-only deps
(``test_cli_smoke`` / lazy-import contract, ml/LESSONS.md L3/L4).
"""
from __future__ import annotations

# --- Thresholds: IDENTICAL to com.healthtrainer.core.exercise.PushUpRule -------------------
# (PushUpRule과 동일 — drift = silent inference corruption, ml/LESSONS.md L4.)
TOP_MIN_ELBOW_ANGLE = 155.0        # PushUpRule.TOP_MIN_ELBOW_ANGLE — arms extended (TOP).
REP_DESCENT_MAX_ELBOW_ANGLE = 130.0  # PushUpRule.REP_DESCENT_MAX_ELBOW_ANGLE — descent opens rep.
BODY_LINE_MIN_ANGLE = 160.0        # PushUpRule.BODY_LINE_MIN_ANGLE — body-line broken below this.
BOTTOM_MIN_ELBOW_ANGLE = 70.0      # PushUpRule.BOTTOM_MIN_ELBOW_ANGLE — good-form bottom band low.
BOTTOM_MAX_ELBOW_ANGLE = 100.0     # PushUpRule.BOTTOM_MAX_ELBOW_ANGLE — good-form bottom band high.
MIN_VISIBILITY = 0.55              # PushUpRule.MIN_VISIBILITY — joint trust gate (extraction).


def segment_reps(frames):
    """Split a frame stream into reps, mirroring :core ``RepStateMachine``.

    Each frame is a dict ``{"elbow": float|None, "body_line": float|None, "visible": bool,
    "ts_ms": int}``. State machine (starts at TOP):
      - TOP: a frame with ``elbow < REP_DESCENT_MAX_ELBOW_ANGLE`` OPENS a rep — that frame is
        the first buffered frame.
      - DOWN: every frame is buffered; the first frame with ``elbow >= TOP_MIN_ELBOW_ANGLE``
        CLOSES the rep (it is the last buffered frame). Then back to TOP.

    Low-confidence frames (``elbow is None``) mid-rep are still buffered and neither split nor
    close a rep (they match neither the descent nor the TOP predicate). An in-progress rep at
    end of stream (never closed) is NOT emitted. Returns ``list[list[frame dict]]``.
    """
    reps = []
    buffer = []
    in_rep = False

    for frame in frames:
        elbow = frame.get("elbow")
        if not in_rep:
            if elbow is not None and elbow < REP_DESCENT_MAX_ELBOW_ANGLE:
                in_rep = True
                buffer = [frame]
        else:
            buffer.append(frame)
            if elbow is not None and elbow >= TOP_MIN_ELBOW_ANGLE:
                reps.append(buffer)
                buffer = []
                in_rep = False

    return reps


def rep_features(rep_frames):
    """Aggregate one rep's frames into the 10-feature row, mirroring :core
    ``PushUpFeatureExtractor.extract`` (NAMES + ORDER == ``FEATURE_COLUMNS``).

    Returns ``list[float]`` of length 10, or ``None`` when the rep carries no elbow sample at
    all (every requested elbow joint occluded across the rep) — the model can't run, so the
    app defers to the rule engine.

    Formulas (PushUpFeatureExtractor와 동일):
      - min/max/mean elbow, range over elbow-bearing frames.
      - body-line min/mean: 0.0 when there is no body-line sample (elbows still present).
      - body_line_broken_ratio = (#frames body_line < BODY_LINE_MIN_ANGLE) / (#body-line-bearing
        frames); 0.0 when no body-line samples. Denominator is the VISIBLE (body-line-bearing)
        frames so occluded frames don't dilute it.
      - visible_frame_ratio = (#elbow-bearing frames) / (#all frames).
      - rep_duration_ms = last ts_ms - first ts_ms.
      - down_phase_ratio = (#frames elbow in [BOTTOM_MIN, BOTTOM_MAX]) / (#all frames).
    """
    elbows = [f["elbow"] for f in rep_frames if f.get("elbow") is not None]
    # Gate: no elbow samples -> every requested joint occluded -> model can't run.
    if not elbows:
        return None

    body_lines = [f["body_line"] for f in rep_frames if f.get("body_line") is not None]
    total = len(rep_frames)

    min_elbow = min(elbows)
    max_elbow = max(elbows)
    mean_elbow = sum(elbows) / len(elbows)
    elbow_range = max_elbow - min_elbow

    if body_lines:
        min_body_line = min(body_lines)
        mean_body_line = sum(body_lines) / len(body_lines)
        broken = sum(1 for bl in body_lines if bl < BODY_LINE_MIN_ANGLE)
        body_line_broken_ratio = broken / len(body_lines)
    else:
        min_body_line = 0.0
        mean_body_line = 0.0
        body_line_broken_ratio = 0.0

    visible_frame_ratio = len(elbows) / total if total else 0.0

    rep_duration_ms = float(rep_frames[-1]["ts_ms"] - rep_frames[0]["ts_ms"])

    bottom_frames = sum(
        1
        for e in elbows
        if BOTTOM_MIN_ELBOW_ANGLE <= e <= BOTTOM_MAX_ELBOW_ANGLE
    )
    down_phase_ratio = bottom_frames / total if total else 0.0

    # ORDER == FEATURE_COLUMNS == :core PushUpFeatureExtractor.FEATURE_NAMES.
    return [
        float(min_elbow),
        float(max_elbow),
        float(mean_elbow),
        float(elbow_range),
        float(min_body_line),
        float(mean_body_line),
        float(body_line_broken_ratio),
        float(visible_frame_ratio),
        float(rep_duration_ms),
        float(down_phase_ratio),
    ]


# ---------------------------------------------------------------------------------------------
# Colab-only video extraction. Heavy deps (cv2, mediapipe) imported LAZILY inside the function
# so importing this module on a mediapipe-less machine is fine (lazy-import contract, L3/L4).
# ---------------------------------------------------------------------------------------------
_ELBOW_TRIPLES = (
    ("left_shoulder", "left_elbow", "left_wrist"),
    ("right_shoulder", "right_elbow", "right_wrist"),
)
_BODY_LINE_TRIPLES = (
    ("left_shoulder", "left_hip", "left_ankle"),
    ("right_shoulder", "right_hip", "right_ankle"),
)


def _averaged_angle(points, triples):
    """Average the a-b-c angle over the sides whose 3 joints are all present (visibility-gated
    upstream). Mirrors PushUpRule.averagedAngle: None when no side is fully visible."""
    from healthtrainer_ml.geometry import angle_degrees

    angles = []
    for a, b, c in triples:
        pa, pb, pc = points.get(a), points.get(b), points.get(c)
        if pa is None or pb is None or pc is None:
            continue
        angles.append(angle_degrees(pa, pb, pc))
    if not angles:
        return None
    return sum(angles) / len(angles)


def extract_pushup_frames(video_path, task_model_path):  # pragma: no cover
    """Decode ``video_path`` and run MediaPipe PoseLandmarker (IMAGE mode) per frame, producing
    the per-frame dicts ``segment_reps`` consumes.

    Returns ``list[{"elbow": float|None, "body_line": float|None, "visible": bool,
    "ts_ms": int}]``. For each frame the elbow (avg L/R shoulder-elbow-wrist) and body-line
    (avg L/R shoulder-hip-ankle) angles are computed from landmarks whose ``visibility >=
    MIN_VISIBILITY`` (PushUpRule.MIN_VISIBILITY == 0.55); a joint below the gate is dropped so
    its side's angle is unavailable, exactly like :core. ``ts_ms`` comes from the frame index
    and the clip FPS. Colab-only: imports cv2 + mediapipe lazily.
    """
    import cv2  # lazy: Colab-only
    import mediapipe as mp  # lazy: Colab-only
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision as mp_vision

    from healthtrainer_ml.landmarks import MEDIAPIPE_POSE_INDEX

    base_options = mp_python.BaseOptions(model_asset_path=task_model_path)
    options = mp_vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=mp_vision.RunningMode.IMAGE,
    )
    landmarker = mp_vision.PoseLandmarker.create_from_options(options)

    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    frames = []
    idx = 0
    try:
        while True:
            ok, bgr = cap.read()
            if not ok:
                break
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            result = landmarker.detect(mp_image)
            ts_ms = int(round(idx * 1000.0 / fps))

            if not result.pose_landmarks:
                frames.append({"elbow": None, "body_line": None, "visible": False, "ts_ms": ts_ms})
                idx += 1
                continue

            lms = result.pose_landmarks[0]
            # visibility gate (>= MIN_VISIBILITY), mirroring :core visiblePoint.
            points = {}
            for name, mp_idx in MEDIAPIPE_POSE_INDEX.items():
                lm = lms[mp_idx]
                if getattr(lm, "visibility", 0.0) >= MIN_VISIBILITY:
                    points[name] = (lm.x, lm.y, lm.z)

            elbow = _averaged_angle(points, _ELBOW_TRIPLES)
            body_line = _averaged_angle(points, _BODY_LINE_TRIPLES)
            frames.append(
                {
                    "elbow": elbow,
                    "body_line": body_line,
                    "visible": elbow is not None,
                    "ts_ms": ts_ms,
                }
            )
            idx += 1
    finally:
        cap.release()

    return frames


def build_pushup_dataframe(dataset_root, task_model_path):  # pragma: no cover
    """Build the rep-level training table from the raw-video dataset.

    Layout (Kaggle ``mohamadashrafsalama/pushup``):
      ``Correct sequence/*.mp4`` -> label 0 (correct)
      ``Wrong sequence/*.mp4``   -> label 1 (incorrect)

    For each clip: extract per-frame angles -> ``segment_reps`` -> ``rep_features`` -> ONE ROW
    PER REP (``FEATURE_COLUMNS`` + ``label`` + provenance ``clip``/``rep_index``). A clip with
    several reps yields several rows; reps that gate to ``None`` (no elbow sample) are skipped.

    Returns a pandas DataFrame validated against the :core feature contract. Colab-only
    (calls ``extract_pushup_frames`` -> cv2 + mediapipe); pandas imported lazily.
    """
    import glob
    import os

    import pandas as pd

    from healthtrainer_ml.pushup_pose_dataset import (
        FEATURE_COLUMNS,
        validate_pushup_dataframe,
    )

    folder_labels = [
        ("Correct sequence", 0),
        ("Wrong sequence", 1),
    ]

    all_clips = []
    for folder, label in folder_labels:
        clip_dir = os.path.join(dataset_root, folder)
        for clip in sorted(glob.glob(os.path.join(clip_dir, "*.mp4"))):
            all_clips.append((folder, label, clip))

    total = len(all_clips)
    rows = []
    zero_rep_clips = 0
    failed_clips = 0
    print(f"[pushup] extracting features from {total} clips (MediaPipe per frame) ...", flush=True)
    for i, (folder, label, clip) in enumerate(all_clips, 1):
        name = os.path.basename(clip)
        try:
            per_frame = extract_pushup_frames(clip, task_model_path)
        except Exception as e:  # one unreadable clip must not kill the whole run.
            failed_clips += 1
            print(f"  [{i}/{total}] {folder}/{name} -> EXTRACT FAILED ({e})", flush=True)
            continue
        reps = segment_reps(per_frame)
        n_rows = 0
        for rep_index, rep in enumerate(reps):
            feats = rep_features(rep)
            if feats is None:
                continue  # rep had no usable elbow sample -> not a model row.
            row = dict(zip(FEATURE_COLUMNS, feats))
            row["label"] = label
            row["clip"] = name
            row["rep_index"] = rep_index
            rows.append(row)
            n_rows += 1
        if n_rows == 0:
            zero_rep_clips += 1
        print(f"  [{i}/{total}] {folder}/{name} -> {n_rows} reps ({len(per_frame)} frames)", flush=True)

    print(
        f"[pushup] done: {len(rows)} rep rows from "
        f"{total - failed_clips - zero_rep_clips}/{total} clips "
        f"({zero_rep_clips} yielded 0 reps, {failed_clips} extract-failed)",
        flush=True,
    )

    df = pd.DataFrame(rows, columns=[*FEATURE_COLUMNS, "label", "clip", "rep_index"])
    validate_pushup_dataframe(df)
    return df
