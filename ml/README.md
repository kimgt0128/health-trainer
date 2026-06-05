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

## Push-up form classifier (binary, rep-level)

Same shape as squat, but **rep-level** and **binary**: one verdict per completed rep.

```text
dataset: mohamadashrafsalama/pushup        # ⚠️ schema UNVERIFIED — confirm columns in Colab
labels:  correct, incorrect
features (rep-level, ORDER = :core PushUpFeatureExtractor.FEATURE_NAMES):
  min_elbow_angle, max_elbow_angle, mean_elbow_angle, elbow_angle_range,
  min_body_line_angle, mean_body_line_angle, body_line_broken_ratio,
  visible_frame_ratio, rep_duration_ms, down_phase_ratio
```

- **Counting is NOT the model's job.** Rep counting + valid/invalid stays 100% the rule
  engine + `RepStateMachine`/`SetTracker` (`aggregateRep`). The model is an ASSIST hint
  shown only at rep end (`correct` is suppressed; only `incorrect` surfaces).
- The Kaggle dataset's exact schema (file path, columns, per-frame vs. rep-aggregated) is
  **unverified locally** (no Kaggle creds). See the `⚠️ SCHEMA UNVERIFIED` block in
  `pushup_pose_dataset.py`: confirm columns in Colab, and if the source is per-frame raw
  landmarks, add a rep-aggregation step before training.

```bash
python ml/src/train_pushup_form_classifier.py \
  --run-dir "$RUNS_DIR/pushup_form_classifier_v1"   # HGB default (--model rf for baseline)
```

Writes `pushup_form_classifier.joblib`, `labels_pushup_form.json`, `feature_config.json`,
`metrics_summary.json`. After training + TFLite export (out of scope this slice), drop
`pushup_form.tflite` into `app/src/main/assets/models/` — the app is rules-only until then
and never crashes for a missing model.

## Contract with the Android app

`configs/*.yaml` + the emitted `feature_config.json` are the single source of truth for
normalization, the landmark subset, and the angle features. The app's `:core` MUST use
identical definitions (see `ml/LESSONS.md` L4). `contract.py` validates the artifact set
(`labels_*.json`, `feature_config.json`, `metrics_summary.json`) on both sides.
