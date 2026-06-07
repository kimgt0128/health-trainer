# Plank Assist Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional plank posture model trained from the Vollkorn01 plank keypoint dataset so the app can surface `hips_low / correct / hips_high` as an assist signal while keeping the existing rule engine as the source of truth.

**Architecture:** Keep plank rule-based scoring primary. Train a small TFLite-compatible MLP on normalized plank posture features, export `plank_form.tflite`, and register it through the existing `FormClassifierRegistry` optional-model seam. Because plank is a hold exercise, the app integration must add a hold-aware assist path instead of relying on rep-boundary-only inference.

**Tech Stack:** Kotlin/JVM `:core`, Android `:app`, Python `ml/`, pandas, scikit-learn metrics, TensorFlow/Keras on Colab, TensorFlow Lite.

---

## Source Decision

Use [Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras](https://github.com/Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras) as the first plank dataset candidate.

Important constraints:

- The repo is a plank-specific keypoint project with labels `0 = too low`, `1 = correct`, `2 = too high`.
- The repo README says the original images were removed for privacy; use the remaining keypoint/dataframe files.
- GitHub API currently reports `"license": null`, so do not vendor the dataset into this repo. Download it in Colab/Drive at training time, cite the source in README, and commit only code/docs plus optional model artifacts when allowed.
- The dataset uses an older 18-keypoint coordinate convention, not MediaPipe's exact 33-landmark topology. Therefore Task 1 is a hard gate: verify keypoint semantics before trusting training metrics.

## File Structure

- Create `core/src/main/kotlin/com/healthtrainer/core/features/PlankFeatureExtractor.kt`
  - Produces the fixed plank model feature vector from normalized `PoseFrame`.
- Create `core/src/test/kotlin/com/healthtrainer/core/features/PlankFeatureExtractorTest.kt`
  - Locks feature order, visibility gate, and sag/pike feature signs.
- Create `ml/src/healthtrainer_ml/plank_pose_dataset.py`
  - Downloads/loads the Vollkorn01 dataframe lazily and converts it into the app feature contract.
- Create `ml/tests/test_plank_pose_dataset.py`
  - Validates labels, feature order, missing-column behavior, and synthetic feature math.
- Create `ml/src/train_plank_form_mlp.py`
  - Trains a small self-normalizing Keras model and exports `plank_form.tflite`.
- Create `ml/tests/test_train_plank_form_mlp.py`
  - Verifies TensorFlow-lazy import, CLI defaults, and artifact contract.
- Modify `app/src/main/java/com/healthtrainer/app/ml/FormClassifierRegistry.kt`
  - Adds `PLANK_LABELS`, `PLANK_MODEL_ASSET`, and one registry entry.
- Modify `app/src/main/java/com/healthtrainer/app/FramePipeline.kt`
  - Adds hold-aware throttled assist inference for plank.
- Modify `app/src/main/assets/models/README.md`
  - Documents `plank_form.tflite` input/output contract.
- Modify `README.md` and `ml/README.md`
  - Adds dataset source, license caution, Colab command, and "model is assist only" explanation.

## Feature Contract

Use this initial feature vector. Names and order must match in Kotlin, Python, and `feature_config.json`.

```text
body_line_angle,
hip_line_signed_offset,
hip_line_abs_offset,
shoulder_hip_y_delta,
hip_ankle_y_delta,
shoulder_ankle_x_span,
max_elbow_offset,
required_visible_ratio
```

Meaning:

- `body_line_angle`: shoulder-center, hip-center, ankle-center angle. Higher is straighter.
- `hip_line_signed_offset`: signed hip deviation from the shoulder-to-ankle line. Positive should mean hips low/sag in the app coordinate convention.
- `hip_line_abs_offset`: absolute value of the same deviation.
- `shoulder_hip_y_delta`: normalized vertical shoulder-to-hip delta.
- `hip_ankle_y_delta`: normalized vertical hip-to-ankle delta.
- `shoulder_ankle_x_span`: normalized horizontal body span.
- `max_elbow_offset`: max horizontal elbow-to-shoulder offset, matching the current plank rule concern.
- `required_visible_ratio`: fraction of required landmarks present/visible.

Labels:

```text
0 -> hips_low
1 -> correct
2 -> hips_high
```

The label names intentionally match app language, not the source repo's `too low / correct / too high`, so UI feedback can map directly to existing `PLANK_HIPS_LOW` and `PLANK_HIPS_HIGH`.

---

### Task 1: Dataset Audit Gate

**Files:**

- Create: `ml/src/healthtrainer_ml/plank_pose_dataset.py`
- Create: `ml/tests/test_plank_pose_dataset.py`

- [ ] **Step 1: Write the dataframe validation tests**

```python
# ml/tests/test_plank_pose_dataset.py
import pandas as pd
import pytest

from healthtrainer_ml.plank_pose_dataset import (
    FEATURE_COLUMNS,
    LABELS,
    SOURCE_COLUMNS,
    validate_plank_dataframe,
    split_features_labels,
    feature_config,
)


def test_label_contract_is_app_facing():
    assert LABELS == {0: "hips_low", 1: "correct", 2: "hips_high"}


def test_feature_contract_order_is_stable():
    assert FEATURE_COLUMNS == [
        "body_line_angle",
        "hip_line_signed_offset",
        "hip_line_abs_offset",
        "shoulder_hip_y_delta",
        "hip_ankle_y_delta",
        "shoulder_ankle_x_span",
        "max_elbow_offset",
        "required_visible_ratio",
    ]


def test_validate_requires_source_columns_and_label():
    df = pd.DataFrame({"label": [1]})
    with pytest.raises(ValueError, match="plank dataset missing columns"):
        validate_plank_dataframe(df)


def test_split_features_labels_returns_expected_shapes():
    row = {str(i): float(i + 1) for i in range(36)}
    row["filename"] = "sample"
    row["label"] = 1
    df = pd.DataFrame([row])

    X, y = split_features_labels(df)

    assert X.shape == (1, len(FEATURE_COLUMNS))
    assert y.tolist() == [1]


def test_feature_config_cites_source_and_license_state():
    cfg = feature_config()
    assert cfg["task"] == "plank_form_classifier"
    assert cfg["source_dataset"] == "Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras"
    assert cfg["source_license"] == "none declared in GitHub API"
    assert cfg["features"] == FEATURE_COLUMNS
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd /Users/kimgt/Developer/Project/health_trainer/ml
.venv/bin/pytest tests/test_plank_pose_dataset.py -q
```

Expected: FAIL because `healthtrainer_ml.plank_pose_dataset` does not exist.

- [ ] **Step 3: Implement the minimal dataset adapter**

```python
# ml/src/healthtrainer_ml/plank_pose_dataset.py
"""Vollkorn01 plank keypoint dataset adapter.

Source:
https://github.com/Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras

The source repo contains `fullDataFrame.csv` with 36 coordinate columns
(`0..35`), `filename`, and `label`. Original images were removed by the source
authors for privacy. GitHub API reports no declared license, so training should
download/read this data in Colab and cite it; do not vendor the dataset here.
"""
from __future__ import annotations

DATASET_HANDLE = "Vollkorn01/Deep-Learning-Fitness-Exercise-Correction-Keras"
DATASET_URL = (
    "https://raw.githubusercontent.com/Vollkorn01/"
    "Deep-Learning-Fitness-Exercise-Correction-Keras/master/fullDataFrame.csv"
)

SOURCE_COLUMNS = [str(i) for i in range(36)]

FEATURE_COLUMNS = [
    "body_line_angle",
    "hip_line_signed_offset",
    "hip_line_abs_offset",
    "shoulder_hip_y_delta",
    "hip_ankle_y_delta",
    "shoulder_ankle_x_span",
    "max_elbow_offset",
    "required_visible_ratio",
]

LABELS = {
    0: "hips_low",
    1: "correct",
    2: "hips_high",
}


def validate_plank_dataframe(df) -> None:
    missing = [c for c in (*SOURCE_COLUMNS, "label") if c not in df.columns]
    if missing:
        raise ValueError(f"plank dataset missing columns: {missing}")

    labels = sorted(int(v) for v in df["label"].dropna().unique().tolist())
    expected = sorted(LABELS)
    if labels and labels != expected:
        raise ValueError(f"expected labels {expected}, got {labels}")


def _xy(row, index: int):
    x = row[str(index * 2)]
    y = row[str(index * 2 + 1)]
    if x != x or y != y:
        return None
    return float(x), float(y)


def _angle_degrees(a, b, c) -> float:
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


def _line_y_at(x: float, shoulder, ankle) -> float:
    dx = ankle[0] - shoulder[0]
    if abs(dx) < 1e-9:
        return (shoulder[1] + ankle[1]) / 2.0
    t = (x - shoulder[0]) / dx
    return shoulder[1] + t * (ankle[1] - shoulder[1])


def _row_to_features(row) -> list[float]:
    # Gate mapping: this is the first mapping to verify visually in Task 2.
    # The source README's project plan describes keypoint 2 as shoulders,
    # 3 as hips, and 4 as knees for the labeled-angle stage. For the 18-point
    # predicted dataframe we use COCO-like points and center left/right pairs.
    left_shoulder = _xy(row, 5)
    right_shoulder = _xy(row, 6)
    left_elbow = _xy(row, 7)
    right_elbow = _xy(row, 8)
    left_hip = _xy(row, 11)
    right_hip = _xy(row, 12)
    left_ankle = _xy(row, 15)
    right_ankle = _xy(row, 16)

    required = [
        left_shoulder,
        right_shoulder,
        left_hip,
        right_hip,
        left_ankle,
        right_ankle,
    ]
    visible_ratio = sum(p is not None for p in required) / len(required)
    if visible_ratio == 0:
        return [0.0] * (len(FEATURE_COLUMNS) - 1) + [0.0]

    def midpoint(a, b):
        if a is None:
            return b
        if b is None:
            return a
        return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)

    shoulder = midpoint(left_shoulder, right_shoulder)
    hip = midpoint(left_hip, right_hip)
    ankle = midpoint(left_ankle, right_ankle)

    if shoulder is None or hip is None or ankle is None:
        return [0.0] * (len(FEATURE_COLUMNS) - 1) + [visible_ratio]

    span = max(abs(ankle[0] - shoulder[0]), 1.0)
    body_line_angle = _angle_degrees(shoulder, hip, ankle)
    signed_offset = (hip[1] - _line_y_at(hip[0], shoulder, ankle)) / span

    elbow_offsets = []
    if left_elbow is not None and left_shoulder is not None:
        elbow_offsets.append(abs(left_elbow[0] - left_shoulder[0]) / span)
    if right_elbow is not None and right_shoulder is not None:
        elbow_offsets.append(abs(right_elbow[0] - right_shoulder[0]) / span)

    return [
        float(body_line_angle),
        float(signed_offset),
        float(abs(signed_offset)),
        float((hip[1] - shoulder[1]) / span),
        float((ankle[1] - hip[1]) / span),
        float(abs(ankle[0] - shoulder[0]) / span),
        float(max(elbow_offsets) if elbow_offsets else 0.0),
        float(visible_ratio),
    ]


def load_plank_dataframe():  # pragma: no cover - network/Colab path
    import pandas as pd

    df = pd.read_csv(DATASET_URL)
    validate_plank_dataframe(df)
    return df


def split_features_labels(df):
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
        "granularity": "frame_or_static_image",
        "features": list(FEATURE_COLUMNS),
        "labels": {str(k): v for k, v in LABELS.items()},
    }
```

- [ ] **Step 4: Run the dataset tests**

Run:

```bash
cd /Users/kimgt/Developer/Project/health_trainer/ml
.venv/bin/pytest tests/test_plank_pose_dataset.py -q
```

Expected: PASS.

- [ ] **Step 5: Run a Colab/data audit before model training**

Run in Colab:

```bash
export PYTHONPATH=/content/health_trainer/ml/src
python - <<'PY'
from healthtrainer_ml.plank_pose_dataset import load_plank_dataframe, split_features_labels
df = load_plank_dataframe()
X, y = split_features_labels(df)
print(df.shape)
print(df["label"].value_counts().sort_index().to_dict())
print("X shape:", X.shape)
print("nan source ratio:", df[[str(i) for i in range(36)]].isna().mean().mean())
print("feature min/max:", X.min(axis=0).round(3).tolist(), X.max(axis=0).round(3).tolist())
PY
```

Expected: 3 labels exist, feature matrix has 8 columns, and feature ranges look plausible. If ranges are clearly broken, stop and fix the keypoint mapping before training.

- [ ] **Step 6: Commit the adapter**

```bash
git add ml/src/healthtrainer_ml/plank_pose_dataset.py ml/tests/test_plank_pose_dataset.py
git commit -m "test: add plank dataset adapter contract"
```

---

### Task 2: Kotlin Feature Extractor Contract

**Files:**

- Create: `core/src/main/kotlin/com/healthtrainer/core/features/PlankFeatureExtractor.kt`
- Create: `core/src/test/kotlin/com/healthtrainer/core/features/PlankFeatureExtractorTest.kt`

- [ ] **Step 1: Write tests for feature order and gate behavior**

```kotlin
// core/src/test/kotlin/com/healthtrainer/core/features/PlankFeatureExtractorTest.kt
package com.healthtrainer.core.features

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

class PlankFeatureExtractorTest {
    private val extractor = PlankFeatureExtractor()

    @Test
    fun contract_isStable() {
        assertThat(extractor.exerciseType).isEqualTo(ExerciseType.PLANK)
        assertThat(extractor.featureNames()).containsExactly(
            "body_line_angle",
            "hip_line_signed_offset",
            "hip_line_abs_offset",
            "shoulder_hip_y_delta",
            "hip_ankle_y_delta",
            "shoulder_ankle_x_span",
            "max_elbow_offset",
            "required_visible_ratio",
        ).inOrder()
    }

    @Test
    fun lowVisibility_returnsNull() {
        val frame = plankFrame(hipY = 0f, visibility = 0.2f)

        assertThat(extractor.extract(frame)).isNull()
    }

    @Test
    fun saggingHip_hasPositiveSignedOffset() {
        val out = extractor.extract(plankFrame(hipY = 0.35f))!!
        val idx = extractor.featureNames().indexOf("hip_line_signed_offset")

        assertThat(out[idx]).isGreaterThan(0f)
    }

    @Test
    fun pikedHip_hasNegativeSignedOffset() {
        val out = extractor.extract(plankFrame(hipY = -0.35f))!!
        val idx = extractor.featureNames().indexOf("hip_line_signed_offset")

        assertThat(out[idx]).isLessThan(0f)
    }

    private fun plankFrame(hipY: Float, visibility: Float = 1f): PoseFrame =
        PoseFrame(
            timestampMs = 0,
            landmarks = mapOf(
                LandmarkName.LEFT_SHOULDER to lm(0f, 0f, visibility),
                LandmarkName.RIGHT_SHOULDER to lm(0f, 0f, visibility),
                LandmarkName.LEFT_HIP to lm(1f, hipY, visibility),
                LandmarkName.RIGHT_HIP to lm(1f, hipY, visibility),
                LandmarkName.LEFT_ANKLE to lm(2f, 0f, visibility),
                LandmarkName.RIGHT_ANKLE to lm(2f, 0f, visibility),
                LandmarkName.LEFT_ELBOW to lm(0.05f, 0.25f, visibility),
                LandmarkName.RIGHT_ELBOW to lm(0.05f, 0.25f, visibility),
            ),
        )

    private fun lm(x: Float, y: Float, visibility: Float) =
        PoseLandmark(x = x, y = y, z = 0f, visibility = visibility)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd /Users/kimgt/Developer/Project/health_trainer
./gradlew :core:test --tests '*PlankFeatureExtractorTest'
```

Expected: FAIL because `PlankFeatureExtractor` does not exist.

- [ ] **Step 3: Implement `PlankFeatureExtractor`**

Implement it to mirror `PlankRule` sign convention:

- average left/right shoulders, hips, ankles, elbows;
- require shoulders, hips, ankles with visibility >= `0.55`;
- return null if required joints are missing;
- use feature names from this plan exactly;
- compute signed offset with positive = hips low/sag.

- [ ] **Step 4: Run the core tests**

Run:

```bash
./gradlew :core:test --tests '*PlankFeatureExtractorTest' --tests '*PlankRuleTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/healthtrainer/core/features/PlankFeatureExtractor.kt core/src/test/kotlin/com/healthtrainer/core/features/PlankFeatureExtractorTest.kt
git commit -m "feat: add plank feature extractor"
```

---

### Task 3: Train TFLite Plank Model

**Files:**

- Create: `ml/src/train_plank_form_mlp.py`
- Create: `ml/tests/test_train_plank_form_mlp.py`

- [ ] **Step 1: Write lazy-import and contract tests**

```python
# ml/tests/test_train_plank_form_mlp.py
import importlib
import sys

import pytest

MODULE = "train_plank_form_mlp"


def test_module_imports_without_tensorflow():
    sys.modules.pop("tensorflow", None)
    mod = importlib.import_module(MODULE)
    assert callable(mod.main)
    assert "tensorflow" not in sys.modules


def test_arg_parser_defaults():
    mod = importlib.import_module(MODULE)
    args = mod.build_arg_parser().parse_args(["--run-dir", "/tmp/run"])
    assert args.run_dir == "/tmp/run"
    assert args.test_size == 0.2
    assert args.random_state == 0
    assert args.hidden == [16]
    assert args.epochs == 100


def test_artifact_contract_matches_app():
    from healthtrainer_ml.plank_pose_dataset import FEATURE_COLUMNS, LABELS, feature_config

    assert len(FEATURE_COLUMNS) == 8
    assert [LABELS[i] for i in range(len(LABELS))] == ["hips_low", "correct", "hips_high"]
    assert feature_config()["features"] == FEATURE_COLUMNS
```

- [ ] **Step 2: Implement trainer by mirroring `train_squat_form_mlp.py`**

Use a tiny model:

```text
Input(8) -> Normalization(adapted on train) -> Dense(16, relu) -> Dense(3, softmax)
```

Emit:

```text
plank_form.tflite
labels_plank_form.json
feature_config.json
metrics_summary.json
```

Metrics:

- stratified held-out accuracy;
- macro F1;
- confusion matrix;
- 5-fold stratified CV if local data size allows.

- [ ] **Step 3: Run local tests**

```bash
cd /Users/kimgt/Developer/Project/health_trainer/ml
.venv/bin/pytest tests/test_plank_pose_dataset.py tests/test_train_plank_form_mlp.py -q
```

Expected: PASS without importing TensorFlow.

- [ ] **Step 4: Train in Colab**

```bash
cd /content/health_trainer
export PYTHONPATH=/content/health_trainer/ml/src
python ml/src/train_plank_form_mlp.py \
  --run-dir "$RUNS_DIR/plank_form_classifier_v1" \
  --test-size 0.2 \
  --random-state 42 \
  --hidden 16 \
  --epochs 100
```

Expected: artifact set is written under `$RUNS_DIR/plank_form_classifier_v1`.

- [ ] **Step 5: Decide if the model is good enough to integrate**

Acceptance threshold for MVP assist:

- macro F1 >= 0.75 on held-out split;
- no single class has F1 < 0.60;
- confusion between `correct` and `hips_high` is documented if present;
- a low-confidence or missing model still leaves the app fully rule-based.

- [ ] **Step 6: Commit**

```bash
git add ml/src/train_plank_form_mlp.py ml/tests/test_train_plank_form_mlp.py
git commit -m "feat: add plank form MLP trainer"
```

---

### Task 4: Android Optional Assist Wiring

**Files:**

- Modify: `app/src/main/java/com/healthtrainer/app/ml/FormClassifierRegistry.kt`
- Modify: `app/src/main/java/com/healthtrainer/app/FramePipeline.kt`
- Modify: `app/src/main/java/com/healthtrainer/app/ui/FeedbackText.kt`
- Modify: `app/src/main/assets/models/README.md`

- [ ] **Step 1: Register the model contract**

Add to `FormClassifierRegistry.kt`:

```kotlin
val PLANK_LABELS: List<String> = listOf("hips_low", "correct", "hips_high")
const val PLANK_MODEL_ASSET = "models/plank_form.tflite"
```

Add registry entry:

```kotlin
ExerciseType.PLANK to Spec(
    extractor = PlankFeatureExtractor(),
    modelAsset = PLANK_MODEL_ASSET,
    labels = PLANK_LABELS,
),
```

- [ ] **Step 2: Add hold-aware throttling**

Modify `FramePipeline.process` so hold exercises can classify occasionally during an active set:

```kotlin
val formAssist = when {
    closedRep != null -> classifyAssist(normFrame, closedRep)
    countReps && isHold -> classifyHoldAssist(normFrame, timestampMs)
    else -> null
}
```

Add:

```kotlin
private var lastHoldAssistAtMs: Long? = null

private fun classifyHoldAssist(normFrame: PoseFrame, timestampMs: Long): FormPrediction? {
    val classifier = formClassifier ?: return null
    val extractor = featureExtractor ?: return null
    val last = lastHoldAssistAtMs
    if (last != null && timestampMs - last < HOLD_ASSIST_INTERVAL_MS) return null
    lastHoldAssistAtMs = timestampMs
    val features = extractor.extract(normFrame) ?: return null
    return fuseAssist(classifier.classify(features))
}
```

Add companion constant:

```kotlin
const val HOLD_ASSIST_INTERVAL_MS = 750L
```

Reset `lastHoldAssistAtMs = null` in `reset`.

- [ ] **Step 3: Map labels to UI text**

In `FeedbackText.modelFormLabel`, map:

```kotlin
"hips_low" -> "엉덩이 처짐 확인 필요"
"hips_high" -> "엉덩이 솟음 확인 필요"
```

Keep `"correct"` suppressed by `FramePipeline.fuseAssist`.

- [ ] **Step 4: Update model README**

Add a row:

```text
| `plank_form.tflite` | Plank | frame/throttled hold | `[1,8]` float32 -> `[1,3]` probabilities | ml track |
```

Document labels:

```text
`hips_low, correct, hips_high`
```

- [ ] **Step 5: Run Android/core tests**

```bash
cd /Users/kimgt/Developer/Project/health_trainer
./gradlew :core:test :app:assembleDebug
```

Expected: PASS. With no `plank_form.tflite`, app remains rules-only and does not crash.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/healthtrainer/app/ml/FormClassifierRegistry.kt \
  app/src/main/java/com/healthtrainer/app/FramePipeline.kt \
  app/src/main/java/com/healthtrainer/app/ui/FeedbackText.kt \
  app/src/main/assets/models/README.md
git commit -m "feat: wire optional plank form assist"
```

---

### Task 5: Documentation and Demo Story

**Files:**

- Modify: `README.md`
- Modify: `ml/README.md`

- [ ] **Step 1: Add dataset provenance**

README wording:

```markdown
### 플랭크 모델 데이터셋

플랭크 보조 모델 후보는 Vollkorn01의 `Deep-Learning-Fitness-Exercise-Correction-Keras`
repo를 사용합니다. 이 repo는 plank 자세를 keypoint 기반으로 `too low / correct / too high`
세 클래스로 다룹니다. 원본 이미지는 privacy 때문에 제거되어 있고, 현재 학습에는 남아 있는
keypoint dataframe을 사용합니다.

주의: GitHub API 기준 별도 license가 선언되어 있지 않기 때문에 데이터 자체는 repo에
vendoring하지 않고, 학습 시 Colab/Drive에서 참조합니다. README에는 출처를 명시합니다.
```

- [ ] **Step 2: Add training command**

```markdown
python ml/src/train_plank_form_mlp.py \
  --run-dir "$RUNS_DIR/plank_form_classifier_v1" \
  --test-size 0.2 \
  --random-state 42 \
  --hidden 16 \
  --epochs 100
```

- [ ] **Step 3: Add architecture note**

```markdown
플랭크는 정적 hold 운동이라 모델을 rep count에 사용하지 않습니다. 기존 rule engine이
`엉덩이 처짐`, `엉덩이 솟음`, `팔꿈치 정렬`을 판단하고, 모델은 일정 간격으로 보조 힌트만
제공합니다. 모델이 없거나 확신이 낮으면 앱은 rule-based 피드백만 보여줍니다.
```

- [ ] **Step 4: Commit**

```bash
git add README.md ml/README.md
git commit -m "docs: document plank assist model plan"
```

---

## Recommended First Execution

Do not start by editing Android. Execute in this order:

1. Task 1 dataset audit.
2. Task 2 Kotlin feature contract.
3. Task 3 trainer and Colab run.
4. Review `metrics_summary.json`.
5. Only if metrics are acceptable, wire Task 4 Android assist.
6. Finish with Task 5 docs.

This avoids spending Android integration work on a dataset whose keypoint mapping might not match MediaPipe well enough.

## Risk Register

- **Dataset license:** no license declared. Mitigation: cite source, avoid vendoring data, keep this as research/demo unless permission is clarified.
- **Keypoint mapping drift:** source repo's 18-keypoint convention may not match MediaPipe. Mitigation: Task 1 audit is mandatory before training claims.
- **Coordinate convention drift:** source y-axis and app normalized coordinates may differ. Mitigation: tests lock sag/pike sign in `PlankFeatureExtractorTest`.
- **Model overclaiming:** plank rules already handle core issues well. Mitigation: model remains optional assist, never rep validity.
- **Hold inference performance:** plank could run for many frames. Mitigation: throttle hold assist to 750ms and suppress `correct`.

## Completion Criteria

- `ml/tests/test_plank_pose_dataset.py` and `ml/tests/test_train_plank_form_mlp.py` pass locally without TensorFlow.
- `./gradlew :core:test :app:assembleDebug` passes.
- Colab run emits `plank_form.tflite`, `labels_plank_form.json`, `feature_config.json`, and `metrics_summary.json`.
- README names the Vollkorn01 source and states the license caution.
- The app still runs rules-only when `models/plank_form.tflite` is absent.
