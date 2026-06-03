# Colab and Google Drive Workflow

이 문서는 Health Trainer의 모델 학습 코드를 로컬에서 작성하고, 실제 학습 실행은 Google Drive의 Colab 환경에서 수행한 뒤, 완성된 모델을 다시 로컬 Android 앱에 통합하는 전략을 정리한다.

## Drive Folder

사용할 Google Drive 폴더:

```text
name: health_training
id: 1arJLx5azgSBvdSLgynE9rmx6Mrexyecy
url: https://drive.google.com/drive/folders/1arJLx5azgSBvdSLgynE9rmx6Mrexyecy
```

이 폴더는 학습용 데이터, Colab notebook, 실험 결과, 최종 export 모델을 보관하는 원격 작업공간으로 사용한다.

## Recommended Strategy

전략은 좋다. 다만 Colab notebook 안에 모든 코드를 직접 작성하면 재현성과 유지보수가 떨어진다. 따라서 **로컬 코드 원본 + Colab 실행 notebook + Drive artifact 저장소** 방식으로 분리한다.

```text
Local workspace
-> 학습 코드 작성, 앱 코드 작성, 문서 관리

Google Drive
-> 데이터셋, Colab notebook, 학습 결과, export 모델 보관

Colab
-> GPU/CPU 실행 환경
-> 로컬에서 작성한 학습 코드를 실행

Android app
-> 완성된 .tflite 모델과 config를 assets에 통합
```

핵심 원칙:

- 로컬에 있는 Python 학습 코드를 source of truth로 둔다.
- Colab notebook은 코드 본문을 길게 담지 않고 실행용 runner로 사용한다.
- Drive에는 데이터와 결과물을 versioned artifact로 저장한다.
- 앱에 병합할 때는 `.tflite`, `labels.json`, `feature_config.json`, `metrics.json`을 한 세트로 가져온다.

## Drive Folder Structure

Drive 폴더는 다음 구조를 권장한다.

```text
health_training/
  colab/
    health_trainer_training.ipynb
  data/
    raw/
      public/
      self_recorded/
    landmarks/
      public/
      self_recorded/
    splits/
      train.csv
      val.csv
      test.csv
  code/
    health_trainer_ml.zip
  runs/
    2026-06-03_exercise_classifier_rf_v1/
      model.joblib
      labels.json
      feature_config.json
      metrics.json
      confusion_matrix.png
      report.md
    2026-06-03_phase_classifier_lstm_v1/
      model.keras
      model.tflite
      labels.json
      feature_config.json
      metrics.json
      confusion_matrix.png
      report.md
  exports/
    latest/
      exercise_classifier.tflite
      phase_classifier.tflite
      labels_exercise.json
      labels_phase.json
      feature_config.json
      metrics_summary.json
```

`runs/`는 모든 실험 결과를 남기는 공간이고, `exports/latest/`는 앱에 넣을 최종 모델만 모아두는 공간이다.

## Local Folder Structure

로컬 프로젝트에는 다음 구조를 권장한다.

```text
health_trainer/
  ml/
    README.md
    requirements-colab.txt
    configs/
      exercise_classifier.yaml
      phase_classifier.yaml
    src/
      extract_landmarks.py
      build_features.py
      train_exercise_classifier.py
      train_phase_classifier.py
      export_tflite.py
      evaluate.py
    notebooks/
      health_trainer_training_colab.ipynb
  app/
    src/main/assets/models/
      exercise_classifier.tflite
      phase_classifier.tflite
      labels_exercise.json
      labels_phase.json
      feature_config.json
```

학습 코드는 `ml/src/`에 둔다. Colab notebook은 `ml/notebooks/`에 두되, 실제 로직은 Python script를 호출하는 방식으로 작성한다.

## Colab Execution Flow

Colab notebook은 다음 흐름으로 구성한다.

```text
1. Google Drive mount
2. health_training 폴더 경로 설정
3. requirements 설치
4. 로컬 학습 코드 zip 다운로드 또는 Drive code zip 압축 해제
5. 데이터셋 경로 확인
6. landmark 추출
7. feature 생성
8. baseline 모델 학습
9. LSTM/1D CNN 모델 학습
10. 평가 리포트 저장
11. TFLite export
12. exports/latest에 최종 artifact 저장
```

Colab 첫 셀 예시:

```python
from google.colab import drive
drive.mount("/content/drive")

DRIVE_ROOT = "/content/drive/MyDrive/health_training"
DATA_DIR = f"{DRIVE_ROOT}/data"
RUNS_DIR = f"{DRIVE_ROOT}/runs"
EXPORT_DIR = f"{DRIVE_ROOT}/exports/latest"
```

코드 압축 해제 예시:

```python
!mkdir -p /content/health_trainer_ml
!unzip -o "$DRIVE_ROOT/code/health_trainer_ml.zip" -d /content/health_trainer_ml
%cd /content/health_trainer_ml
```

학습 실행 예시:

```python
!python ml/src/train_exercise_classifier.py \
  --config ml/configs/exercise_classifier.yaml \
  --data-dir "$DATA_DIR" \
  --run-dir "$RUNS_DIR/exercise_classifier_lstm_v1"
```

TFLite export 예시:

```python
!python ml/src/export_tflite.py \
  --run-dir "$RUNS_DIR/exercise_classifier_lstm_v1" \
  --export-dir "$EXPORT_DIR"
```

## Local to Drive Code Sync

현재 프로젝트가 git 저장소가 아니므로, 초기에는 zip 방식이 가장 단순하다.

```text
로컬에서 ml/ 코드 작성
-> health_trainer_ml.zip 생성
-> Drive health_training/code/에 업로드
-> Colab에서 zip 압축 해제
-> 학습 실행
```

프로젝트가 GitHub 저장소로 올라가면 Colab에서는 zip 대신 git clone 방식을 사용한다.

```python
!git clone https://github.com/<owner>/<repo>.git
%cd <repo>
```

추천은 장기적으로 GitHub private repository를 사용하는 것이다. 하지만 텀프로젝트 MVP에서는 Drive zip sync로도 충분하다.

## Drive to Local Model Merge

모델이 완성되면 Drive의 `exports/latest/`에서 다음 파일을 로컬 앱으로 가져온다.

```text
exercise_classifier.tflite
phase_classifier.tflite
labels_exercise.json
labels_phase.json
feature_config.json
metrics_summary.json
```

로컬 앱 통합 위치:

```text
app/src/main/assets/models/
```

병합할 때는 모델 파일만 가져오지 않는다. 반드시 label과 feature config도 함께 가져온다. 모델 입력 feature 순서가 바뀌면 앱 추론 결과가 완전히 틀어질 수 있기 때문이다.

## Artifact Contract

앱에 통합 가능한 모델 artifact는 다음 계약을 만족해야 한다.

### `labels_exercise.json`

```json
{
  "0": "squat",
  "1": "push_up",
  "2": "plank",
  "3": "unknown"
}
```

### `labels_phase.json`

```json
{
  "0": "top",
  "1": "bottom",
  "2": "hold",
  "3": "transition"
}
```

### `feature_config.json`

```json
{
  "sequence_length": 60,
  "landmarks": [
    "left_shoulder",
    "right_shoulder",
    "left_elbow",
    "right_elbow",
    "left_wrist",
    "right_wrist",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle"
  ],
  "per_landmark_features": ["x", "y", "z", "visibility"],
  "angle_features": [
    "left_knee_angle",
    "right_knee_angle",
    "left_elbow_angle",
    "right_elbow_angle",
    "body_line_angle"
  ],
  "normalization": {
    "center": "hip_center",
    "scale": "shoulder_width"
  }
}
```

### `metrics_summary.json`

```json
{
  "exercise_classifier": {
    "accuracy": 0.9,
    "macro_f1": 0.88
  },
  "phase_classifier": {
    "accuracy": 0.86,
    "macro_f1": 0.84
  }
}
```

## Integration Decision Rule

모델을 앱에 넣을지 결정하는 기준:

- 운동 종류 분류 macro F1이 `0.80` 이상이면 앱 실험 기능으로 통합한다.
- 동작 단계 분류 macro F1이 `0.75` 이상이면 rep tracker 보조 신호로 사용한다.
- 실제 데모 영상에서 오작동이 많으면 앱에서는 모델을 비활성화하고 rule-based만 사용한다.
- 어떤 경우에도 정자세 최종 판정은 rule engine과 confidence gate를 거친다.

## Why This Strategy Works

이 전략은 MVP에 적합하다.

- 로컬 코드가 기준이라 실험 재현성이 좋다.
- Colab에서 GPU를 쓸 수 있어 로컬 장비 부담이 없다.
- Drive에 데이터와 결과물이 남아 발표 자료 만들기 쉽다.
- 완성 모델만 Android 앱에 병합하므로 앱 구조가 복잡해지지 않는다.
- 모델이 기대만큼 좋지 않아도 rule-based MVP는 계속 동작한다.

정리하면, **Colab은 훈련장, Drive는 실험 창고, 로컬 프로젝트는 제품 코드의 기준점**으로 쓰는 방식이 가장 안전하다.

