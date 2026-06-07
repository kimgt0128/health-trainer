package com.healthtrainer.app.ui

import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.tracker.RepRecord

/**
 * Pure presentation mapping for `:core` [FeedbackCode]s: Korean labels, severity buckets, and the
 * `"N세트 M회차: <사유>"` invalid-rep line.
 *
 * Intentionally free of Android imports so it stays unit-testable as plain Kotlin (no test required
 * this slice, but it is the one piece of `:app` logic worth keeping verifiable by inspection).
 *
 * The label / severity table mirrors §4 of the mvp-4 plan and the `pose-rule-authoring` skill:
 * hard failures drive a red overlay, soft warnings yellow, and [FeedbackCode.LOW_CONFIDENCE] is
 * neither (the frame simply couldn't be evaluated).
 */
object FeedbackText {

    /** Display severity of a code, used to pick the overlay color and group result lines. */
    enum class Severity { HARD, SOFT, INFO }

    /**
     * Korean label for a scoring AXIS key (`SessionSummary`/`SetScore`/`RepScore` `AxisScore.key`).
     * THE one catalog for axis-key -> label (design-system §6: "키→라벨 매핑은 카탈로그 한 곳"). The
     * keys are the ones the `:core` scorers emit (SquatScorer/PushUpScorer/PlankScorer):
     * squat `depth`/`torso`, push-up `depth`/`body_line`, plank `body_line`/`elbow`.
     *
     * Unknown keys fall back to the raw key (forward-compatible with a new scorer axis) rather than
     * crashing.
     */
    fun axisLabel(key: String): String = when (key) {
        "depth" -> "깊이"
        "torso" -> "상체"
        "body_line" -> "몸통 일직선"
        "elbow" -> "팔꿈치 정렬"
        else -> key
    }

    /** Korean label shown to the user for a [FeedbackCode]. */
    fun label(code: FeedbackCode): String = when (code) {
        FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH -> "스쿼트 깊이 부족"
        FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH -> "푸쉬업 깊이 부족"
        FeedbackCode.PUSH_UP_BODY_LINE_BROKEN -> "몸통 일직선 무너짐"
        FeedbackCode.PLANK_HIPS_LOW -> "엉덩이 처짐"
        FeedbackCode.PLANK_HIPS_HIGH -> "엉덩이 솟음"
        FeedbackCode.SQUAT_TORSO_LEAN -> "상체 과도하게 숙임"
        FeedbackCode.PLANK_ELBOW_MISALIGNED -> "팔꿈치 정렬 어긋남"
        FeedbackCode.LOW_CONFIDENCE -> "자세 인식 불가"
    }

    /**
     * Korean label for an OPTIONAL on-device form-classifier class (e.g. `"knees_caving_in"`). These
     * are an ASSIST signal layered on top of the rule feedback — note that three of them
     * (knees-caving / heels-off / asymmetric) are squat faults the sagittal rule engine can't see.
     *
     * Unknown labels (a model trained with classes we don't map yet) fall back to the raw string
     * rather than crashing — forward-compatible with a re-exported model. `"correct"` is included for
     * completeness but the fusion gate suppresses it before it ever reaches the UI.
     */
    fun modelFormLabel(label: String): String = when (label) {
        "correct" -> "좋은 자세 (모델)"
        "shallow_squat" -> "스쿼트 깊이 부족 (모델)"
        "forward_lean" -> "상체 과도하게 숙임 (모델)"
        "knees_caving_in" -> "무릎이 안쪽으로 모임 (모델)"
        "heels_off_ground" -> "뒤꿈치가 들림 (모델)"
        "asymmetric_squat" -> "좌우 비대칭 (모델)"
        "incorrect" -> "푸쉬업 자세 확인 필요 (모델)"
        "hips_low" -> "엉덩이 처짐 확인 필요 (모델)"
        "hips_high" -> "엉덩이 솟음 확인 필요 (모델)"
        else -> "$label (모델)"
    }

    /** Severity bucket of a [FeedbackCode] (§4 of the plan). */
    fun severity(code: FeedbackCode): Severity = when (code) {
        FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH,
        FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH,
        FeedbackCode.PUSH_UP_BODY_LINE_BROKEN,
        FeedbackCode.PLANK_HIPS_LOW,
        FeedbackCode.PLANK_HIPS_HIGH -> Severity.HARD

        FeedbackCode.SQUAT_TORSO_LEAN,
        FeedbackCode.PLANK_ELBOW_MISALIGNED -> Severity.SOFT

        FeedbackCode.LOW_CONFIDENCE -> Severity.INFO
    }

    /**
     * The `"N세트 M회차: <사유1·사유2>"` line for a failed rep (rep-level [RepRecord.failures]).
     *
     * Example: a shallow 2nd-rep squat — `RepRecord(setNo=1, repNo=2,
     * failures={SQUAT_DEPTH_NOT_ENOUGH})` — renders **`1세트 2회차: 스쿼트 깊이 부족`**.
     *
     * Failures are joined with `·`. A valid rep (empty failures) yields just the
     * `"N세트 M회차"` prefix, but callers should only render this for invalid reps.
     */
    fun invalidRepLine(rep: RepRecord): String {
        val reasons = rep.failures.joinToString("·") { label(it) }
        val prefix = "${rep.setNo}세트 ${rep.repNo}회차"
        return if (reasons.isEmpty()) prefix else "$prefix: $reasons"
    }

    // ---- Result-report presentation (design-system §4/§6) --------------------------------------

    /**
     * The short tag shown on the right of an [com.healthtrainer.app.ui.components.IssueChip], derived
     * deterministically from the code's [severity]: a HARD failure is "우선"(fix first), a SOFT one is
     * "확인"(check), and an INFO signal is "유지"(keep/neutral). NOT free-form — it is a pure function
     * of severity so the same code always tags the same way.
     */
    fun issueTag(code: FeedbackCode): String = when (severity(code)) {
        Severity.HARD -> "우선"
        Severity.SOFT -> "확인"
        Severity.INFO -> "유지"
    }

    /**
     * A deterministic, templated coaching cue for a single [FeedbackCode] — the bullet text of a
     * [com.healthtrainer.app.ui.components.CoachNote]. This is a fixed lookup (NOT generative NLG): one
     * phrase per code, so the note is reproducible and reviewable. The advice mirrors the axis the
     * code maps to in the `:core` scorers.
     */
    fun coachCue(code: FeedbackCode): String = when (code) {
        FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH -> "허벅지가 무릎 높이 아래로 내려가도록 더 깊게 앉아 보세요."
        FeedbackCode.SQUAT_TORSO_LEAN -> "가슴을 세우고 시선을 정면에 두어 상체가 앞으로 숙여지지 않게 하세요."
        FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH -> "가슴이 바닥에 가까워지도록 팔꿈치를 더 굽혀 내려가세요."
        FeedbackCode.PUSH_UP_BODY_LINE_BROKEN -> "머리부터 발끝까지 한 줄이 되도록 복부와 엉덩이에 힘을 주세요."
        FeedbackCode.PLANK_HIPS_LOW -> "엉덩이가 처지지 않도록 복부를 조여 골반을 살짝 들어 올리세요."
        FeedbackCode.PLANK_HIPS_HIGH -> "엉덩이가 솟지 않도록 골반을 내려 몸을 일직선으로 맞추세요."
        FeedbackCode.PLANK_ELBOW_MISALIGNED -> "팔꿈치가 어깨 바로 아래에 오도록 위치를 맞추세요."
        FeedbackCode.LOW_CONFIDENCE -> "전신이 화면 안에 들어오도록 카메라 위치를 조정하세요."
    }

    /**
     * Title + ordered bullets for a [com.healthtrainer.app.ui.components.CoachNote], built from the
     * dominant [FeedbackCode]s of a set/session (the issue tally already sorted descending by count).
     * Deterministic: take up to [maxCues] codes in the given order and map each via [coachCue]. When
     * there are no issues, returns an encouraging single bullet (still fixed text, not generated).
     *
     * @param dominant codes ordered by importance (e.g. `topIssues.map { it.code }` or a set's failure
     *                 frequency), already excluding [FeedbackCode.LOW_CONFIDENCE] upstream if desired.
     */
    fun coachNote(dominant: List<FeedbackCode>, maxCues: Int = 3): CoachNoteText {
        val cues = dominant.distinct().take(maxCues).map(::coachCue)
        return if (cues.isEmpty()) {
            CoachNoteText(title = "잘하고 있어요", bullets = listOf("이 페이스로 자세를 유지하세요."))
        } else {
            CoachNoteText(title = "다음에 신경 쓸 점", bullets = cues)
        }
    }

    /** A single replay tip from the replayed rep's dominant failure (or a neutral keep-it-up line). */
    fun replayTip(failures: Collection<FeedbackCode>): CoachNoteText {
        val dominant = failures.filter { it != FeedbackCode.LOW_CONFIDENCE }
        return coachNote(dominant, maxCues = 1)
    }

    /** Plain title + bullet text for a coach note (no Compose types — kept unit-testable). */
    data class CoachNoteText(val title: String, val bullets: List<String>)
}
