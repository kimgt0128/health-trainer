# Demo Script

Health Trainer 데모는 두 부분으로 나뉜다. (A) 카메라/디바이스 없이도 보여줄 수 있는 **핵심 로직 데모**와, (B) 실제 Android 기기에서 보여주는 **앱 데모**.

## A. 핵심 로직 데모 (디바이스 불필요)

rule engine과 rep/set tracker는 순수 Kotlin `:core` 모듈에 있고 단위 테스트로 검증된다. 카메라 없이 "왜 실패인가"를 설명할 수 있다는 점이 이 프로젝트의 핵심이다.

```bash
./gradlew :core:test
```

기대 결과: `BUILD SUCCESSFUL`, 73개 테스트 통과.

특히 다음 테스트가 "1세트 2회차 실패" 기능을 그대로 증명한다.

```
SetTrackerTest > squatSet_secondRepDepthFailure_identifiedBySetAndRepNo
```

이 테스트는 합성 스쿼트 세트(정상 → 깊이 부족 → 정상)를 넣고, 2회차가 `SQUAT_DEPTH_NOT_ENOUGH`로 무효 기록되는지(`records[1].repNo == 2`, `valid == false`) 검증한다. 깊이가 부족한 회차도 느슨한 descent 임계값 덕분에 카운트된 뒤 실패로 남는다는 것을 보여준다.

설명 포인트:
- 자세 판정은 학습 모델이 아니라 각도/정렬 rule이다 → 모든 실패에 설명 가능한 사유가 붙는다.
- 임계값은 각 rule 클래스의 `companion object`에 있어 실제 영상으로 튜닝 가능하다.
- rep 유효성은 `aggregateRep`가 단독 판정한다(프레임 단위 신호의 OR이 아님) → 푸쉬업 몸통처럼 "전체의 30% 초과" 같은 비율 기준이 정확히 적용된다.

## B. 앱 데모 (Android 기기 필요)

> 현재 개발 머신에는 Android SDK가 없어 `:app`은 빌드/실행 검증되지 않았다. 아래는 SDK + 실기기가 있을 때의 절차다.

사전 준비:
1. `ANDROID_HOME`(또는 `ANDROID_SDK_ROOT`)을 설정하면 `settings.gradle.kts`가 `:app`을 빌드 그래프에 포함한다.
2. MediaPipe 모델을 받는다(런타임 의존, git에는 포함하지 않음):
   ```bash
   mkdir -p app/src/main/assets
   curl -L "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task" \
     -o app/src/main/assets/pose_landmarker_lite.task
   ```
3. 설치: `./gradlew :app:installDebug`

데모 흐름:
1. 앱 실행 후 **스쿼트**를 선택한다.
2. 측면 카메라 구도로 선다(MVP 기본 구도).
3. 정상 스쿼트 1회 → **일부러 깊이가 부족한 스쿼트 1회** → 정상 스쿼트 1회를 수행한다.
4. 화면에서 실시간 rep count와 skeleton overlay(정상 초록 / 경고 노랑 / 실패 빨강)를 확인한다.
5. 세트 종료 후 결과 화면에서 `1세트 2회차: 스쿼트 깊이 부족` 실패 기록을 확인한다.
6. 3D 리플레이 슬라이더로 실패 구간이 빨간색으로 표시되는지 보여준다.
7. 푸쉬업 또는 플랭크로 전환해 같은 rule engine 구조가 다른 운동에도 적용됨을 설명한다.

## 권장 시연 순서

1. 먼저 A(핵심 로직 데모)로 "판정 로직이 테스트로 검증된다"를 보여준다.
2. 이어 B(앱 데모)로 실제 카메라 경험을 보여준다.

A만으로도 프로젝트의 핵심(설명 가능한 rule-based 자세 판정 + 세트별 실패 회차 기록)은 완전히 시연 가능하다.
