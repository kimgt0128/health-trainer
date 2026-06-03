# Model Training Plan

이 문서는 Health Trainer의 모델 학습 전략을 정리한다. MVP의 목표는 전문 트레이너 수준의 정자세 판정 모델을 만드는 것이 아니라, MediaPipe로 추출한 관절 landmark를 이용해 운동 동작을 이해하고 자세 피드백을 보조하는 가벼운 모델을 만드는 것이다.

## 결론

MVP에서는 **분류 모델 중심**으로 간다.

```text
운동 종류 인식
-> 분류 모델
-> squat / push_up / plank / unknown

동작 단계 인식
-> 분류 모델
-> top / bottom / hold / transition

자세 품질 평가
-> rule-based scoring
-> 깊이, 몸통 정렬, 무릎/팔꿈치 각도 기준

폼 점수 예측
-> 회귀 모델
-> 추후 고도화 항목
```

회귀 모델은 전문 트레이너가 각 rep에 `0~100`점 같은 품질 점수를 붙인 데이터가 있을 때 적합하다. 현재 MVP에서는 그런 라벨을 확보하기 어렵기 때문에, 회귀보다 분류 모델과 rule-based scoring을 조합하는 것이 현실적이다.

## 학습 목표

### 1단계: 운동 종류 분류

가장 먼저 학습할 모델이다.

- 입력: 30~90프레임의 normalized landmark sequence
- 출력: `squat`, `push_up`, `plank`, `unknown`
- 목적: 앱이 현재 사용자가 어떤 운동을 하는지 이해하는 기능
- 모델 후보: RandomForest, 1D CNN, LSTM

MVP에서는 사용자가 운동을 직접 선택하더라도, 운동 종류 분류 모델을 실험으로 포함하면 프로젝트의 데이터 분석/모델 학습 요소가 명확해진다.

### 2단계: 동작 단계 분류

반복 횟수 카운팅을 안정화하기 위한 모델이다.

- 입력: 현재 프레임 또는 짧은 window의 landmark/angle feature
- 출력: `top`, `bottom`, `hold`, `transition`
- 목적: `top -> bottom -> top` 상태 전환을 더 안정적으로 감지
- 모델 후보: LSTM, GRU, TCN

이 모델은 정자세 판정이 아니라 움직임의 phase를 예측한다. 따라서 라벨링이 비교적 쉽고, 적은 데이터로도 데모 품질을 높일 수 있다.

### 3단계: 제한된 오자세 분류

여유가 있을 때만 진행한다.

- 입력: 한 rep 단위 landmark sequence
- 출력: `correct`, `shallow`, `forward_lean`, `body_line_broken`
- 목적: rule-based feedback과 비교하거나 보조하는 실험 기능
- 모델 후보: LSTM, 1D CNN, TCN

이 단계는 공개 데이터셋과 직접 촬영 데이터의 라벨 기준이 다를 수 있으므로, 앱의 최종 판정 로직으로 단독 사용하지 않는다.

## 데이터 전략

데이터는 세 가지로 나눈다.

```text
공개 데이터셋
-> baseline 학습과 실험용

직접 촬영 데이터
-> 최종 데모 튜닝과 검증용

rule-based pseudo-label 데이터
-> 라벨 부족 보완용
```

공개 데이터셋은 모델이 학습 가능한지 증명하는 데 좋다. 하지만 카메라 각도, 사람 체형, 조명, 라벨 기준이 앱 데모 환경과 다를 수 있다. 따라서 최종 시연 품질은 직접 촬영한 작은 데이터셋으로 맞춘다.

## 참고 데이터셋

우선 검토할 데이터셋은 다음과 같다.

- Kaggle Squat Exercise Pose Dataset: 스쿼트 correct/incorrect pose feature 학습에 사용
- Kaggle LSTM Exercise Classification Push Up Videos: 푸쉬업 correct/incorrect 영상과 MediaPipe keypoint 실험에 사용
- Kaggle Physical Exercise Recognition Time Series: push-up, pull-up, sit-up, jumping jack, squat landmark sequence 분류에 사용
- Kaggle Real-Time Exercise Recognition Dataset: squat, push-up, bicep curl, shoulder press 운동 종류 분류 실험에 사용
- Hugging Face PushUpBench: 반복 횟수 counting 평가 아이디어 참고
- MM-Fit: 2D/3D pose estimate와 multimodal 운동 데이터 참고
- Fit3D: 3D skeleton, repetition segmentation, fitness motion 분석 참고
- Fitness-AQA/FLEX: action quality assessment 연구 방향 참고

MVP에서는 데이터셋을 많이 섞기보다, 하나의 공개 데이터셋으로 baseline을 만들고 직접 촬영 데이터로 보정하는 것이 좋다.

## 직접 촬영 데이터셋

데모 안정성을 위해 작은 controlled dataset을 직접 만든다.

추천 수량:

- 스쿼트 정상 10 clips
- 스쿼트 얕은 동작 10 clips
- 스쿼트 상체 과도 숙임 5 clips
- 푸쉬업 정상 10 clips
- 푸쉬업 얕은 동작 10 clips
- 푸쉬업 몸통 정렬 무너짐 5 clips
- 플랭크 정상 5 clips
- 플랭크 엉덩이 높음/낮음 5 clips

촬영 조건:

- 한 사람 전체 몸이 화면에 들어와야 한다.
- 측면 카메라 구도를 기본으로 한다.
- 카메라는 고정한다.
- 한 clip에는 같은 운동과 같은 라벨만 포함한다.
- 정상/오자세를 일부러 과장해서 라벨이 명확하게 보이도록 한다.

## 데이터 전처리

영상 자체를 바로 학습하지 않는다. 먼저 MediaPipe로 landmark를 추출하고, 숫자 feature로 변환한다.

```text
mp4
-> MediaPipe Pose
-> 33 landmarks
-> visibility filtering
-> normalization
-> angle feature extraction
-> sequence windowing
-> train/validation/test split
```

정규화 기준:

```text
hip_center = midpoint(left_hip, right_hip)
scale = distance(left_shoulder, right_shoulder)
normalized_landmark = (landmark - hip_center) / scale
```

프레임 품질 기준:

- 필수 관절 visibility가 `0.55` 미만이면 해당 프레임은 학습에서 제외한다.
- 제외 프레임이 clip의 `30%`를 넘으면 clip 자체를 제외한다.
- 좌표가 크게 튀는 경우 5-frame moving average 또는 EMA smoothing을 적용한다.

## Feature 설계

모델 입력은 두 종류를 함께 사용한다.

### Landmark Feature

MediaPipe 33개 landmark의 좌표를 사용한다.

```text
33 landmarks x (x, y, z, visibility)
= 132 features per frame
```

앱 MVP에서는 모든 landmark를 쓰기보다 주요 관절 중심으로 줄일 수 있다.

- shoulder
- elbow
- wrist
- hip
- knee
- ankle
- heel
- foot index

### Angle Feature

운동 판정에 직접적인 각도를 추가한다.

- left/right knee angle
- left/right elbow angle
- left/right hip angle
- shoulder-hip-ankle body-line angle
- torso lean angle
- hip height relative to shoulder/ankle line
- knee-to-ankle horizontal offset

추천 입력 형태:

```text
sequence_length = 60 frames
feature_dim = normalized landmarks + angle features
```

## 모델 후보

### Baseline: RandomForest

첫 실험용으로 가장 좋다.

- GPU가 필요 없다.
- feature importance를 볼 수 있다.
- 데이터가 적어도 빠르게 결과를 확인할 수 있다.
- sequence 전체를 평균, 최소, 최대, 표준편차로 요약해 입력한다.

예시 feature:

```text
min_left_knee_angle
max_left_knee_angle
mean_torso_angle
std_hip_height
min_elbow_angle
visibility_mean
```

### Sequence Model: LSTM

동작 흐름을 반영하기 좋다.

추천 구조:

```text
Input: 60 x feature_dim
LSTM: 64 hidden units
Dense: 32
Output: class count
```

Colab 무료 GPU 또는 CPU로도 MVP 실험이 가능하다.

### Sequence Model: 1D CNN / TCN

LSTM보다 빠르게 학습되고 모바일 배포에 유리할 수 있다.

추천 구조:

```text
Input: 60 x feature_dim
Conv1D 64
Conv1D 64
GlobalAveragePooling1D
Dense 32
Output class count
```

### 비추천: 영상 기반 3D CNN / VideoMAE

MVP에서는 사용하지 않는다.

- GPU 요구량이 크다.
- 데이터가 많이 필요하다.
- Android 앱 탑재가 어렵다.
- landmark 기반 접근보다 설명 가능성이 낮다.

## Colab 학습 계획

Colab에서는 다음 순서로 진행한다.

학습 실행은 Google Drive의 `health_training` 폴더를 기준으로 한다. 자세한 Drive/Colab/로컬 동기화 전략은 `docs/colab-drive-workflow.md`에 정리한다.

```text
1. 데이터셋 다운로드
2. MediaPipe 설치
3. 영상에서 landmark 추출
4. CSV/NPY로 저장
5. train/validation/test split
6. RandomForest baseline 학습
7. LSTM 또는 1D CNN 학습
8. confusion matrix와 F1-score 확인
9. TFLite 변환 실험
10. Android 앱에서 샘플 입력으로 추론 확인
```

GPU 필요도:

| 작업 | GPU 필요도 | Colab 적합성 |
|---|---:|---|
| MediaPipe landmark 추출 | 낮음 | CPU 가능 |
| RandomForest/SVM/XGBoost | 낮음 | CPU 가능 |
| LSTM/GRU/1D CNN | 낮음~중간 | 무료 T4 충분 |
| TCN/작은 Transformer | 중간 | 무료 T4 가능 |
| VideoMAE/3D CNN | 높음 | MVP 비추천 |

## 평가 지표

모델 accuracy만 보지 않는다. 앱 기능에 맞는 지표를 함께 본다.

### 운동 종류 분류

- accuracy
- macro F1-score
- confusion matrix

### 동작 단계 분류

- frame-level accuracy
- macro F1-score
- transition detection delay

### 반복 횟수

- MAE
- off-by-one accuracy

예:

```text
실제 10회, 예측 9~11회면 off-by-one 성공
실제 10회, 예측 8회면 실패
```

### 오자세 탐지

- precision
- recall
- false positive rate

오자세 탐지는 false positive가 너무 많으면 사용자 경험이 나빠진다. 따라서 MVP에서는 recall보다 precision을 우선한다.

## 앱 적용 전략

학습 모델은 최종 판정자가 아니라 보조 신호로 사용한다.

```text
MediaPipe landmark
-> lightweight classifier
-> exercise / phase prediction
-> rule engine
-> form score and feedback
-> result record
```

앱에서 사용하는 최종 피드백은 다음 정보를 함께 고려한다.

- landmark visibility
- camera angle guide 통과 여부
- 분류 모델 confidence
- rule-based metric
- rep state machine 결과

모델 confidence가 낮으면 판정을 보류한다.

```text
모델 confidence < 0.6
-> "카메라 구도 또는 동작 인식이 불안정합니다"
-> rep/form 판정 보류
```

완성된 모델은 Drive의 `exports/latest/`에서 로컬 앱의 `app/src/main/assets/models/`로 가져와 통합한다. 이때 `.tflite` 파일만 가져오지 말고 `labels.json`, `feature_config.json`, `metrics_summary.json`을 함께 가져온다.

## TensorFlow Lite Export

Android 앱에 모델을 넣는다면 TensorFlow Lite를 사용한다.

권장 순서:

```text
Keras model
-> SavedModel
-> TFLite float32
-> Android integration
-> 필요하면 float16 quantization
```

처음부터 int8 quantization까지 하지 않는다. MVP에서는 정확도 손실과 calibration dataset 준비 부담이 생길 수 있다.

## 발표 포인트

발표에서는 다음 흐름으로 설명한다.

1. 영상에서 직접 학습하지 않고 pose landmark를 추출했다.
2. landmark를 정규화해서 체형과 거리 차이를 줄였다.
3. 운동 종류/동작 단계는 분류 모델로 학습했다.
4. 정자세 평가는 관절 각도 기반 rule-based scoring으로 처리했다.
5. 모델 결과와 rule 결과를 함께 사용해 안정적인 피드백을 제공했다.
6. 잘못된 회차는 세트별로 기록하고 3D skeleton replay로 근거를 보여준다.

핵심 메시지:

```text
모델은 정답 판정자가 아니라 동작 이해 보조자다.
최종 피드백은 설명 가능한 관절 기준과 함께 제공한다.
```

## 최종 MVP 산출물

- Colab notebook: landmark extraction and model training
- `features.csv` 또는 `features.npz`: 전처리된 landmark/angle feature
- `exercise_classifier.tflite`: 선택적 앱 탑재 모델
- `phase_classifier.tflite`: 선택적 앱 탑재 모델
- `metrics_report.md`: accuracy, F1-score, confusion matrix 요약
- Android app: rule-based scoring과 3D replay 중심 MVP

## 리스크와 대응

- 공개 데이터셋과 실제 데모 환경이 다름: 직접 촬영 데이터로 최종 threshold를 보정한다.
- 오자세 라벨이 애매함: 처음에는 과장된 오자세만 라벨링한다.
- 모델이 잘못된 판정을 함: confidence가 낮으면 판정을 보류하고 rule-based 결과를 우선한다.
- 데이터가 적음: RandomForest baseline과 LSTM을 모두 비교해 작은 데이터에서 더 안정적인 모델을 선택한다.
- 모바일 탑재가 어려움: 모델 탑재 없이도 rule-based scoring으로 앱이 동작하도록 만든다.
