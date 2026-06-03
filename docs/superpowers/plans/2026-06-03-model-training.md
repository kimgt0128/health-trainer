# Model Training Track Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development for every code module (RED→GREEN→REFACTOR). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the parallel **model-training track** (`ml/`) described in `docs/model-training-plan.md` + `docs/colab-drive-workflow.md`: a pure-Python, CPU-testable feature-engineering core that mirrors the app's `:core` normalization/angle contract, plus baseline (RandomForest) training, plus the Colab/Drive execution scaffolding (MCP servers + agents).

**Architecture:** The heavy, GPU/dataset-bound parts (MediaPipe landmark extraction, TensorFlow sequence models, TFLite export) run on **Colab**. The deterministic parts (normalization, angle features, windowing, summary features, confidence filtering, label/artifact contracts, metrics) are **pure Python**, run locally on CPU, and are developed strictly TDD. These pure parts are the source of truth for the `feature_config.json` contract that BOTH the model and the Android app must agree on. Heavy libs are imported lazily/guarded so the local test suite never needs `mediapipe`/`tensorflow`.

**Tech Stack:** Python 3.11 (uv-managed venv), numpy, scikit-learn, PyYAML, pytest (local CPU). Colab-only: mediapipe, tensorflow. MCP: Google official `colab-mcp` (browser-proxy, semi-manual) + Datalayer `jupyter-mcp-server` (local kernel, CPU verification), both project-scoped via `.mcp.json`.

**Isolation:** All work happens on the `model-training` git worktree (branched from `main`), independent of the parallel `:core`/`:app` MVP slices.

---

## Reality constraints (read every session)

- This machine: **no GPU, no Android SDK.** `mediapipe`/`tensorflow` are NOT installed locally and may not build on this arch — keep them out of the local test path (guarded imports).
- The model's normalization + angle definitions MUST byte-for-byte match the app `:core` contract (`health-trainer-conventions`): hip-center translation, shoulder-width scale, angle tolerance `0.5°`, required-joint `visibility < 0.55` → frame dropped. A mismatch silently corrupts inference. This is the highest-value correctness target.
- Colab execution via MCP is **semi-manual** (open a logged-in Colab tab + click "Connect"); free-tier headless is unavailable. We configure it but cannot fully verify GPU execution without the user's browser.

## File structure

```
ml/
  README.md                       # how the track works, local vs Colab
  LESSONS.md                       # compound-engineering mistake log (lives at repo root? -> ml/)
  pyproject.toml                   # package metadata + pytest config
  requirements-dev.txt             # numpy, scikit-learn, pyyaml, pytest (local CPU)
  requirements-colab.txt           # mediapipe, tensorflow, ... (Colab only)
  configs/
    exercise_classifier.yaml
    phase_classifier.yaml
  src/healthtrainer_ml/
    __init__.py
    geometry.py        # angle_degrees, distance, midpoint  (mirrors :core AngleCalculator)
    normalize.py       # hip-center + shoulder-width normalization (mirrors :core LandmarkNormalizer)
    confidence.py      # visibility<0.55 frame drop, >30% clip drop
    features.py        # per-frame landmark+angle feature vector per feature_config
    windowing.py       # fixed-length (60) sequence windows + stride
    summarize.py       # min/max/mean/std summary features for RandomForest baseline
    labels.py          # label map load/validate
    contract.py        # feature_config.json / labels / metrics_summary schema validation
    metrics.py         # accuracy, macro F1, confusion matrix, off-by-one (rep count)
    landmarks.py       # MediaPipe extraction (GUARDED import) + pure post-processing
    train_baseline.py  # RandomForest (sklearn) — smoke-tested on synthetic data
  src/  (CLI entrypoints, thin)
    extract_landmarks.py  build_features.py
    train_exercise_classifier.py  train_phase_classifier.py
    export_tflite.py  evaluate.py
  tests/
    test_geometry.py test_normalize.py test_confidence.py test_features.py
    test_windowing.py test_summarize.py test_labels.py test_contract.py
    test_metrics.py test_train_baseline.py
  notebooks/
    health_trainer_training_colab.ipynb
.claude/mcp/
  README.md  setup.sh             # MCP env setup (project-scoped)
.mcp.json                          # project-scoped MCP server config (colab-mcp + jupyter)
.claude/agents/
  ml-data-engineer.md  ml-training-engineer.md
  ml-export-engineer.md  ml-verification-qa.md
```

## Module contracts (locked here so later tasks stay consistent)

Landmark name keys (subset used by feature_config, snake_case to match `feature_config.json`):
`left_shoulder right_shoulder left_elbow right_elbow left_wrist right_wrist left_hip right_hip left_knee right_knee left_ankle right_ankle`.

A **frame** is `dict[str, tuple[float, float, float, float]]` → name → (x, y, z, visibility).

- `geometry.angle_degrees(a, b, c) -> float` — interior angle at `b`, in degrees, 0..180. 3D points are `(x,y,z)`.
- `geometry.distance(a, b) -> float`; `geometry.midpoint(a, b) -> tuple`.
- `normalize.normalize_frame(frame) -> dict` — translate by hip center `midpoint(left_hip,right_hip)`, divide by `distance(left_shoulder,right_shoulder)`; visibility passed through unchanged. Raises `ValueError` if shoulder width is 0.
- `confidence.REQUIRED_VISIBILITY = 0.55`; `confidence.frame_is_usable(frame, required_names) -> bool`; `confidence.clip_is_usable(frames, required_names, max_drop_ratio=0.30) -> bool`.
- `features.ANGLE_FEATURES = ["left_knee_angle","right_knee_angle","left_elbow_angle","right_elbow_angle","body_line_angle"]` (matches `feature_config.json`). `features.frame_feature_vector(frame, config) -> list[float]`.
- `windowing.make_windows(seq, length=60, stride=30) -> list[list]` — only full-length windows.
- `summarize.summarize_window(window) -> dict` — per-feature `min/max/mean/std`.
- `labels.load_label_map(path) -> dict[int,str]`; validates contiguous int keys from 0.
- `contract.validate_feature_config(d)`, `contract.validate_labels(d)`, `contract.validate_metrics_summary(d)` — raise `ValueError` on violation.
- `metrics.accuracy`, `metrics.macro_f1`, `metrics.confusion_matrix`, `metrics.off_by_one_accuracy`.

---

## Task 0: Track scaffolding + lessons log (this commit)

**Files:** Create `docs/superpowers/plans/2026-06-03-model-training.md` (this file), `ml/LESSONS.md`.

- [ ] Write this plan, seed `ml/LESSONS.md` with session lessons, commit `docs(ml): model-training plan + lessons log`.

## Task 1: Python package + dev tooling

**Files:** `ml/pyproject.toml`, `ml/requirements-dev.txt`, `ml/requirements-colab.txt`, `ml/src/healthtrainer_ml/__init__.py`, `ml/README.md`.

- [ ] Create uv venv (`uv venv --python 3.11` in `ml/`), install dev deps, confirm `pytest` collects 0 tests cleanly. Commit `chore(ml): python package skeleton + dev tooling`.

## Task 2: geometry (TDD)  — mirrors `:core` AngleCalculator

- [ ] **RED** `tests/test_geometry.py`:
```python
from healthtrainer_ml.geometry import angle_degrees, distance, midpoint
def test_right_angle_returns_90():
    assert abs(angle_degrees((1,0,0),(0,0,0),(0,1,0)) - 90.0) < 0.5
def test_straight_line_returns_180():
    assert abs(angle_degrees((-1,0,0),(0,0,0),(1,0,0)) - 180.0) < 0.5
def test_distance_and_midpoint():
    assert abs(distance((0,0,0),(3,4,0)) - 5.0) < 1e-6
    assert midpoint((0,0,0),(2,4,6)) == (1.0,2.0,3.0)
```
- [ ] **Verify RED** (`module not found`). **GREEN** minimal `geometry.py`. **Verify GREEN**. Commit `feat(ml): geometry angle/distance/midpoint (TDD)`.

## Task 3: normalize (TDD) — mirrors `:core` LandmarkNormalizer

- [ ] **RED** `tests/test_normalize.py`: hip center maps to origin; a point one shoulder-width away maps to unit distance; visibility preserved; zero shoulder width raises `ValueError`.
- [ ] GREEN `normalize.py`. Commit `feat(ml): hip-center/shoulder-width normalization (TDD)`.

## Task 4: confidence filter (TDD)

- [ ] **RED** `tests/test_confidence.py`: frame with a required joint at visibility `0.5` is unusable; all `>=0.55` usable; clip with >30% dropped frames is unusable.
- [ ] GREEN `confidence.py`. Commit `feat(ml): visibility confidence filter (TDD)`.

## Task 5: features (TDD) — feature_config contract

- [ ] **RED** `tests/test_features.py`: `frame_feature_vector` length == len(landmarks)*4 + len(ANGLE_FEATURES); knee angle computed from hip-knee-ankle matches `geometry.angle_degrees`.
- [ ] GREEN `features.py`. Commit `feat(ml): per-frame landmark+angle feature vector (TDD)`.

## Task 6: windowing (TDD)

- [ ] **RED** `tests/test_windowing.py`: 120 frames, length 60 stride 30 → 3 full windows; 50 frames → 0 windows.
- [ ] GREEN `windowing.py`. Commit `feat(ml): sequence windowing (TDD)`.

## Task 7: summarize (TDD)

- [ ] **RED** `tests/test_summarize.py`: summary of a known window yields expected min/max/mean/std per feature.
- [ ] GREEN `summarize.py`. Commit `feat(ml): RandomForest summary features (TDD)`.

## Task 8: labels + contract (TDD)

- [ ] **RED** `tests/test_labels.py`, `tests/test_contract.py`: valid label map loads; non-contiguous keys raise; valid/invalid `feature_config.json` and `metrics_summary.json` per `docs/colab-drive-workflow.md` Artifact Contract.
- [ ] GREEN `labels.py`, `contract.py`. Commit `feat(ml): label map + artifact-contract validation (TDD)`.

## Task 9: metrics (TDD)

- [ ] **RED** `tests/test_metrics.py`: accuracy, macro-F1 (known confusion), off-by-one (pred within ±1 of true counts).
- [ ] GREEN `metrics.py`. Commit `feat(ml): eval metrics incl off-by-one rep accuracy (TDD)`.

## Task 10: RandomForest baseline (TDD smoke)

- [ ] **RED** `tests/test_train_baseline.py`: on a synthetic separable 2-class summary-feature set, `train_baseline.fit` returns a model whose train accuracy is `> 0.9`.
- [ ] GREEN `train_baseline.py` (sklearn `RandomForestClassifier`). Commit `feat(ml): RandomForest baseline trainer (TDD smoke)`.

## Task 11: guarded heavy modules + CLI entrypoints (no heavy run)

**Files:** `ml/src/healthtrainer_ml/landmarks.py`, `ml/src/{extract_landmarks,build_features,train_exercise_classifier,train_phase_classifier,export_tflite,evaluate}.py`.

- [ ] `landmarks.py`: `import mediapipe` is lazy (inside the extraction function); the pure post-processing (`raw_to_frame`) IS unit-tested with a fake raw result. CLI entrypoints are thin `argparse` wrappers calling the tested core, matching the commands in `docs/colab-drive-workflow.md`. Commit `feat(ml): guarded mediapipe adapter + CLI entrypoints`.

## Task 12: configs + Colab notebook

**Files:** `ml/configs/*.yaml`, `ml/notebooks/health_trainer_training_colab.ipynb`.

- [ ] YAML configs per `feature_config.json` (sequence_length 60, the 12 landmarks, 5 angle features, hip_center/shoulder_width normalization). Notebook = thin runner that mounts Drive, unzips `code/`, calls the CLI scripts, writes to `runs/` + `exports/latest/` (per workflow doc). Commit `feat(ml): training configs + Colab runner notebook`.

## Task 13: MCP environment (project-scoped)

**Files:** `.claude/mcp/README.md`, `.claude/mcp/setup.sh`, `.mcp.json`.

- [ ] `.mcp.json` registers `colab-mcp` (`uvx git+https://github.com/googlecolab/colab-mcp`) and `jupyter` (Datalayer, local kernel) — project scope only. `setup.sh` prepares a uv-managed venv under `.claude/mcp/` for the local Jupyter MCP and documents the semi-manual Colab "Connect" step. Commit `feat(mcp): project-scoped Colab + Jupyter MCP servers`.

## Task 14: model-training agents

**Files:** `.claude/agents/ml-{data-engineer,training-engineer,export-engineer,verification-qa}.md`.

- [ ] Author 4 agents modeled on the existing `.claude/agents/*.md` (frontmatter: name/description/tools/model; body: role, skills, TDD discipline, verification). Commit `feat(agents): model-training specialist agents`.

## Task 15: verify + PR

- [ ] `pytest` all green (capture output). Update `CLAUDE.md` change log. Push branch, open PR `model-training → main`. Commit `docs: record model-training track in harness changelog`.

## Self-review

- Spec coverage: extraction(guarded)+features+windowing+baseline+metrics+export(guarded)+Colab/Drive scaffolding+MCP+agents all mapped. Phase/error-classifier models stay Colab-side (guarded) per docs (MVP shows exercise + phase only).
- No placeholders: each TDD task lists concrete assertions; heavy libs explicitly guarded.
- Type consistency: frame = name→(x,y,z,visibility) throughout; snake_case landmark keys match `feature_config.json`.
