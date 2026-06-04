---
name: pose-test-fixtures
description: Health Trainer의 rule/tracker 단위 테스트에서 목표 각도의 합성 PoseFrame을 손쉽게 만드는 방법 — 검증된 공유 헬퍼 com.healthtrainer.core.testutil.SyntheticPose. 스쿼트/푸쉬업/플랭크 rule 테스트나 rep/set tracker 테스트를 새로 작성·수정할 때, "무릎 각도 90도인 프레임", "팔꿈치 ~170도", "깊이 부족 회차" 같은 합성 입력이 필요할 때 반드시 사용. 좌표를 손으로 계산하지 말 것.
---

# 합성 PoseFrame 테스트 픽스처

rule/tracker 테스트는 "특정 관절 각도"를 가진 `PoseFrame`이 필요하다(예: 무릎 90°, 팔꿈치 170°). 좌표를 손으로 계산해 각도를 맞추는 것은 반복적이고 오류가 잦다(이 프로젝트의 초기 rule 테스트들이 각자 그렇게 했고 매번 수치 검증을 해야 했다). 대신 **검증된 공유 헬퍼**를 쓴다.

## 헬퍼 위치
`core/src/test/kotlin/com/healthtrainer/core/testutil/SyntheticPose.kt`
(자기 검증 테스트: 같은 폴더 `SyntheticPoseTest.kt`가 각도/길이/visibility 정확성을 고정한다.)

## 핵심 API
- `SyntheticPose.pointForAngle(a, b, degrees, length = 1f): Point3` — 각 A-B-C(꼭짓점 B)가 정확히 `degrees`가 되도록 점 C를 z=0 평면에 배치한다. 즉 `AngleCalculator.angleDegrees(a, b, c) ≈ degrees`(구성상 보장).
- `SyntheticPose.frame(timestampMs, vararg name to Point3, visibility = 0.9f): PoseFrame` — 프레임 조립. `visibility`를 0.55 미만으로 주면 저신뢰 경로를 테스트할 수 있다.
- `SyntheticPose.lm(name, x, y, visibility)` — 단일 랜드마크.

## 사용 예
무릎 각도 90°인 스쿼트 BOTTOM 프레임:
```kotlin
import com.healthtrainer.core.testutil.SyntheticPose
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName.*

val hip   = Point3(0f, 2f, 0f)
val knee  = Point3(0f, 1f, 0f)
val ankle = SyntheticPose.pointForAngle(a = hip, b = knee, degrees = 90f) // 무릎 각도 = 90°
val frame = SyntheticPose.frame(
    timestampMs = 0L,
    LEFT_HIP to hip,   RIGHT_HIP to hip,
    LEFT_KNEE to knee, RIGHT_KNEE to knee,
    LEFT_ANKLE to ankle, RIGHT_ANKLE to ankle,
    // 스쿼트 torso 평가용 어깨도 필요하면 추가
)
```
얕은(깊이 부족) 회차는 `degrees = 130f`처럼 BOTTOM 밴드(70~110) 밖이지만 깊이 실패(>120)에 해당하는 값을 주면 된다. 저신뢰 프레임은 `visibility = 0.3f`.

## 원칙
- **각도는 손으로 계산하지 말고 `pointForAngle`로 만든다.** 의도("90°를 원함")가 테스트에 그대로 드러나고, 수치 재검증이 불필요하다.
- 좌우 양쪽을 같은 점으로 미러링하면 rule의 좌우 평균 경로가 동작한다(한쪽만 주면 visible-side 경로).
- 기존 rule 테스트(mvp-1~3)는 이 헬퍼보다 먼저 작성되어 자체 픽스처를 쓴다 — **새 테스트는 이 헬퍼를 쓴다**. 기존 것을 굳이 리팩터링할 필요는 없다(green 유지가 우선).
- 헬퍼를 고치면 `SyntheticPoseTest`로 재검증한다([core-build-test] 스킬의 `core-test.sh`).
