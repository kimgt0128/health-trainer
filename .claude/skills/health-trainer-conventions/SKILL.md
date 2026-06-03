---
name: health-trainer-conventions
description: Health Trainer 프로젝트의 코드 규약 — :core/:app 모듈 구조, 패키지 네이밍(com.healthtrainer.*), 랜드마크 데이터 모델(LandmarkName/PoseLandmark/PoseFrame/Point3), :core 순수성 규칙, Gradle 멀티모듈 + Android SDK 조건부 include 설정, JUnit4+Truth 테스트 패턴. Health Trainer의 Kotlin 코드를 작성·수정·리뷰하거나 모듈 구조·패키지·테스트 위치를 결정할 때 반드시 참조.
---

# Health Trainer 코드 규약

이 문서는 Health Trainer 코드의 단일 진실 소스다. 새 코드는 주변 코드처럼 읽혀야 한다.

## 모듈 구조 (핵심)

프로젝트는 **2개 Gradle 모듈**로 나뉜다. 분리 이유는 *카메라/디바이스 없이 도메인 로직을 테스트*하기 위해서다.

```
health_trainer/
  settings.gradle.kts        # :core 항상 포함, :app은 Android SDK 있을 때만 조건부 포함
  build.gradle.kts           # 공통 플러그인 버전
  gradle/wrapper/            # Gradle wrapper (system gradle 불필요)
  core/                      # 순수 Kotlin/JVM — Android 의존성 0
    build.gradle.kts         # org.jetbrains.kotlin.jvm 플러그인
    src/main/kotlin/com/healthtrainer/core/
      geometry/   pose/   exercise/   tracker/   replay/(데이터 모델만)
    src/test/kotlin/com/healthtrainer/core/
  app/                       # Android — Compose/CameraX/MediaPipe (디바이스 필요)
    build.gradle.kts         # com.android.application 플러그인
    src/main/java/com/healthtrainer/app/
      MainActivity.kt  camera/  pose/(MediaPipe 어댑터)  ui/  replay/(뷰어)
```

### :core 순수성 규칙 (비협상)
`:core`의 어떤 파일도 `android.*`, `androidx.*`, `com.google.mediapipe.*`를 import하지 않는다.
- 이유: `:core`는 JDK만으로 `./gradlew :core:test`가 돌아야 한다. Android import가 섞이면 이 검증이 깨진다.
- 좌표/랜드마크는 `:core`가 정의한 자체 타입(`Point3`, `PoseLandmark`, `PoseFrame`)으로만 다룬다.
- MediaPipe 결과 → `PoseFrame` 변환은 `:app`의 어댑터 책임이다.

### 의존 방향
`:app → :core` 단방향. `:core`는 `:app`을 모른다.

## 패키지 네이밍
- `:core` → `com.healthtrainer.core.{geometry|pose|exercise|tracker|replay}`
- `:app` → `com.healthtrainer.app.{camera|pose|ui|replay}`

## 랜드마크 데이터 모델 (:core, pose 패키지)

```kotlin
enum class LandmarkName {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX
}

data class PoseLandmark(
    val name: LandmarkName,
    val x: Float, val y: Float, val z: Float,
    val visibility: Float,
)

data class PoseFrame(
    val timestampMs: Long,
    val landmarks: Map<LandmarkName, PoseLandmark>,
)
```

`Point3`는 geometry 패키지: `data class Point3(val x: Float, val y: Float, val z: Float)`.

## 정규화 규약
골반 중심 기준 평행이동 + 어깨 너비로 스케일:
```
hipCenter    = midpoint(LEFT_HIP, RIGHT_HIP)
shoulderWidth = distance(LEFT_SHOULDER, RIGHT_SHOULDER)
normalized   = (landmark - hipCenter) / shoulderWidth
```

## 신뢰도 필터
필요한 관절의 `visibility < 0.55`이면 그 프레임은 rule 평가에서 제외하고 `FeedbackCode.LOW_CONFIDENCE`로 표시한다.

## 테스트 규약
- 프레임워크: **JUnit4** + **Google Truth** (`assertThat(x).isWithin(t).of(v)`, `.contains(...)`).
- 위치: `core/src/test/kotlin/com/healthtrainer/core/{패키지}/{클래스}Test.kt`.
- 부동소수 비교는 항상 tolerance(`isWithin`)를 쓴다. 각도는 `0.5f`, 정규화 좌표는 `1e-4f` 권장.
- 테스트 메서드명은 `상황_기대결과` 형식: `rightAngle_returns90Degrees`, `shallowRep_marksDepthFailure`.
- rule/tracker 테스트는 합성 `PoseFrame`을 만들어 입력한다(카메라 불필요).

## Gradle 명령
- 도메인 테스트: `./gradlew :core:test`  ← 이 머신에서 실제 green 검증되는 명령
- 단일 클래스: `./gradlew :core:test --tests "com.healthtrainer.core.geometry.AngleCalculatorTest"`
- `:app`은 Android SDK가 있을 때만 `./gradlew :app:assembleDebug`로 빌드(이 머신엔 SDK 없음 → 미검증).

## settings.gradle.kts 조건부 include 패턴
Android SDK가 없는 환경에서도 `:core:test`가 막히지 않도록:
```kotlin
rootProject.name = "HealthTrainer"
include(":core")
val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (sdk != null && file(sdk).exists()) {
    include(":app")   // Android SDK 있을 때만 :app 구성
}
```
이유: 멀티모듈 빌드는 한 모듈만 테스트해도 전체를 *configure*한다. SDK 없이 `:app`(AGP)을 configure하면 실패하므로, SDK 부재 시 `:app`을 빌드 그래프에서 제외한다.

## 검증 현실 (정직하게)
- `:core` 로직 → 이 머신에서 **실제 테스트 통과로 검증됨**.
- `:app` 런타임(카메라, MediaPipe, Compose 렌더링) → **디바이스 필요, 이 세션에서 미검증**. 미검증 항목은 항상 그렇게 표기한다.
