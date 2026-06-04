# Health Trainer

스마트폰 카메라로 관절 좌표를 추정하고 rule-based로 스쿼트·푸쉬업·플랭크 자세를 판정하는 Android 컴퓨터비전 앱. 세트별 실패 회차 기록 + 3D 스켈레톤 replay. 배경은 `READ.md`, 작업 계획은 `docs/plan.md`.

## 하네스: Health Trainer 개발

**목표:** 검증 가능한 `:core` 도메인 로직(TDD)과 `:app` Android 골격을 MVP 슬라이스 단위로 체계적으로 쌓아 올린다.

**트리거:** Health Trainer 기능 구현·MVP 진행·새 슬라이스 추가·이전 슬라이스 보완/재실행·워크트리 작업을 요청하면 `mvp-pipeline` 스킬을 사용하라. 단순 질문(개념 설명 등)은 직접 응답 가능. 머지/리베이스/cherry-pick 충돌이 발생하면 `conflict-resolver` 에이전트를 사용하라. 버그를 고치거나·교정받거나·구조 개선점을 발견하면 `compound-engineering` 스킬로 교훈을 `docs/LESSONS.md`에 기록하고 하네스에 되먹인다.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-03 | 초기 구성 (에이전트 5 + 스킬 3) | 전체 | READ.md/plan.md 기반 하네스 구축 |
| 2026-06-03 | 에이전트에 Skill 도구 추가 | agents/*.md | 디스패치된 서브에이전트가 참조 스킬(superpowers + 로컬)을 로드 가능하도록 |
| 2026-06-03 | rule↔tracker 계약 정정 (rep 유효성=aggregateRep 단독, 느슨한 descent 카운트, 보이는-프레임 분모) | skills/pose-rule-authoring | mvp-2 code-reviewer 피드백 반영 |
| 2026-06-03 | 유틸리티 스킬 2종 추가 (core-build-test 스크립트, pose-test-fixtures + 검증된 SyntheticPose 테스트 헬퍼) | skills/, core test util | 세션마다 반복된 빌드/테스트·합성 프레임 작업을 결정적 스크립트/헬퍼로 번들 |
| 2026-06-03 | 모델 학습 트랙 추가 (`ml/` 순수-Python TDD 코어 64 tests green + 프로젝트 스코프 MCP + ml 에이전트 4) | `ml/`, `.mcp.json`, `.claude/mcp`, `.claude/agents/ml-*.md` | `docs/model-training-plan.md` 병렬 구현; feature 계약을 앱 `:core`와 일치시켜 inference 드리프트 방지 |
| 2026-06-04 | PR 자동 리뷰 추가 (pr-reviewer 에이전트 + .github/workflows/pr-review.yml: :core 게이트 + LLM 리뷰) | agents/pr-reviewer.md, .github/ | PR마다 자동 코드 리뷰·판정 (code-reviewer 기반) |
| 2026-06-04 | conflict-resolver 에이전트 추가 (추가형 충돌만 자동 union, 의미충돌·삭제는 사용자 질의) | agents/conflict-resolver.md | 머지/리베이스 충돌 발생 시 체계적 확인·해결 (code-reviewer 기반) |
| 2026-06-04 | 운동 확장성 리팩터링 + 에이전트 아키텍처 원칙 (:core ExerciseRegistry/mode, :app MVVM/UDF UiState+FramePipeline+라벨 카탈로그) | :core, :app, agents/android-platform-engineer.md | 새 운동 추가를 등록 1줄로(open/closed), 깔끔한 Android 아키텍처 유지 |
| 2026-06-04 | :app 빌드 가능화 + 환경 현실 갱신 (settings SDK 탐지 강화: env→local.properties→기본경로; ExerciseScreen 잘못된 weight import 제거) | settings.gradle.kts, :app, CLAUDE.md | dev 머신에 SDK 생김 → 실제 :app:assembleDebug로 컴파일 검증, import 컴파일 에러 발견·수정 |
| 2026-06-04 | compound-engineering 스킬 + docs/LESSONS.md 추가 | skills/compound-engineering, docs/LESSONS.md | 실수→교훈→하네스 되먹임 실천(시드 4건) |

## 환경 현실 (매 세션 유의)
- 이 dev 머신: **JDK 17 + Android SDK(`~/Library/Android/sdk`, platform-35) 있음.** (CI·다른 환경은 SDK 없을 수 있음.)
- `:core`(순수 Kotlin/JVM) → `./gradlew :core:test`로 **실제 검증됨**. 도메인 로직은 모두 여기.
- `:app`(Android) → **이제 `./gradlew :app:assembleDebug`로 컴파일·APK 빌드 검증 가능.** 단 카메라/MediaPipe/렌더링 **런타임은 실기기 필요** → 런타임 미검증 항목은 `unverified (requires device)`로 표기한다.
- `:core`에 `android.*`/`androidx.*`/MediaPipe import 금지(순수성). 의존 방향은 `:app → :core` 단방향.
- `settings.gradle.kts`는 SDK를 env(`ANDROID_HOME`) → `local.properties`(`sdk.dir`) → 기본 경로 순으로 탐지해, 있을 때만 `:app`을 포함한다(SDK 없는 CI에서 `:core:test`가 막히지 않도록).
