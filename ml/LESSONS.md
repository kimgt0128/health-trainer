# Model-Training Track — Lessons Log (compound engineering)

Running log of mistakes, surprises, and the rule each one produced. The literal
`/compound-engineering` command is **not installed** in this environment (the `ecc`
plugin marketplace is the nearest relative but ships no such command), so this file
fulfills its intent: record each mistake → fix → durable rule as the work proceeds.

Format per entry: **What happened → Why → Rule.**

---

## L1 — The harness said "not a git repository", but it was one
- **What:** Session env reported `Is a git repository: false`, and an early `ls` showed
  only `docs/` + `.claude/`. In reality the repo had commits, a `main` branch, and a
  parallel job actively committing on `mvp-0-scaffold` / `mvp-1-pose-geometry`.
- **Why:** The environment flag was stale / computed before scaffolding landed; the
  working tree changed under us between tool calls (parallel job moving refs).
- **Rule:** Never trust the env's git flag — run `git rev-parse --is-inside-work-tree`,
  `git log`, `git worktree list`, `git branch` before deciding. Re-check refs if values
  look inconsistent between calls; that means someone else is committing concurrently.

## L2 — `EnterWorktree` tool fails here; use raw `git worktree` + absolute paths
- **What:** `EnterWorktree` returned "current directory is not in a git repository"
  (same stale detection as L1), so the session could not switch cwd into the worktree.
- **Why:** Harness git-detection bug for this cwd.
- **Rule:** Create isolation with `git worktree add -b <branch> .claude/worktrees/<name> main`,
  then operate via **absolute paths** under that worktree and `git -C <worktree>` for all
  git ops. Isolation is achieved by *where you write*, not by the session cwd.

## L3 — Colab cannot be driven head-lessly by an MCP (free tier)
- **What:** Goal was "run training on Colab GPU via MCP, autonomously."
- **Why:** There is no free-Colab REST API. Google's official `colab-mcp` (Mar 2026)
  works only as a **browser-proxy**: a logged-in Colab tab must stay open and the user
  must click "Connect". The headless `--enable-runtime` mode is Google-internal only.
- **Rule:** Treat the Colab GPU run as **semi-manual** (user keeps a tab open + clicks
  Connect). Configure `colab-mcp` for that, but verify *code correctness* locally with a
  Datalayer Jupyter MCP against a local CPU kernel. For truly autonomous GPU, the honest
  options are Modal or Colab-Enterprise/Vertex (paid) — out of scope for this MVP.

## L4 — Model features MUST equal the app `:core` contract
- **What:** (Design-time guard.) The `ml/` feature pipeline and the Android `:core`
  normalization/angle code independently produce the inputs a model is trained on and
  later inferred with.
- **Why:** If hip-center/shoulder-width normalization, the `0.55` visibility gate, or the
  angle definition drift between the two, the model trains on one distribution and infers
  on another — inference silently corrupts. `docs/colab-drive-workflow.md` flags exactly
  this ("feature 순서가 바뀌면 앱 추론 결과가 완전히 틀어질 수 있다").
- **Rule:** Mirror `:core` constants exactly (visibility `0.55`, angle tolerance `0.5°`,
  hip-center translate + shoulder-width scale). The `feature_config.json` is the single
  contract; `contract.py` validates it and the artifact set on both sides.

## L5 — On this balanced tabular data, the *model family* beat any RF tuning
- **What:** Squat-form baseline (RandomForest) hit acc 0.884 / macroF1 0.883, with
  `asymmetric_squat` the weak class (recall 0.63, mostly mislabeled `correct`). A Colab
  tuning sweep (seed 42, same split) compared RF variants vs HistGradientBoosting:
  | model | acc | macroF1 | asym_recall |
  |---|---|---|---|
  | RF 100 (baseline) | 0.884 | 0.883 | 0.629 |
  | RF 300 | 0.887 | 0.885 | 0.634 |
  | RF 300 balanced_subsample | 0.888 | 0.887 | 0.633 |
  | RF 400 leaf2 sqrt | 0.887 | 0.885 | 0.631 |
  | **HistGradientBoosting** | **0.940** | **0.939** | **0.765** |
- **Why:** Classes were already balanced (~1581 each), so `class_weight`/more-trees barely
  moved the needle — `asymmetric_squat` was a *separability* problem. Gradient boosting
  captured the `correct`↔`asymmetric` boundary that bagged trees could not.
- **Rule:** When per-class counts are balanced and one class is weak, don't reach for
  `class_weight` first — try a different model family. HGB is now the default in
  `train_squat_form_classifier.py` (`--model hgb`); RF stays available as `--model rf`.
  Feature importances: knee angles (L/R) + `torso_lean` dominate; `symmetry_score` lowest.
