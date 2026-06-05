package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PushUpRule

/**
 * Scores a push-up rep on two axes the side-view rule actually measures:
 * - `depth` — from `min_elbow_angle`: 100 at/below [PushUpRule.DEPTH_MAX_ELBOW_ANGLE] (deep enough),
 *   ramping to 0 at [PushUpRule.EXTENDED_ELBOW_ANGLE] (arms never bent). `failed` mirrors
 *   [FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH].
 * - `body_line` — from `min_body_line_angle`: 100 at/above [PushUpRule.BODY_LINE_MIN_ANGLE]
 *   (straight), ramping to 0 at [PushUpRule.BODY_LINE_BROKEN_ANGLE]. `failed` mirrors
 *   [FeedbackCode.PUSH_UP_BODY_LINE_BROKEN].
 *
 * Thresholds are read from [PushUpRule]'s companion (single source). A missing metric scores that
 * axis 0 but the axis is still emitted.
 */
class PushUpScorer : ExerciseScorer {

    override val exerciseType: ExerciseType = ExerciseType.PUSH_UP

    override fun axisKeys(): List<String> = AXES

    override fun scoreRep(metrics: Map<String, Float>, failures: Set<FeedbackCode>, durationMs: Long): RepScore {
        val depthScore = metrics["min_elbow_angle"]?.let {
            ScoreMath.rampLowerBetter(it, full = PushUpRule.DEPTH_MAX_ELBOW_ANGLE, zero = PushUpRule.EXTENDED_ELBOW_ANGLE)
        } ?: 0
        val bodyLineScore = metrics["min_body_line_angle"]?.let {
            ScoreMath.rampHigherBetter(it, full = PushUpRule.BODY_LINE_MIN_ANGLE, zero = PushUpRule.BODY_LINE_BROKEN_ANGLE)
        } ?: 0

        val axes = listOf(
            AxisScore("depth", depthScore, FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH in failures),
            AxisScore("body_line", bodyLineScore, FeedbackCode.PUSH_UP_BODY_LINE_BROKEN in failures),
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
        val AXES = listOf("depth", "body_line")
    }
}
