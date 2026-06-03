---
name: pose-rule-authoring
description: Health Trainer의 운동별 정자세 rule engine을 작성·확장하는 도메인 지식 — ExerciseRule 인터페이스 계약, MovementPhase/FeedbackCode 의미, 스쿼트/푸쉬업/플랭크 자세 기준 임계값, rep state machine이 phase를 소비하는 방식. 새 운동 rule을 추가하거나 자세 판정 기준·임계값을 작성·튜닝할 때 반드시 참조.
---

# 자세 Rule 작성 가이드

Health Trainer의 자세 판정은 학습 모델 없이 **설명 가능한 rule-based**다. 포즈 모델은 좌표를 주고, rule engine이 각도·정렬·상태 전환으로 해석한다. "왜 실패인가?"에 "2회차 최저점 무릎 각도가 기준보다 커서 깊이 부족"처럼 답할 수 있어야 한다.

## 공통 타입 계약 (:core exercise 패키지)

```kotlin
enum class ExerciseType { SQUAT, PUSH_UP, PLANK }

enum class MovementPhase { READY, TOP, BOTTOM, HOLD, UNKNOWN }

enum class FeedbackCode {
    LOW_CONFIDENCE,
    SQUAT_DEPTH_NOT_ENOUGH, SQUAT_TORSO_LEAN,
    PUSH_UP_DEPTH_NOT_ENOUGH, PUSH_UP_BODY_LINE_BROKEN,
    PLANK_HIPS_LOW, PLANK_HIPS_HIGH, PLANK_ELBOW_MISALIGNED,
}

data class ExerciseFeedback(
    val phase: MovementPhase,
    val hardFailures: Set<FeedbackCode>,   // rep 무효 사유
    val softWarnings: Set<FeedbackCode>,   // 경고(무효 아님)
    val metrics: Map<String, Float>,       // 디버그/리플레이용 측정값(예: "kneeAngle")
)

interface ExerciseRule {
    fun evaluate(frame: PoseFrame): ExerciseFeedback
}
```

규칙:
- **hardFailures**는 rep을 무효로 만든다. **softWarnings**는 표시만 한다.
- 필요한 관절 `visibility < 0.55`이면 평가 스킵 → `phase = UNKNOWN`, `hardFailures`에 비우고 `softWarnings`에 `LOW_CONFIDENCE`(또는 별도 처리). 합의: LOW_CONFIDENCE는 hard가 아니라 평가 불가 신호.
- 좌우 모두 visibility ≥ 0.55면 좌우 각도 평균을 쓴다. 한쪽만 보이면 보이는 쪽을 쓴다.
- **임계값은 각 rule 클래스의 `companion object` 상수**로 둔다(실제 영상 테스트 후 튜닝). 출처(plan.md)를 주석에 남긴다.

## 자세 기준 임계값 (MVP 기본값, 측면 구도)

### SquatRule
- 사용 관절: HIP, KNEE, ANKLE, SHOULDER (카메라 가까운 쪽).
- `standing(TOP)`: 무릎 각도 ≥ `160°`.
- `bottom(BOTTOM)`: 무릎 각도 `70°..110°`.
- **깊이 실패** `SQUAT_DEPTH_NOT_ENOUGH`: rep 동안 최소 무릎 각도가 `120°` 초과로 유지.
- **상체 경고** `SQUAT_TORSO_LEAN`(soft): 하강 중 shoulder-hip-ankle 각도 < `145°`.
- rep 완성: `standing → bottom → standing`.

### PushUpRule
- 사용 각도: 팔꿈치(shoulder-elbow-wrist), 몸통(shoulder-hip-ankle).
- `up(TOP)`: 팔꿈치 각도 ≥ `155°`.
- `down(BOTTOM)`: 팔꿈치 각도 `70°..100°`.
- **깊이 실패** `PUSH_UP_DEPTH_NOT_ENOUGH`: rep 동안 최소 팔꿈치 각도가 `105°` 초과.
- **몸통 실패** `PUSH_UP_BODY_LINE_BROKEN`(hard): shoulder-hip-ankle 각도 < `160°`인 프레임이 rep의 `30%` 초과.
- rep 완성: `up → down → up`.

### PlankRule
- 정적 hold. rep 카운트가 아니라 유지 품질.
- `phase = HOLD`.
- **몸통 정렬 유효**: shoulder-hip-ankle 각도 ≥ `160°`. 미달 시 각도가 너무 작으면(엉덩이 처짐) `PLANK_HIPS_LOW`, 너무 크면(엉덩이 솟음) `PLANK_HIPS_HIGH`.
- **팔꿈치 위치**(soft) `PLANK_ELBOW_MISALIGNED`: 정규화 좌표에서 팔꿈치가 어깨 바로 아래에서 수평 거리 `0.25` 초과로 벗어남.
- hold 성공: 분석 프레임의 `70%` 이상에서 유효 자세 유지.

## rep state machine과의 계약 (tracker 패키지)
- 스쿼트/푸쉬업: `TOP → BOTTOM → TOP` 전이가 완성될 때만 rep 1회 카운트.
- rep window 동안 `hardFailures`가 한 번이라도 모이면 그 rep은 `valid = false`.
- 푸쉬업 몸통 실패는 비율 기준(broken 프레임 > 30%)이므로, tracker가 rep window의 프레임을 누적해 판정한다.
- 플랭크: 사용자가 타이머를 멈출 때 hold 레코드 1개 생성.
- 실패 회차는 `1세트 2회차`처럼 `setNo`/`repNo`로 식별된다.

## 테스트 작성 팁
합성 `PoseFrame`으로 각 phase와 실패를 재현한다:
```kotlin
assertThat(result.phase).isEqualTo(MovementPhase.BOTTOM)
assertThat(result.hardFailures).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
```
각 rule당 최소: TOP 인식, BOTTOM 인식, 정상 rep(실패 없음), 의도된 실패 1종.

## 새 운동 추가 시
1. `FeedbackCode`에 해당 운동 코드 추가.
2. `ExerciseRule`을 구현하는 `{Exercise}Rule` 클래스 작성(임계값은 companion object).
3. RED 테스트부터(phase 전이 + 실패 코드).
4. tracker가 새 phase 패턴을 처리하는지 verification-qa로 경계면 확인.
