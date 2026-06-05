package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.SquatRule

/**
 * Scores a squat rep on two axes the side-view rule actually measures:
 * - `depth` — from `min_knee_angle`: 100 at/below [SquatRule.DEPTH_MAX_KNEE_ANGLE] (deep enough),
 *   ramping to 0 at [SquatRule.STANDING_KNEE_ANGLE] (never bent). `failed` mirrors
 *   [FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH].
 * - `torso` — from `mean_torso_angle`: 100 at/above [SquatRule.TORSO_LEAN_MIN_ANGLE] (upright),
 *   ramping to 0 at [SquatRule.TORSO_FLAT_ANGLE]. `failed` mirrors [FeedbackCode.SQUAT_TORSO_LEAN].
 *
 * Thresholds are read from [SquatRule]'s companion (single source). A missing metric scores that
 * axis 0 but the axis is still emitted.
 */
class SquatScorer : ExerciseScorer {

    override val exerciseType: ExerciseType = ExerciseType.SQUAT

    override fun axisKeys(): List<String> = AXES

    override fun scoreRep(metrics: Map<String, Float>, failures: Set<FeedbackCode>, durationMs: Long): RepScore {
        val depthScore = metrics["min_knee_angle"]?.let {
            ScoreMath.rampLowerBetter(it, full = SquatRule.DEPTH_MAX_KNEE_ANGLE, zero = SquatRule.STANDING_KNEE_ANGLE)
        } ?: 0
        val torsoScore = metrics["mean_torso_angle"]?.let {
            ScoreMath.rampHigherBetter(it, full = SquatRule.TORSO_LEAN_MIN_ANGLE, zero = SquatRule.TORSO_FLAT_ANGLE)
        } ?: 0

        val axes = listOf(
            AxisScore("depth", depthScore, FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH in failures),
            AxisScore("torso", torsoScore, FeedbackCode.SQUAT_TORSO_LEAN in failures),
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
        val AXES = listOf("depth", "torso")
    }
}
