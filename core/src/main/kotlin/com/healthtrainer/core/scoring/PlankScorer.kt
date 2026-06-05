package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PlankRule

/**
 * Scores a plank hold on two axes the side-view rule actually measures:
 * - `body_line` — from `min_body_line_angle`: 100 at/above [PlankRule.BODY_LINE_MIN_ANGLE]
 *   (straight), ramping to 0 at [PlankRule.BODY_LINE_BROKEN_ANGLE]. `failed` mirrors either hip
 *   fault ([FeedbackCode.PLANK_HIPS_LOW] or [FeedbackCode.PLANK_HIPS_HIGH]).
 * - `elbow` — from `max_elbow_offset`: 100 at offset 0 (under the shoulder), ramping to 0 at
 *   [PlankRule.ELBOW_OFFSET_MAX]. `failed` mirrors [FeedbackCode.PLANK_ELBOW_MISALIGNED].
 *
 * Thresholds are read from [PlankRule]'s companion (single source). A missing metric scores that
 * axis 0 but the axis is still emitted.
 */
class PlankScorer : ExerciseScorer {

    override val exerciseType: ExerciseType = ExerciseType.PLANK

    override fun axisKeys(): List<String> = AXES

    override fun scoreRep(metrics: Map<String, Float>, failures: Set<FeedbackCode>, durationMs: Long): RepScore {
        val bodyLineScore = metrics["min_body_line_angle"]?.let {
            ScoreMath.rampHigherBetter(it, full = PlankRule.BODY_LINE_MIN_ANGLE, zero = PlankRule.BODY_LINE_BROKEN_ANGLE)
        } ?: 0
        val elbowScore = metrics["max_elbow_offset"]?.let {
            ScoreMath.rampLowerBetter(it, full = 0f, zero = PlankRule.ELBOW_OFFSET_MAX)
        } ?: 0

        val bodyLineFailed = FeedbackCode.PLANK_HIPS_LOW in failures || FeedbackCode.PLANK_HIPS_HIGH in failures
        val axes = listOf(
            AxisScore("body_line", bodyLineScore, bodyLineFailed),
            AxisScore("elbow", elbowScore, FeedbackCode.PLANK_ELBOW_MISALIGNED in failures),
        )
        return RepScore(
            setNo = 0,
            repNo = 0,
            valid = failures.isEmpty(),
            overall = ScoreMath.meanScore(axes.map { it.score }),
            axes = axes,
            failures = failures,
            durationMs = durationMs,
        )
    }

    private companion object {
        val AXES = listOf("body_line", "elbow")
    }
}
