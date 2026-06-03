# Health Trainer

Health Trainer는 스마트폰 카메라로 사람의 관절 좌표를 실시간 추정하고, 스쿼트, 푸쉬업, 플랭크 같은 헬스 동작의 자세를 검사하는 컴퓨터비전 텀프로젝트입니다. 운동 중에는 관절 스켈레톤과 피드백을 보여주고, 운동 후에는 세트별 실패 동작을 기록해 사용자가 어떤 회차에서 어떤 기준을 만족하지 못했는지 확인할 수 있게 합니다.

이 프로젝트의 핵심 아이디어는 카메라 영상에서 바로 "좋은 자세"를 맞히는 거대한 모델을 만드는 것이 아닙니다. 먼저 MediaPipe Pose Landmarker 같은 포즈 추정 모델로 관절 좌표를 얻고, 그 좌표를 각도, 거리, 정렬, 상태 전환 규칙으로 해석해 설명 가능한 자세 판정을 만드는 것입니다.

## 기획 의도

헬스 동작은 반복 횟수만 세는 것보다 "그 반복이 제대로 수행되었는지"가 중요합니다. 예를 들어 스쿼트 10회를 했더라도 2회차에서 깊이가 부족하거나, 푸쉬업 5회차에서 허리가 처졌다면 사용자는 그 회차를 나중에 다시 확인할 수 있어야 합니다.

Health Trainer는 다음 문제를 해결하는 것을 목표로 합니다.

- 실시간으로 사용자의 주요 관절 좌표를 추정한다.
- 운동별 정자세 기준을 각도와 정렬 규칙으로 판정한다.
- 세트와 반복 횟수를 추적한다.
- 기준을 만족하지 못한 회차를 `1세트 2회차`처럼 명확히 기록한다.
- 저장된 3D landmark를 이용해 사용자가 운동 후 자세를 다시 볼 수 있게 한다.

## MVP 범위

초기 버전은 세 가지 운동만 지원합니다.

- 스쿼트: 깊이 부족, 상체 과도 숙임, 무릎 정렬 문제를 검사합니다.
- 푸쉬업: 팔꿈치 굽힘 깊이와 어깨-엉덩이-발목 라인을 검사합니다.
- 플랭크: 몸통 정렬, 엉덩이 높이, 팔꿈치 위치를 검사합니다.

첫 제출 버전은 측면 카메라 구도를 기본으로 합니다. 정면 구도까지 동시에 지원하면 무릎 모임, 좌우 균형 같은 분석이 더 좋아지지만, 텀프로젝트 MVP에서는 난이도가 급격히 올라갑니다.

## 상용화 지향 MVP 방향

이 프로젝트는 처음부터 "전문 트레이너 수준의 정자세 판정 모델"을 목표로 잡기보다, 실제 서비스처럼 보이는 안정적인 코칭 경험을 목표로 합니다. 사용자가 납득할 수 있는 피드백, 세트별 기록, 리플레이 경험을 우선하고, 자세 판정은 보수적인 점수화 방식으로 제공합니다.

추천 방향은 다음과 같습니다.

- 포즈 추정은 MediaPipe Pose Landmarker 같은 검증된 모델을 사용합니다.
- 운동 종류와 동작 단계는 landmark sequence 기반으로 안정적으로 분류합니다.
- 정자세 여부는 학습 모델 단독 판정보다 rule-based score로 시작합니다.
- 피드백 문구는 "틀렸습니다"보다 "깊이가 부족할 수 있습니다", "몸통 정렬이 흔들렸습니다"처럼 보수적으로 표현합니다.
- 카메라 구도, landmark confidence, 사용자 거리 조건을 만족할 때만 판정합니다.
- 사용자의 준비 자세를 2~3초 캘리브레이션해서 체형 차이를 줄입니다.
- 세트 종료 후에는 실패 회차와 3D 리플레이를 보여줘 제품 완성도를 높입니다.

상용적으로 설득력 있는 핵심은 모델 정확도 하나가 아니라 전체 경험입니다. 실시간 skeleton overlay, 안정적인 rep count, 실패 회차 기록, 3D replay, 설명 가능한 피드백이 함께 있으면 텀프로젝트 데모에서도 완성도가 좋아 보입니다.

```text
좋은 데모 목표:
운동을 완벽하게 평가한다
  -> X

사용자가 어떤 회차에서 어떤 자세 요소가 흔들렸는지 이해하고 다시 볼 수 있다
  -> O
```

따라서 초기 앱의 표현은 `정확한 자세/틀린 자세` 이분법보다 `폼 점수`, `주의 항목`, `리플레이 근거` 중심으로 설계합니다.

## 사용할 기술

추천 구현 스택은 Android Native입니다.

- Android Kotlin: 앱 기본 구현 언어
- Jetpack Compose: 운동 선택, 카메라 화면, 결과 화면 UI
- CameraX: 실시간 카메라 프레임 수집
- MediaPipe Pose Landmarker: 33개 인체 landmark 추정
- Kotlin/JUnit: 각도 계산, rule engine, rep tracker 단위 테스트
- Kotlin serialization 또는 Room: 세션 결과와 replay frame 저장
- OpenCV: 필수 추론 엔진이 아니라, 오프라인 영상 테스트와 디버깅 보조 도구
- 3D replay: 초기에는 native Canvas pseudo-3D 또는 Three.js WebView 방식

Flutter로도 만들 수 있지만, 실시간 카메라 프레임과 MediaPipe Android SDK를 안정적으로 연결하려면 native bridge가 필요합니다. 그래서 제출 안정성을 우선하면 Android Native가 더 적합합니다.

## 시스템 구조

```text
Camera Frame
  -> Pose Estimator
  -> Landmark Confidence Filter
  -> Landmark Normalizer
  -> Angle and Alignment Calculator
  -> Exercise Rule Engine
  -> Rep and Set Tracker
  -> Real-Time Feedback UI
  -> Session Result and 3D Replay Storage
```

각 단계의 책임은 분리합니다.

- Pose Estimator: 카메라 프레임을 MediaPipe에 넘겨 landmark를 얻습니다.
- Confidence Filter: visibility가 낮은 관절은 판정에서 제외합니다.
- Landmark Normalizer: 골반 중심과 어깨 너비 기준으로 좌표를 정규화합니다.
- Geometry Calculator: 무릎, 팔꿈치, 어깨-엉덩이-발목 각도를 계산합니다.
- Exercise Rule Engine: 운동별 정자세 기준을 평가합니다.
- Rep Tracker: `위 -> 아래 -> 위` 같은 상태 전환으로 반복 횟수를 셉니다.
- Session Storage: 세트별 성공/실패 기록과 replay frame을 저장합니다.
- 3D Replay Viewer: 저장된 world landmark를 3D 스켈레톤으로 재생합니다.

## 정자세 판단 방식

정자세 판단에는 별도 학습 모델 없이 rule-based 방식으로 시작합니다. 포즈 추정 모델은 관절 좌표를 제공하고, 앱의 rule engine이 그 좌표를 해석합니다.

예를 들어 스쿼트는 다음처럼 판단합니다.

```text
무릎 각도가 충분히 작아졌는가?
상체 라인이 과하게 무너졌는가?
골반과 무릎, 발목의 상대 위치가 안정적인가?
standing -> bottom -> standing 상태 전환이 완성되었는가?
```

이 방식의 장점은 설명 가능성입니다. "왜 실패인가?"라는 질문에 "2회차 최저점에서 무릎 각도가 기준보다 커서 깊이 부족으로 판정했습니다"라고 설명할 수 있습니다.

추후 고도화 단계에서는 좋은 자세와 나쁜 자세 데이터를 모아 분류 모델을 학습할 수 있습니다. 하지만 초기 MVP에서는 데이터셋 구축보다 실시간 처리, 세트별 기록, 결과 재생 경험을 완성하는 것이 우선입니다.

## 추가 기능: 모델 학습 전략

모델 학습은 초기 MVP의 필수 기능이 아니라, 앱 개발과 병렬로 진행하는 추가 실험 기능입니다. 기본 앱은 MediaPipe landmark와 rule-based scoring만으로 먼저 완성하고, 학습 모델이 충분히 안정적으로 나오면 이후 버전에 병합합니다.

따라서 모델을 추가하더라도 처음부터 정자세를 완전히 판단하는 모델보다, 작은 범위의 보조 모델부터 붙이는 것이 좋습니다.

자세한 학습 실험 계획은 `docs/model-training-plan.md`에 정리합니다.
로컬 코드 작성, Google Drive 기반 Colab 실행, 완성 모델의 앱 병합 전략은 `docs/colab-drive-workflow.md`에 정리합니다.

모델 학습 트랙은 다음 방식으로 운영합니다.

```text
MVP 앱 개발
-> Android Native + MediaPipe + rule-based scoring
-> 모델 없이도 시연 가능해야 함

병렬 모델 실험
-> 로컬에서 학습 코드 작성
-> Google Drive/Colab에서 실행
-> 모델 성능이 충분하면 앱에 선택적으로 병합
```

1. 1차 목표: 운동 종류 분류
   - 입력: 30~90프레임 landmark sequence
   - 출력: squat, push-up, plank, unknown
   - 효과: 사용자가 운동을 직접 선택하지 않아도 되는 UX로 확장 가능

2. 2차 목표: 동작 단계 분류
   - 입력: landmark angle sequence
   - 출력: top, bottom, hold, transition
   - 효과: rep count가 더 안정적임

3. 3차 목표: 제한된 오자세 분류
   - 입력: 한 rep 단위 landmark sequence
   - 출력: correct, shallow, forward lean, body-line broken 등
   - 효과: rule-based 피드백을 보조하거나 비교할 수 있음

텀프로젝트에서는 1차와 2차만 학습 모델로 보여줘도 충분합니다. 정자세 품질 평가는 rule-based score로 유지하고, 오자세 분류 모델은 "실험 기능"으로 두는 편이 안전합니다. 모델 성능이 기대보다 낮으면 앱에는 넣지 않고, 학습 실험 결과만 보고서에 포함합니다.

## 3D 리플레이

MediaPipe Pose Landmarker는 normalized image coordinate뿐 아니라 world coordinate도 제공합니다. 이 값을 저장하면 운동 후에 사용자의 동작을 3D 스켈레톤 형태로 다시 볼 수 있습니다.

초기 구현은 실제 3D 아바타보다 3D stick figure viewer를 목표로 합니다.

```text
운동 중:
Camera -> Pose Landmarks -> Rule Evaluation -> Replay Frame 저장

운동 후:
Replay Frames -> 3D Skeleton Viewer -> 실패 구간 하이라이트
```

리플레이 화면에서는 슬라이더로 프레임을 이동하고, 실패한 rep의 문제 관절이나 뼈대를 빨간색으로 표시합니다. 단일 카메라 기반 3D 좌표는 의료급 모션캡처가 아니므로, 정확한 신체 치수 측정보다는 자세 흐름을 입체적으로 다시 보는 용도로 사용합니다.

## 구현 계획 요약

자세한 작업 계획은 `docs/plan.md`에 정리되어 있습니다.

큰 흐름은 다음과 같습니다.

1. Android 프로젝트를 구성합니다.
2. MediaPipe Pose Landmarker를 앱에 연결합니다.
3. 관절 좌표 자료구조와 정규화 로직을 만듭니다.
4. 각도 계산 유틸리티를 테스트 기반으로 구현합니다.
5. 스쿼트, 푸쉬업, 플랭크 rule engine을 만듭니다.
6. rep/set tracker로 세트별 실패 회차를 기록합니다.
7. 실시간 카메라 UI와 스켈레톤 overlay를 구현합니다.
8. 저장된 landmark 기반 3D replay 화면을 구현합니다.
9. 데모 스크립트와 테스트 영상을 준비합니다.

## 예상 결과 화면

- 운동 선택 화면
- 실시간 카메라 + skeleton overlay 화면
- 현재 rep count와 자세 피드백 표시
- 세트 종료 후 결과 요약
- 실패 회차 목록
- 3D skeleton replay와 실패 구간 하이라이트

예시 결과:

```text
1세트 결과
총 10회 중 8회 성공

실패 동작
- 2회차: 스쿼트 깊이 부족
- 6회차: 무릎 정렬 불안정
```

## 프로젝트 성공 기준

제출 가능한 MVP의 성공 기준은 다음입니다.

모델 학습 기능은 추가 기능이므로, 아래 기준은 학습 모델 없이도 달성 가능해야 합니다.

- 실제 Android 기기에서 카메라 preview가 동작합니다.
- 사람이 화면에 들어오면 skeleton overlay가 표시됩니다.
- 스쿼트 또는 푸쉬업 반복 횟수를 안정적으로 셉니다.
- 일부러 잘못 수행한 회차가 결과 화면에 실패로 남습니다.
- 실패 사유가 각도나 정렬 기준으로 설명됩니다.
- 저장된 landmark를 이용해 3D skeleton replay를 볼 수 있습니다.
- 앱이 낮은 confidence, 나쁜 카메라 구도, 사람이 화면 밖으로 나간 상황을 감지하고 판정을 보류합니다.

## 참고 문서

- MediaPipe Pose Landmarker: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker
- MediaPipe Android guide: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android
- ML Kit Pose Detection: https://developers.google.com/ml-kit/vision/pose-detection/android
- TensorFlow Lite Pose Estimation: https://www.tensorflow.org/lite/examples/pose_estimation/overview
