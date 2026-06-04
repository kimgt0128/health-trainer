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

## Contract with the Android app

`configs/*.yaml` + the emitted `feature_config.json` are the single source of truth for
normalization, the landmark subset, and the angle features. The app's `:core` MUST use
identical definitions (see `ml/LESSONS.md` L4). `contract.py` validates the artifact set
(`labels_*.json`, `feature_config.json`, `metrics_summary.json`) on both sides.
