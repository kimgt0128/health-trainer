# Health Trainer Design Rules

Health Trainer UI must feel like a restrained posture-coaching product, not a generic AI-generated fitness dashboard. Use the current result-flow wireframe in `docs/wireframes/health-trainer-wireframe.html` as the visual reference.

## Design Direction

- Reference mood: clean neutral product UI, similar in restraint to Cal.com-style interfaces.
- Product tone: calm, precise, coaching-oriented, and practical.
- Avoid: neon fitness styling, colorful dashboard charts, decorative gradients, excessive cards, floating blobs, large marketing hero sections, and generic "AI assistant" visuals.
- The interface should look useful during an actual workout, not like a landing page or concept poster.

## Theme

Use a monochrome theme by default.

```text
Background      #F7F7F5
Surface         #FFFFFF
Primary ink     #101211
Secondary ink   #2A2D2B
Muted text      #737872
Faint text      #9DA39C
Soft surface    #F1F2F0
Secondary soft  #E9EBE8
Line            #E0E3DF
Strong line     #CFD4CE
Camera dark     #121614
Camera dark 2   #1E2420
```

Do not introduce new theme colors unless there is a strong product reason. In particular, do not use separate green, yellow, or red blocks for good/warning/error states in the default UI.

### Status Styling

Status must be expressed through hierarchy, weight, border, tone, and short labels rather than separate colors.

Use:

- `확인` for normal items that need review.
- `우선` for the most important issue.
- `유지` for stable/maintained posture.
- Slightly darker border or soft gray background for priority.
- Bold text for the main issue.

Do not use:

- Bright green for "good".
- Yellow warning cards.
- Red error cards.
- Multi-color graphs unless a future accessibility review explicitly approves a semantic palette.

## Typography

Use system UI typography:

```css
font-family: Inter, Pretendard, -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", sans-serif;
letter-spacing: 0;
word-break: keep-all;
overflow-wrap: normal;
```

Rules:

- Use strong, compact headings.
- Avoid oversized hero text inside app screens.
- Avoid arbitrary Korean line breaks. Prefer shorter Korean phrases over long wrapped sentences.
- Important labels should stay on one line with `white-space: nowrap`.
- If text may overflow in compact UI, shorten the copy first. Use ellipsis only for secondary text.

Recommended heading sizes:

```text
Screen H1     30px / 780
Screen H2     26px / 780
Large score   74-76px / 820
Body          13-14px
Small label   12-13px / 680-780
```

## Layout

Use mobile-first app screens with restrained spacing.

- Phone mockup radius: about `30px`.
- Inner screen padding: `22-24px`.
- Component radius: `10-16px`, usually `12px`.
- Thin borders are preferred over heavy fills.
- Default gap between compact elements: `8-14px`.
- Section spacing: `18-26px`.
- Keep layouts scannable and dense enough for repeated use.

Avoid nested card stacks. A screen can have cards, but not every line needs a card. Let text lists and dividers carry simple information.

## Result Flow Information Architecture

The workout result experience should use progressive disclosure:

```text
Overall workout summary
-> Set-level summary
-> Set detail with trend graph
-> Specific rep replay
```

Reason: after a workout, users first need the answer to "How did I do overall?" Then they need to inspect "Which set got worse?" and only then "Which rep should I replay?" Do not put every graph, every rep, and every coaching note on one screen.

### Screen 1: Overall Summary

Purpose: show the whole workout at a glance.

Must include:

- Exercise name and completion context.
- Overall score.
- Total reps.
- Stable reps.
- Review-needed reps.
- Set-level summary list.
- Small sparkline per set showing set trend.
- Primary action to open the most relevant set detail.

Do not include:

- Full per-rep graph.
- Long coaching explanation.
- Skeleton replay.

### Screen 2: Set Detail

Purpose: show whether form held up or declined within one set.

Must include:

- Selected set title.
- Metric tabs such as `안정도`, `깊이`, `상체`.
- A line chart over rep index.
- Optional secondary trend line for set average or prior baseline.
- Metric bars for the selected set.
- Notable reps list.
- Short coach note with action items.
- Action to replay the most important rep.

The line chart should make end-of-set fatigue obvious. It should answer: "Did posture stay stable, improve, or degrade as reps accumulated?"

### Screen 3: Rep Replay

Purpose: inspect one problematic rep.

Must include:

- Set and rep number.
- Skeleton replay or pose frame.
- Short per-rep metrics.
- Issue list.
- Next-action coaching note.
- Return action to summary or detail.

## Charts

Charts should remain monochrome.

Use:

- Main line: primary ink.
- Secondary line: muted gray with lower opacity.
- Grid lines: light gray.
- Highlight points: white fill with black stroke.
- Axis labels: muted, small, readable.

Avoid:

- Color-coded series.
- Large chart legends.
- Decorative chart backgrounds.
- Overly dense axes.

Sparkline rules:

- Use sparklines only for set summaries.
- No axis labels in sparklines.
- Keep sparklines under roughly `40px` tall.
- Use them to show direction, not exact values.

## Components

### Buttons

- Primary action: black background, white text.
- Secondary action: white background, gray border, black text.
- Do not use green primary buttons in the monochrome theme.
- Button text must stay on one line.

### Issue Rows

Use a neutral issue row:

```text
2회차 · 깊이 부족        확인
7회차 · 자세 흔들림      우선
무릎 정렬은 안정         유지
```

The left side is the issue. The right side is the status label. Both should stay on one line.

### Coach Notes

Use `코치 노트` or `다음 동작 팁`, not `AI 보조 해석`.

Coach notes should be specific and short:

- Good: `후반 7회차부터 안정도가 내려갑니다.`
- Good: `다음 세트는 8회 기준으로 끊어도 좋습니다.`
- Bad: `AI 모델이 사용자의 운동 자세를 종합적으로 분석했습니다.`

Prefer bullet-like short rows over long paragraphs.

### Camera / Skeleton Area

- Use a dark neutral camera surface.
- Skeleton lines should be light gray/white.
- Problem joints may be shown through opacity or stroke weight, not bright warning colors.
- The camera panel should feel like a real product viewport, not a decorative illustration.

## Copywriting

Use concise Korean product copy.

Prefer:

```text
자세 확인
Squat 요약
회차별 추세
세트별 요약
자세 추적 중
코치 노트
문제 회차 리플레이
```

Avoid:

```text
자세를 확인해볼까요?
AI 보조 해석
Skeleton tracking
Primary theme
자세 안정도 분석 대시보드
```

Guidelines:

- Keep labels noun-like and compact.
- Keep body copy to one or two short sentences.
- Avoid explanatory UI text that describes the feature instead of helping the user decide what to do.
- Avoid Korean copy that wraps in the middle of a short phrase.

## Workout Report Scoring

Scores are an interpretive UI layer, not the source of truth. The source of truth remains rule-based rep/set records.

Recommended report hierarchy:

```text
overall_score
set_scores[]
rep_scores[]
failure_codes[]
coach_note
replay_entrypoint
```

Display failures as actionable posture review items, not alarms.

Example:

```text
Overall:
82 · 3세트 중 마지막 세트에서 자세 유지가 조금 떨어졌습니다.

Set:
2세트 · 82점 · 확인

Rep:
7회차 · 자세 흔들림 · 우선
```

## Implementation Guardrails

When implementing in Jetpack Compose:

- Build from the same information hierarchy as the wireframe.
- Keep the monochrome token set centralized.
- Do not reintroduce separate semantic color blocks for success/warning/error.
- Ensure Korean labels fit at common Android screen widths.
- Test compact layouts with the longest expected labels:
  - `10회차 · 피로 누적`
  - `7회차 · 자세 흔들림`
  - `Skeleton Replay 보기`
  - `전체 요약으로 돌아가기`
- Prefer lists and simple rows over dense cards.
- Use charts only when they answer a product question. For this app, the key chart question is: "Does form degrade as the set progresses?"

## Definition of Done for UI Changes

A UI change follows this design rule only if:

- It keeps the monochrome theme.
- It avoids isolated green/yellow/red status colors.
- It separates overview, set detail, and rep replay.
- It avoids awkward Korean wrapping.
- It uses short coaching copy.
- It makes the next action obvious.
- It feels like a real app screen, not a generated dashboard mockup.
