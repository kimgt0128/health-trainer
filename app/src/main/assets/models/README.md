# On-device form-classifier models (optional, gitignored binaries)

This directory holds the OPTIONAL per-exercise TensorFlow Lite form classifiers used by
`com.healthtrainer.app.ml.TfliteFormClassifier` as a **secondary ASSIST signal**. The rule engine
(`:core`) is always primary — the app runs fully without any model here.

## Expected files

| File               | Exercise | Input → Output                          | Produced by |
|--------------------|----------|-----------------------------------------|-------------|
| `squat_form.tflite`| Squat    | `[1,12]` float32 → `[1,6]` probabilities | ml track (Colab export) |

The 12 inputs are `SquatFeatureExtractor.FEATURE_NAMES` (from `:core`), in that exact order. The 6
output classes, in order, are `FormClassifierRegistry.SQUAT_LABELS`:
`correct, shallow_squat, forward_lean, knees_caving_in, heels_off_ground, asymmetric_squat`.

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
