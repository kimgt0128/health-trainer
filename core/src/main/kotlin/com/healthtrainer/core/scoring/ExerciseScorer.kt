package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode

/**
 * Turns a rep's persisted angle aggregate ([com.healthtrainer.core.tracker.RepRecord.metrics]) +
 * rep-level failures into a [RepScore]. One implementation per [ExerciseType].
 *
 * **Threshold sourcing.** Every axis formula reads its cut-offs from the matching
 * [com.healthtrainer.core.exercise.ExerciseRule]'s public companion constants (e.g.
 * `SquatRule.DEPTH_MAX_KNEE_ANGLE`). The scorer keeps NO second copy of a threshold, so tuning a
 * rule re-tunes the score in lock-step.
 *
 * The scorer owns `overall` and `axes`; set/rep numbering belongs to [SessionSummarizer], so
 * [scoreRep] returns `setNo = 0` / `repNo = 0` placeholders the summarizer overwrites.
 */
interface ExerciseScorer {
    val exerciseType: ExerciseType

    /** The axis keys this scorer emits, in display order (stable per exercise). */
    fun axisKeys(): List<String>

    /**
     * Score one rep from its [metrics] (the rule's per-rep aggregate), the rep-level [failures], and
     * its [durationMs]. Returns a [RepScore] with `setNo`/`repNo` = 0 (the summarizer attaches them).
     * Axes are always emitted (in [axisKeys] order); a missing metric scores that axis 0.
     */
    fun scoreRep(metrics: Map<String, Float>, failures: Set<FeedbackCode>, durationMs: Long): RepScore
}

/**
 * Shared scoring primitives so every scorer ramps angles the same way (no divergent math).
 */
internal object ScoreMath {

    /** Clamp to the valid score range and round to an Int. */
    fun clampScore(raw: Float): Int = Math.round(raw).coerceIn(0, 100)

    /**
     * A "lower value is better" ramp: [full] (or below) -> 100, [zero] (or above) -> 0, linear
     * between. Used when a *smaller* angle/offset is better (squat depth: smaller knee angle = deeper;
     * elbow offset: smaller = more aligned).
     */
    fun rampLowerBetter(value: Float, full: Float, zero: Float): Int {
        if (zero == full) return if (value <= full) 100 else 0
        val t = (zero - value) / (zero - full) // 1 at `full`, 0 at `zero`
        return clampScore(t * 100f)
    }

    /**
     * A "higher value is better" ramp: [full] (or above) -> 100, [zero] (or below) -> 0, linear
     * between. Used when a *larger* angle is better (torso uprightness, body-line straightness).
     */
    fun rampHigherBetter(value: Float, full: Float, zero: Float): Int {
        if (full == zero) return if (value >= full) 100 else 0
        val t = (value - zero) / (full - zero) // 1 at `full`, 0 at `zero`
        return clampScore(t * 100f)
    }

    /** Rounded mean of the axis scores (0 when there are none). */
    fun meanScore(scores: List<Int>): Int =
        if (scores.isEmpty()) 0 else Math.round(scores.average().toFloat()).coerceIn(0, 100)
}
