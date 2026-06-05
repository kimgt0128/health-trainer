package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode

/**
 * Score model for the result report. Everything here is derived — honestly — from what the rule
 * engine actually measured (per-rep angle aggregates + rep-level [FeedbackCode]s + valid ratio).
 * No fabricated numbers: an axis is only scored from a metric the rule produced, and `failed`
 * mirrors a real rep-level failure.
 *
 * Scores are integers in `0..100`.
 */

/**
 * One scored quality axis of a rep/set/session (e.g. "depth", "torso", "body_line").
 *
 * @property key    the axis identifier (stable across rep/set/session for the same exercise).
 * @property score  0..100, higher is better.
 * @property failed whether the rule flagged the corresponding rep-level failure for this axis.
 */
data class AxisScore(
    val key: String,
    val score: Int,
    val failed: Boolean,
)

/**
 * One scored rep.
 *
 * @property setNo   1-based set number (attached by [SessionSummarizer]; 0 from a bare scorer call).
 * @property repNo   1-based rep number within its set (attached by [SessionSummarizer]).
 * @property valid   whether the rep was good form (`failures` empty).
 * @property overall 0..100 rounded mean of [axes].
 * @property axes    per-axis scores for this rep.
 * @property failures the rep-level hard-failure codes (carried for the issue tally).
 * @property durationMs rep duration in ms (end - start).
 */
data class RepScore(
    val setNo: Int,
    val repNo: Int,
    val valid: Boolean,
    val overall: Int,
    val axes: List<AxisScore>,
    val failures: Set<FeedbackCode>,
    val durationMs: Long,
)

/**
 * One scored set.
 *
 * @property setNo   1-based set number.
 * @property overall 0..100 rounded mean of its reps' [RepScore.overall].
 * @property reps    the scored reps, in order.
 * @property axes    per-axis mean across the set's reps.
 */
data class SetScore(
    val setNo: Int,
    val overall: Int,
    val reps: List<RepScore>,
    val axes: List<AxisScore>,
)

/** How often a [FeedbackCode] occurred across the scored reps (for the "top issues" list). */
data class IssueTally(
    val code: FeedbackCode,
    val count: Int,
)

/**
 * The whole-session summary the result screen renders.
 *
 * @property exerciseType the session's exercise.
 * @property overall   0..100 rounded mean of all rep overalls (0 if no reps).
 * @property totalReps total reps scored.
 * @property validReps reps that were good form.
 * @property sets      the scored sets, in order.
 * @property axes      per-axis mean across all reps.
 * @property topIssues [FeedbackCode] frequency across all reps, descending (excludes LOW_CONFIDENCE).
 */
data class SessionSummary(
    val exerciseType: ExerciseType,
    val overall: Int,
    val totalReps: Int,
    val validReps: Int,
    val sets: List<SetScore>,
    val axes: List<AxisScore>,
    val topIssues: List<IssueTally>,
)
