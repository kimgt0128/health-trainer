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
}
