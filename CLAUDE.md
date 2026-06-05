# Health Trainer

스마트폰 카메라로 관절 좌표를 추정하고 rule-based로 스쿼트·푸쉬업·플랭크 자세를 판정하는 Android 컴퓨터비전 앱. 세트별 실패 회차 기록 + 3D 스켈레톤 replay. 배경은 `READ.md`, 작업 계획은 `docs/plan.md`.

## 하네스: Health Trainer 개발

**목표:** 검증 가능한 `:core` 도메인 로직(TDD)과 `:app` Android 골격을 MVP 슬라이스 단위로 체계적으로 쌓아 올린다.

**트리거:** Health Trainer 기능 구현·MVP 진행·새 슬라이스 추가·이전 슬라이스 보완/재실행·워크트리 작업을 요청하면 `mvp-pipeline` 스킬을 사용하라. 단순 질문(개념 설명 등)은 직접 응답 가능. 머지/리베이스/cherry-pick 충돌이 발생하면 `conflict-resolver` 에이전트를 사용하라. 버그를 고치거나·교정받거나·구조 개선점을 발견하면 `compound-engineering` 스킬로 교훈을 `docs/LESSONS.md`에 기록하고 하네스에 되먹인다.

**디자인 라우팅:** 화면·UI(Compose)를 구현·개선·리뷰할 때는 **`rules/design-system.md`(디자인 규칙)를 먼저 확인**하고 그 토큰·타이포·컴포넌트·레이아웃 법칙·**정직 원칙(표시 수치는 `:core` 파생, 날조 금지)**을 따르라. `rules/`는 frontmatter(`applies-to`)로 적용 범위를 선언한다.

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
| 2026-06-04 | PR 한글 작성 규약 추가 | CLAUDE.md (작업 규약) | 사용자 요청: PR 제목·본문을 한글로 |
| 2026-06-04 | 푸쉬업 assist 모델 확장 (rep-level seam) + 확장 패턴 규약화 | :core(RepFeatureExtractor/PushUpFeatureExtractor), :app, ml/, docs | 스쿼트 구조 재사용; 종목 추가 시 seam 선택 + 레지스트리 1줄 |
| 2026-06-05 | ml-data-engineer에 "데이터셋 inspect-우선" 원칙 추가 | agents/ml-data-engineer.md, docs/LESSONS.md | 푸쉬업 데이터셋을 feature CSV로 가정해 전면 재작업 → 어댑터 작성 전 실제 구조 확인 강제 |
| 2026-06-05 | 디자인 시스템 + UI 라우팅 (`rules/design-system.md` 신설, CLAUDE.md/agents 라우팅, :core scoring/SessionSummary, 와이어프레임 결과 플로우 UI) | rules/, CLAUDE.md, agents/android-*, :core(scoring), :app(ui/theme·components·화면) | 결과 리포트를 룰 측정값에서 정직 파생; UI 작업 시 디자인 규칙 일관 적용 |

## 작업 규약
- **PR 제목과 본문은 한글로 작성한다.** (`gh pr create`의 `--title`/`--body` 모두 한글.) 코드 식별자·경로·지표·`feat:`/`fix:` 같은 conventional-commit 접두사는 영어 그대로 두되, 설명 문장은 한글로 쓴다.
- 커밋은 작은 단위로 쪼개고, 메시지 접두사는 conventional commit(`feat:`, `fix:`, `docs:`, `chore:` …)을 따른다.

## assist 모델 확장 패턴 (종목 추가 시)
운동별 optional form 모델은 rule engine **보조 신호**일 뿐이다 — **카운팅·valid 판정의 source of truth는 항상 `:core` rule + `RepStateMachine`/`SetTracker`(`aggregateRep`)**, 모델이 아니다. 모델 결과는 rep 종료 시점의 힌트로만 표시한다. 새 종목 모델은 다음 표준 경로로 붙인다(기존 파이프라인·ViewModel 변경 없음 = OCP):
1. **feature seam 선택** — 프레임 단위 판정이면 `:core` `ExerciseFeatureExtractor`(스쿼트), rep 전체 집계가 필요하면 `RepFeatureExtractor`(푸쉬업)를 구현. `featureNames()` 순서 = ml `feature_config` 순서(계약, 테스트로 고정 / 드리프트 단일 지점).
2. **레지스트리 한 줄** — `app/.../ml/FormClassifierRegistry`에 `Spec(extractor 또는 repExtractor, modelAsset, labels)` 등록(`forExercise`/`extractorFor`/`repExtractorFor`가 자동 소비).
3. **ml 트랙** — `ml/src/healthtrainer_ml/<운동>_pose_dataset.py` + `train_<운동>_form_classifier.py`(스쿼트/푸쉬업 미러, HGB 기본·heavy는 lazy import). 산출물 Drive `runs/` → 앱 `assets/models/<운동>_form.tflite`(gitignore). 모델 없으면 rules-only로 정상 동작(crash 금지).
4. **표시** — `FeedbackText.modelFormLabel`에 라벨 한글 매핑. `correct`류는 `fuseAssist`가 억제하고, 표현은 단정("틀림") 대신 "확인 필요" 톤.

## 환경 현실 (매 세션 유의)
- 이 dev 머신: **JDK 17 + Android SDK(`~/Library/Android/sdk`, platform-35) 있음.** (CI·다른 환경은 SDK 없을 수 있음.)
- `:core`(순수 Kotlin/JVM) → `./gradlew :core:test`로 **실제 검증됨**. 도메인 로직은 모두 여기.
- `:app`(Android) → **이제 `./gradlew :app:assembleDebug`로 컴파일·APK 빌드 검증 가능.** 단 카메라/MediaPipe/렌더링 **런타임은 실기기 필요** → 런타임 미검증 항목은 `unverified (requires device)`로 표기한다.
- `:core`에 `android.*`/`androidx.*`/MediaPipe import 금지(순수성). 의존 방향은 `:app → :core` 단방향.
- `settings.gradle.kts`는 SDK를 env(`ANDROID_HOME`) → `local.properties`(`sdk.dir`) → 기본 경로 순으로 탐지해, 있을 때만 `:app`을 포함한다(SDK 없는 CI에서 `:core:test`가 막히지 않도록).
