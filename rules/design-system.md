---
name: design-system
description: Health Trainer UI 디자인 규칙 — 결과 리포트 플로우의 모노크롬 비주얼 언어(토큰·타이포·컴포넌트·레이아웃 법칙·정직 원칙). Compose 화면을 작성·개선할 때 반드시 따른다.
applies-to: app/src/main/java/com/healthtrainer/app/ui/**, app/src/main/java/com/healthtrainer/app/replay/**
source: docs/wireframes/health-trainer-wireframe.html
---

# Health Trainer 디자인 시스템

스마트폰 결과 리포트의 비주얼 언어. 출처 = `docs/wireframes/health-trainer-wireframe.html`
("Overview first · set detail on demand · monochrome theme"). **새 화면/컴포넌트를 만들거나 기존
UI를 손볼 때 이 문서를 먼저 확인**하고, 토큰·컴포넌트·레이아웃 법칙을 따른다.

## 0. 핵심 법칙
1. **모노크롬.** 색상(hue)으로 정보를 전달하지 않는다. 강조는 **굵기·채움(fill)·배경 명도·라벨**로 한다.
   유일한 예외: replay 스켈레톤의 "정상/주의" 구분(기존 `SkeletonGraphics`)은 유지하되 리포트 화면은 무채색.
2. **Overview → detail-on-demand → replay.** 요약을 먼저 보이고, 상세는 탭/카드 진입으로, 리플레이는 그 끝에서.
   한 화면에 모든 수치를 쏟지 않는다.
3. **정직 (가장 중요).** 표시하는 모든 수치는 `:core` `SessionSummary`(룰이 측정·판단한 값)에서 온다.
   **숫자를 만들어내지 않는다.** 룰이 측정하지 않은 축(예: 스쿼트 무릎 valgus는 시상면 룰이 못 봄)은
   **표시하지 않거나 "측정 안 됨"으로 비활성**한다. 와이어프레임은 *참고*지 계약이 아니다.
4. **한글 줄바꿈.** 텍스트는 어절 단위로(`word-break: keep-all` 상응). 짧은 통계 라벨·태그는 nowrap.

## 1. 색 토큰 (Compose `Color`)
모노크롬 그레이스케일. `ui/theme/`에 토큰으로 두고 직접 hex를 흩뿌리지 않는다.

| 토큰 | hex | 용도 |
|------|-----|------|
| `bg` | `#F7F7F5` | 화면 배경 |
| `surface` | `#FFFFFF` | 카드·시트 |
| `ink` | `#101211` | 1차 텍스트·버튼 배경·채움 막대 |
| `ink2` | `#2A2D2B` | 2차 텍스트 |
| `muted` | `#737872` | 보조 텍스트·eyebrow·축 라벨 |
| `faint` | `#9DA39C` | 더 약한 텍스트 |
| `soft` | `#F1F2F0` | 선택 카드/탭 배경·트랙 |
| `soft2` | `#E9EBE8` | 트랙(빈 막대) |
| `line` | `#E0E3DF` | 1px 보더·구분선 |
| `lineStrong` | `#CFD4CE` | 강조 보더(선택 카드) |
| `camera` / `camera2` | `#121614` / `#1E2420` | replay 다크 캔버스 그라데이션 |

## 2. 타이포 스케일 (sp / weight)
| 역할 | size | weight | color |
|------|------|--------|-------|
| score (히어로 숫자) | 76 | 820 | ink |
| h1 (화면 타이틀) | 30 | 780 | ink |
| h2 (보조 타이틀) | 26 | 780 | ink |
| mini-stat 값 | 20 | 700 | ink |
| body | 14–15 | 400–600 | ink/ink2 |
| eyebrow (타이틀 위 한줄) | 13 | 400 | muted |
| section 헤더 | 12 | 780 | muted |
| 통계/축 라벨 | 12–13 | 600 | muted |

## 3. 형태 / 간격
- **Radius:** 카드 12–16, 칩/타일 10–12, 탭 8–11, 버튼 12.
- **보더:** 기본 1px `line`. 선택/강조 카드 = `soft` 배경 + `lineStrong` 보더.
- **패딩:** 화면 가장자리 22–24dp, 카드 12–16dp, 컴포넌트 간 gap 8–12dp.
- **버튼(primary):** 높이 52, radius 12, `ink` 배경, 흰 텍스트 15/760, 화면 하단 고정(`weight`로 본문 밀어내기).

## 4. 컴포넌트 (무상태 Composable, `ui/components/`)
각 컴포넌트는 `SessionSummary` 파생 데이터를 받아 표시만 한다(비즈니스 로직 금지, state hoisting).

- **HeroScore** — 큰 점수(76) + 우측 한줄 코멘트(muted 14). 입력: `overall: Int`, `comment: String`.
- **MiniStatTile** — `strong`(20) 값 + `span`(12 muted) 라벨. 3열 그리드(총 반복/안정/확인 등).
- **SetSummaryCard** — `N세트` + `점수 · 라벨`(우측) + **Sparkline**. 선택 시 `soft`+`lineStrong`.
- **Sparkline** — per-rep 점수(`reps[].overall`) 폴리라인. baseline(line-strong 1.2px) + ink 2.4px stroke.
- **SegmentedTabs** — 축 전환(`soft` 트랙, active=흰 배경+소프트 섀도우+ink). 입력: `axisKeys` + 선택.
- **MetricBar** — 라벨(72dp muted) + 트랙(`soft2`, 6dp, round) + `ink` fill(점수%) + 값(우측 strong). 축 점수용.
- **TrendChart** — per-rep 추세 라인(ink 3.5px) + 선택적 평균선(muted 2.4px, opacity .48) + 그리드(line 1px) + 축 라벨(11 muted). 눈에 띄는 회차는 흰 채움 점(ink stroke).
- **IssueChip** — 좌측 사유(strong) + 우측 태그(우선/확인/유지, muted). 강조형 = `soft`+`lineStrong`. 입력: `FeedbackCode`/라벨 + 태그.
- **CoachNote** — `#FAFAFA` 카드, 제목(14) + 불릿 행(muted 13, 앞 4dp 점). 텍스트는 dominant `FeedbackCode` 템플릿(결정적, NLG 아님).
- **ReplayCanvas** — 다크 그라데이션 + 좌상단 tracking pill(흰 알약, ink 12/720) + 스켈레톤.

## 5. 화면 구성 (참조)
- **결과 요약(ResultScreen):** eyebrow+h1 + 우상단 재시작(ghost) → HeroScore → 구분선 → MiniStat 3 → "세트별 요약" + SetSummaryCard들 → primary "N세트 상세 보기".
- **회차별 추세(SetDetailScreen, 신규):** eyebrow+h2 + back → SegmentedTabs → TrendChart 카드 → MetricBar(축별) → "눈에 띄는 회차" IssueChip들 → CoachNote → primary "문제 회차 리플레이".
- **Replay(Skeleton3DViewer):** eyebrow+h2 + back → ReplayCanvas → MiniStat(이 rep 축/시간) → "이 회차에서 보인 점" IssueChip → 팁 CoachNote → primary "전체 요약으로".

ghost 버튼 = 38×38, radius 10, 1px line, 흰 배경(← / ↺).

## 6. 데이터 바인딩 (`:core` → UI)
- 화면은 `MainViewModel`이 노출하는 `SessionSummary`(= `SessionSummarizer.summarize(session)`)만 본다.
- 점수 = `SessionSummary.overall` / `SetScore.overall` / `RepScore.overall`(전부 0..100 정수).
- MiniStat(총/안정/확인) = `totalReps` / `validReps` / `totalReps - validReps`.
- 막대 = `SetScore.axes`(또는 `SessionSummary.axes`)의 `AxisScore.score`, `failed`로 강조.
- 축 라벨/탭 = `AxisScore.key` → `FeedbackText`의 한글 매핑(`depth`→"깊이" 등). **키→라벨 매핑은 카탈로그 한 곳**.
- 이슈/코치노트 = `topIssues`(`IssueTally`) + invalid `RepScore.failures` → `FeedbackText`.
- 측정 없는 축은 렌더하지 않는다(법칙 3).
