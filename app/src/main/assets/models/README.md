# On-device form-classifier models (optional, gitignored binaries)

This directory holds the OPTIONAL per-exercise TensorFlow Lite form classifiers used by
`com.healthtrainer.app.ml.TfliteFormClassifier` as a **secondary ASSIST signal**. The rule engine
(`:core`) is always primary — the app runs fully without any model here.

## Expected files

| File                | Exercise | Level              | Input → Output                           | Produced by |
|---------------------|----------|--------------------|------------------------------------------|-------------|
| `squat_form.tflite` | Squat    | frame              | `[1,12]` float32 → `[1,6]` probabilities | ml track (Colab export) |
| `pushup_form.tflite`| Push-up  | rep                | `[1,10]` float32 → `[1,2]` probabilities | ml track (Colab export) |
| `plank_form.tflite` | Plank    | frame (throttled)  | `[1,8]` float32 → `[1,3]` probabilities  | ml track (Colab export) |

The squat model is **frame-level**: its 12 inputs are `SquatFeatureExtractor.FEATURE_NAMES` (from
`:core`), in that exact order. The 6 output classes, in order, are `FormClassifierRegistry.SQUAT_LABELS`:
`correct, shallow_squat, forward_lean, knees_caving_in, heels_off_ground, asymmetric_squat`.

The push-up model is **rep-level** (scored once per completed rep, not per frame): its 10 inputs are
`PushUpFeatureExtractor.FEATURE_NAMES` (from `:core`), in that exact order — `min_elbow_angle,
max_elbow_angle, mean_elbow_angle, elbow_angle_range, min_body_line_angle, mean_body_line_angle,
body_line_broken_ratio, visible_frame_ratio, rep_duration_ms, down_phase_ratio`. The 2 output classes,
in order, are `FormClassifierRegistry.PUSH_UP_LABELS`: `correct, incorrect`.

The plank model is a **hold assist**: plank is a static hold and closes no reps, so the app classifies
the live frame at most once per `FramePipeline.HOLD_ASSIST_INTERVAL_MS` (750 ms) while the hold set is
active. Its 8 inputs are `PlankFeatureExtractor.FEATURE_NAMES` (from `:core`), in that exact order —
`body_line_angle, knee_line_angle, hip_perp_offset_signed, hip_perp_offset_abs, hip_axial_ratio, knee_perp_offset_signed, knee_axial_ratio, required_visible_ratio`
— a rotation/scale-normalized **body frame** (shoulder→ankle axis), so it matches the Vollkorn training
features regardless of pixel-vs-MediaPipe scale or camera tilt. The 3 output classes, in order, are
`FormClassifierRegistry.PLANK_LABELS`: `hips_low, correct, hips_high`. `correct` is suppressed by the
fusion gate; `hips_low` / `hips_high` surface as the soft "확인 필요" hint.

All three exercises are already registered in `FormClassifierRegistry.REGISTRY` — only the `.tflite`
binary needs dropping here to enable the assist hint (the app runs rules-only until then).

## These are gitignored

`*.tflite` here is a large binary dropped out-of-band by the ml track; it is **not** checked in
(see `.gitignore`). On a fresh checkout the file is absent and `TfliteFormClassifier` degrades to
**inert** (its `classify()` returns `null` and the app defers to the rules) — no crash. Drop the
exported `.tflite` into this folder to enable the assist signal.

## Adding a new exercise's model

1. Export `<exercise>_form.tflite` from the ml track and place it here.
2. Add one `register(...)`-style entry in `FormClassifierRegistry.REGISTRY` with the `:core`
   feature extractor, the asset name, and the label list (in the model's output order).

No other `:app` code changes.
