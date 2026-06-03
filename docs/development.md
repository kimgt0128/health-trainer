# Development Guide

Health Trainer 개발/빌드/검증 안내와 MVP 진행 기록.

## 모듈 구조

| 모듈 | 성격 | 검증 |
|------|------|------|
| `:core` | 순수 Kotlin/JVM 도메인 로직 (geometry, pose 모델, normalizer, rule engine, rep/set tracker). Android 의존성 0. | `./gradlew :core:test` — JDK만으로 실행, **이 저장소에서 green 검증됨** |
| `:app` | Android 레이어 (CameraX, MediaPipe, Jetpack Compose, 3D replay). `:core`에 의존. | Android SDK + 실기기 필요. **현재 개발 머신엔 SDK 없음 → 미검증(requires device)** |

의존 방향은 `:app → :core` 단방향. `:core`에는 `android.*`/`androidx.*`/MediaPipe import가 들어가지 않는다(순수성). 이 분리 덕분에 카메라 없이 판정 로직 전체를 단위 테스트로 검증한다.

`settings.gradle.kts`는 `ANDROID_HOME`/`ANDROID_SDK_ROOT`가 있을 때만 `:app`을 빌드 그래프에 포함한다 — SDK 없는 환경에서 `:core:test`가 막히지 않도록.

## 빌드 & 테스트

```bash
# 도메인 로직 (이 저장소에서 검증되는 부분)
./gradlew :core:test
./gradlew projects                 # SDK 없으면 :core만 표시

# 앱 (Android SDK 필요)
export ANDROID_HOME=/path/to/sdk
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Gradle wrapper가 커밋되어 있어 system gradle 설치는 불필요하다.

## 검증 현황 (정직하게)

- **검증됨:** `:core`의 모든 도메인 로직 — 73개 JUnit/Truth 테스트 통과(각도 계산, 정규화, 스쿼트/푸쉬업/플랭크 rule, rep/set tracking, "1세트 2회차 실패" 판정).
- **미검증 (requires device):** `:app`의 카메라(CameraX), MediaPipe 추론, Compose 렌더링, 파일 IO, 권한 흐름. 디바이스 없이는 런타임 확인 불가하여 해당 코드에 `// requires device`로 표기.

## MVP 진행 기록 (main 브랜치, 슬라이스별 머지)

각 MVP는 `mvp-N-이름` 브랜치에서 작업 후 `--no-ff`로 main에 머지했다.

| 슬라이스 | 내용 | 검증 |
|----------|------|------|
| `mvp-0-scaffold` | Gradle 멀티모듈(`:core` JVM + `:app` Android), wrapper, 조건부 include | `:core:build` SUCCESSFUL |
| `mvp-1-pose-geometry` | Point3, AngleCalculator, Geometry, LandmarkName/PoseLandmark/PoseFrame, LandmarkNormalizer | TDD, 22 tests |
| `mvp-2-rule-engine` | ExerciseRule + Squat/PushUp/Plank rule (per-frame evaluate + rep-level aggregateRep) | TDD, +27 tests, code-reviewer GO |
| `mvp-3-rep-tracking` | RepStateMachine(느슨한 descent 카운트), SetTracker, 세션 레코드 | TDD, +19 tests, verification-qa PASS |
| `mvp-4-app-skeleton` | `:core` 라이브 카운트 API(+TDD) → `:app` 카메라/MediaPipe/Compose/replay 골격 | `:core` green; `:app` requires device |
| `mvp-5-docs-demo` | 데모 스크립트, 개발 가이드 | 문서 |

## 자세 판정 핵심 규약

- 임계값은 각 rule 클래스의 `companion object`에 있다(실제 영상으로 튜닝 가능).
- rep 유효성은 `ExerciseRule.aggregateRep`가 단독 판정한다 — 프레임 단위 `hardFailures`를 OR하지 않는다(비율 게이트 우회 방지). 프레임 단위 신호는 실시간 오버레이/replay 하이라이트용.
- rep 카운트는 정자세 BOTTOM 밴드보다 느슨한 descent 임계값으로 세서, 깊이 부족 회차도 카운트된 뒤 실패로 기록된다.
- 자세한 기준은 `.claude/skills/pose-rule-authoring/SKILL.md` 참조.

## Claude Code 하네스

이 저장소는 `.claude/`에 개발용 하네스를 둔다 — 전문 에이전트 5종(`android-architect`, `kotlin-tdd-engineer`, `android-platform-engineer`, `verification-qa`, `code-reviewer`)과 스킬 3종(`health-trainer-conventions`, `pose-rule-authoring`, `mvp-pipeline` 오케스트레이터). 트리거 규칙과 변경 이력은 `CLAUDE.md` 참조.
