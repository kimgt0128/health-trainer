# Health Trainer — 진행 현황 (Status)

> 마지막 갱신: **2026-06-08**. "지금 어디까지 됐나"를 한눈에 보는 요약. 상세 계획은 `docs/plan.md`(앱 MVP) / `docs/model-training-plan.md`(모델), 교훈은 `docs/LESSONS.md`.

## 한눈에
- **MVP 앱 (rule-based, primary):** pose 추정 → 정규화 → 룰 자세 판정 → rep/set·실패 회차 기록 → 결과 리포트 → 3D skeleton replay. 스쿼트·푸쉬업·플랭크 지원.
- **ML 보조 모델 (optional, secondary):** 운동별 TFLite form 분류기. **룰이 카운팅·유효성의 source of truth, 모델은 힌트만.** 모델 없으면 rules-only로 안전 동작(crash 금지).

## 검증 환경
- JDK 17 + Android SDK 있음 → `./gradlew :core:test`(도메인 로직) + `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug`(앱 컴파일·APK 빌드)로 검증.
- 카메라/MediaPipe/TFLite **런타임은 실기기 필요** (`unverified, requires device`).

## 운동별 보조 모델 현황
| 운동 | seam (`:core` extractor) | feature 수 | 학습 결과 | `.tflite` 통합 |
|---|---|---|---|---|
| 스쿼트 | frame · `SquatFeatureExtractor` | 12 | Keras MLP ~0.95 (HGB 0.94) | 레지스트리 등록만 (에셋 미배치 → rules-only) |
| 푸쉬업 | rep · `PushUpFeatureExtractor` | 10 | rep CV 0.835 / clip 0.860 (leakage 제거) | 레지스트리 등록만 |
| **플랭크** | frame · hold 750ms throttle · `PlankFeatureExtractor` | 8 | **Keras MLP macro-F1 0.767 / CV 0.773** | **통합 완료** — 에셋에 `.tflite` 배치 + 빌드·패키징 확인 |

플랭크 모델 디테일: 회전·스케일 정규화 **body-frame** feature(어깨→발목 축, `/L²`) + z=0 투영(2D) → 학습(OpenPose 픽셀)과 추론(MediaPipe 정규화)의 분포 일치. 라벨이 어깨-엉덩이-무릎 각도 기반이라 **무릎 feature 포함**(5→8, 0.57→0.77). 클래스별 F1: hips_low 0.72 / correct 0.77 / hips_high 0.81.

## 데이터·학습 트랙 (`ml/`)
- 순수 Python TDD 코어 + **Colab 학습**(mediapipe/tensorflow는 lazy import — 로컬 테스트는 heavy dep 없이). 산출물: Drive `runs/` → 앱 `assets/models/<운동>_form.tflite`(gitignore, 기기/로컬 전용).
- **feature 계약** = `:core` `featureNames()` 순서 ≡ ml `feature_config`(드리프트 단일 출처, 테스트로 고정).
- 데이터셋: 스쿼트(Kaggle feature CSV) / 푸쉬업(Kaggle raw video → MediaPipe rep feature) / 플랭크(Vollkorn01 keypoint, **license 미선언 → vendoring 금지·학습 시 HTTP 참조·출처 명시**).
- 플랭크 학습 노트북: `ml/notebooks/plank_training_colab.ipynb` (Kaggle·MediaPipe 불필요, 데이터 HTTP 로드).

## 현재 브랜치 / PR
- `model-training` 브랜치: 플랭크 보조 모델 전체(데이터 감사 → 8-feature body-frame 계약 → `:core` 추출기 → MLP 학습 → `:app` 배선 → `.tflite` 통합) + 문서. → **PR #17** (model-training → main, MERGEABLE).

## 다음 후보 (미착수)
- 플랭크 모델 **실기기 런타임** 검증(카메라+MediaPipe+TFLite).
- 스쿼트·푸쉬업 `.tflite`를 앱 에셋에 배치(현재 등록만 → 모델 드롭 시 활성).
- 앱-수집 라벨 데이터로 종목별 모델 개선.
