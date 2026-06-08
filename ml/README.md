# `ml/` — Model-Training Track

Parallel, **optional** track described in `docs/model-training-plan.md` and
`docs/colab-drive-workflow.md`. The Android MVP ships with rule-based scoring and does
not depend on this; a model is merged only if it clears the integration thresholds in
the workflow doc.

## Two execution environments

| Where | Runs | Deps |
|-------|------|------|
| **Local (this machine)** | deterministic feature engineering + RandomForest baseline + all unit tests | `requirements-dev.txt` (numpy, scikit-learn, pyyaml, pytest) — **no GPU, no mediapipe/tensorflow** |
| **Colab** | MediaPipe landmark extraction, LSTM/1D-CNN training, TFLite export | `requirements-colab.txt` |

Heavy libraries are imported **lazily** so the local test suite never needs them.

## Local setup

```bash
cd ml
uv venv --python 3.11
uv pip install -r requirements-dev.txt
.venv/bin/pytest            # all tests, CPU only
```

## Pipeline (matches the docs)

```
mp4 → extract_landmarks → build_features (normalize + angles + windowing)
    → train_exercise_classifier / train_phase_classifier → evaluate → export_tflite
```

Local CLI entrypoints live in `src/`; the tested core lives in `src/healthtrainer_ml/`.
Colab orchestration is `notebooks/health_trainer_training_colab.ipynb` (a thin runner
that calls the same CLI scripts against the Drive `health_training/` workspace).

## First real-data training target

Use KaggleHub from Colab to pull the tabular squat-form dataset directly:

```text
dataset: thashmiladewmini/squat-exercise-pose-dataset
file: squat_dataset/squat_features_augmented.csv
labels: correct, shallow_squat, forward_lean, knees_caving_in, heels_off_ground, asymmetric_squat
```

This dataset already contains MediaPipe-derived pose features, so it does **not** use
`extract_landmarks.py`. Train it with:

```bash
python ml/src/train_squat_form_classifier.py \
  --run-dir "$RUNS_DIR/squat_form_classifier_rf_v1"
```

The script writes `squat_form_classifier.joblib`, `labels_squat_form.json`,
`feature_config.json`, and `metrics_summary.json` into the Drive run directory.

## Push-up form classifier (binary: correct / incorrect)

The Kaggle dataset `mohamadashrafsalama/pushup` turned out to be **raw videos**
(`Correct sequence/*.mp4`, `Wrong sequence/*.mp4`), not a feature CSV — so training runs
MediaPipe per frame -> angles -> features, on **Colab**. Both granularities share the same 10
features (== `:core` `PushUpFeatureExtractor.FEATURE_NAMES`):

```text
min_elbow_angle, max_elbow_angle, mean_elbow_angle, elbow_angle_range,
min_body_line_angle, mean_body_line_angle, body_line_broken_ratio,
visible_frame_ratio, rep_duration_ms, down_phase_ratio
```

- **`--granularity rep`** (default, app-aligned): segment each video into reps (mirrors
  `RepStateMachine`), one row per rep — the same unit the app feeds at inference.
- **`--granularity clip`**: one row per whole video — matches this dataset's clip-level labels
  and uses ALL clips. A comparison experiment, not the in-app unit.

**Counting is NOT the model's job** — rep counting + valid/invalid stays the rule engine +
`RepStateMachine`/`SetTracker`. The model is an ASSIST hint at rep end (`correct` is suppressed).

### Results (group-by-clip split + grouped 5-fold CV — leakage-free)

| experiment | unit | n | CV accuracy | CV macro-F1 |
|---|---|--:|--:|--:|
| rep-level (ships to app) | rep | 54 | 0.835 +/- 0.133 | 0.810 +/- 0.157 |
| clip-level (analysis) | clip | 100 | **0.860 +/- 0.037** | **0.858 +/- 0.038** |

clip-level wins on this dataset (more data + matches the clip labels -> far lower variance), but
**rep-level is what ships to the app** (it matches rep-level inference). Full write-up + the
honest methodology (we caught a fake 100% from group leakage): `docs/pushup-form-results.md`.

```bash
# rep-level baseline (app):
python ml/src/train_pushup_form_classifier.py --run-dir "$RUNS_DIR/pushup_form_classifier_v1"
# clip-level experiment:
python ml/src/train_pushup_form_classifier.py --run-dir "$RUNS_DIR/pushup_form_classifier_clip_v1" --granularity clip
```

Each writes `pushup_form_classifier.joblib`, `labels_pushup_form.json`, `feature_config.json`,
`metrics_summary.json`. TFLite export + app integration are out of scope (the app stays
rules-only until a `pushup_form.tflite` is dropped into `app/src/main/assets/models/`, and never
crashes for a missing model).


## Plank form classifier (3-class: hips_low / correct / hips_high)

Source: [`Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras`](https://github.com/Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras)
— `fullDataFrame.csv`, 6922 frames of **OpenPose/COCO-18** keypoints labeled `0 too low / 1 correct /
2 too high` (mapped to `hips_low / correct / hips_high`). GitHub API reports **no license**, so the
data is NOT vendored — Colab/Drive reads it at train time and cites the source.

Plank is a static HOLD, so the model is a frame-level ASSIST only (the rule engine owns everything).
The source is raw PIXEL coords and its bodies sit differently in-frame from the app's MediaPipe planks,
so the 8 features are computed in a **rotation/scale-normalized body frame** (shoulder->ankle axis,
scale = axis length) — identical to `:core` `PlankFeatureExtractor.FEATURE_NAMES`:

```text
body_line_angle, knee_line_angle, hip_perp_offset_signed, hip_perp_offset_abs,
hip_axial_ratio, knee_perp_offset_signed, knee_axial_ratio, required_visible_ratio
```

The source label was generated from the shoulder-hip-KNEE back angle, so `knee_line_angle` and the
knee body-frame offsets are included alongside the ankle features (an ankle-only 5-feature set only
reached ~0.57 macro-F1; +knee recovers it to ~0.76-0.79 local HGB CV).

`hip_perp_offset_signed` is the hip's signed perpendicular distance to the body axis / L; a canonical
horizontal sag (`hips_low`) is **positive** (matches `PlankRule`). On the real data this removed the
drift the old image-y formula had: offsets went from pixel-scale (-410..1642, sag sign inverted) to
dimensionless (-0.21..0.24, sag = +0.036), so train and app inference share one distribution.

Training is a small Keras MLP on Colab (class weights for the ~10% `hips_low` minority + stratified
5-fold CV):

```bash
python ml/src/train_plank_form_mlp.py \
  --run-dir "$RUNS_DIR/plank_form_classifier_v1" --test-size 0.2 --random-state 42 --hidden 16 --epochs 100
```

Writes `plank_form.tflite`, `labels_plank_form.json`, `feature_config.json`, `metrics_summary.json`.
The app stays rules-only until `plank_form.tflite` is dropped into `app/src/main/assets/models/`
(never crashes for a missing model).

## Contract with the Android app

`configs/*.yaml` + the emitted `feature_config.json` are the single source of truth for
normalization, the landmark subset, and the angle features. The app's `:core` MUST use
identical definitions (see `ml/LESSONS.md` L4). `contract.py` validates the artifact set
(`labels_*.json`, `feature_config.json`, `metrics_summary.json`) on both sides.
