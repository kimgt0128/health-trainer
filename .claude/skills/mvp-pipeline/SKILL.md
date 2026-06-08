---
name: mvp-pipeline
description: Health Trainer를 MVP 슬라이스 단위로 체계적으로 구현하는 오케스트레이터. main에서 슬라이스별 브랜치/워크트리를 만들고, 슬라이스마다 설계→TDD구현/골격→검증→리뷰→머지 파이프라인으로 진행한다. Health Trainer 기능 구현·MVP 진행·새 슬라이스 추가·이전 슬라이스 보완·재실행·워크트리 작업을 요청하면 사용. 단순 질문은 직접 응답.
---

# MVP 파이프라인 오케스트레이터

Health Trainer를 "체계적으로 진행한 프로젝트"로 만드는 워크플로우. 전문가 풀(에이전트) + 생성-검증 하이브리드.

## 기반 superpowers 스킬
- `superpowers:using-git-worktrees` — 슬라이스별 격리 작업공간
- `superpowers:executing-plans` / `superpowers:subagent-driven-development` — 계획 실행
- `superpowers:finishing-a-development-branch` — 슬라이스 완료/머지 결정
- `superpowers:requesting-code-review`, `superpowers:verification-before-completion`

## Phase 0: 컨텍스트 확인
시작 시 현재 상태를 판별해 실행 모드를 정한다:
- `_workspace/` 없음 + 슬라이스 미시작 → **초기 실행** (mvp-0부터)
- 일부 슬라이스가 main에 머지됨 → **이어가기** (다음 미완 슬라이스)
- 사용자가 특정 슬라이스 수정/보완 요청 → **부분 재실행** (해당 슬라이스 브랜치에서 해당 에이전트만 재호출)
- `git worktree list`로 떠 있는 워크트리 확인.

## MVP 슬라이스 (브랜치 = `mvp-N-이름`)
의존 순서대로. 각 슬라이스는 독립 브랜치에서 작업 후 main에 머지한다.

| 브랜치 | 모듈 | 내용 | 검증 |
|--------|------|------|------|
| `mvp-0-scaffold` | root | Gradle 멀티모듈, wrapper, settings 조건부 include, .gitignore | `:core` configure 통과 |
| `mvp-1-pose-geometry` | :core | LandmarkName/PoseLandmark/PoseFrame, Point3, AngleCalculator, LandmarkNormalizer | `:core:test` green (TDD) |
| `mvp-2-rule-engine` | :core | ExerciseType/FeedbackCode/MovementPhase/ExerciseFeedback/ExerciseRule, Squat/PushUp/Plank Rule | `:core:test` green (TDD) |
| `mvp-3-rep-tracking` | :core | RepRecord/SetRecord/ExerciseSession, RepStateMachine, SetTracker | `:core:test` green (TDD) |
| `mvp-4-app-skeleton` | :app | MainActivity, MediaPipe 어댑터, CameraPreview, SkeletonOverlay, ExerciseScreen, ResultScreen, 3D viewer (골격) | 컴파일 구조만, 런타임 미검증(requires device) |
| `mvp-5-docs-demo` | docs | demo-script.md, 진행 현황 문서 갱신 | 문서 리뷰 |

> 슬라이스 범위는 고정이 아니다. 사용자 요청에 맞춰 android-architect가 재분해할 수 있다.

## 슬라이스 파이프라인 (각 브랜치에서)
**실행 모드:** 전문가 풀(슬라이스 성격에 맞는 에이전트 dispatch) + 생성-검증.

1. **브랜치/워크트리 생성** — `superpowers:using-git-worktrees`로 `mvp-N-이름` 격리 작업공간 확보. (main은 항상 깨끗하게 유지) **워크트리/repo-level git 작업은 메인 repo 절대경로로 한다**(`git -C /abs/main worktree add …`). 워크트리 안에서 `git rev-parse --show-toplevel`은 *현재 워크트리* 루트를 반환하므로 메인 repo 경로 대용으로 쓰면 워크트리가 중첩 생성된다(LESSONS 2026-06-05).
2. **설계** — `android-architect`로 `_workspace/mvp-N-plan.md` 작성(파일 목록·단계·검증 명령). 범위가 명확하면 생략 가능.
3. **구현:**
   - `:core` 슬라이스 → `kotlin-tdd-engineer` (RED→GREEN→REFACTOR, 실제 `:core:test` green)
   - `:app` 슬라이스 → `android-platform-engineer` (골격, 미검증 표기)
4. **검증** — `verification-qa`로 `:core:test` 실행 + 경계면 교차 비교. 실패 시 3으로 복귀.
5. **리뷰** — `code-reviewer`로 diff 검토(:core 순수성, TDD, 계약, 규약). blocker 있으면 3으로 복귀.
6. **커밋 & 머지** — 의미 단위 커밋 후 `--no-ff`로 main에 머지(슬라이스 경계가 히스토리에 남도록). `superpowers:finishing-a-development-branch`로 정리.

모든 Agent 호출은 `model: "opus"`로 한다.

## 커밋 컨벤션
- 한 커밋 = 한 논리 변경. RED 테스트와 GREEN 구현을 의미 있게 나눈다.
- 메시지: `<slice>: <변경>` 예) `mvp-1: add AngleCalculator with right/straight angle tests`.
- 머지: `git merge --no-ff mvp-N-이름` (슬라이스 단위 보존).

## 데이터 전달
- 슬라이스 계획·중간 산출물: `_workspace/mvp-N-*.md` (파일 기반, 감사 추적).
- 에이전트 결과: Agent 반환값으로 메인이 수집.
- 최종 코드: `:core`/`:app` 소스 + git 히스토리.

## 에러 핸들링
- 에이전트 1회 재시도 후 재실패 시: 결과 없이 진행하되 보고서에 누락 명시(임의 봉합 금지).
- 테스트 실패: `superpowers:systematic-debugging`로 근본 원인. 증상만 가리지 않는다.
- 환경 한계(Android SDK 부재로 :app 미검증): 통과로 위장하지 않고 `unverified (requires device)`로 정직하게 보고.
- 상충하는 임계값: plan.md 기준을 따르고 출처 주석. 임의 삭제 금지.

## 테스트 시나리오
**정상 흐름:** "스쿼트 rule 구현" → Phase 0(이어가기 판별) → `mvp-2-rule-engine` 브랜치 → architect 계획 → kotlin-tdd-engineer가 RED(BOTTOM 인식, 깊이 실패) → GREEN → qa가 `:core:test` green + rule↔tracker 경계 확인 → reviewer가 :core 순수성 확인 → `--no-ff` 머지.

**에러 흐름:** kotlin-tdd-engineer가 `:core:test` 실패 보고 → systematic-debugging으로 각도 공식 부호 오류 발견 → 수정 → 재실행 green → 진행. (실패를 숨기고 머지하지 않는다.)
