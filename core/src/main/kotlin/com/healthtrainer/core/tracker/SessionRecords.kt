package com.healthtrainer.core.tracker

import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode

/**
 * Session records produced by the tracker: an [ExerciseSession] holds [SetRecord]s, each holding
 * [RepRecord]s. A failed rep is identified as e.g. "set 1, rep 2" via [RepRecord.setNo] /
 * [RepRecord.repNo] (pose-rule-authoring: "실패 회차는 1세트 2회차처럼 setNo/repNo로 식별된다").
 *
 * Pure :core data — no Android, no clock. Timestamps come from the source frames / caller, never
 * from System.currentTimeMillis().
 */

/**
 * One completed rep.
 *
 * @property setNo  1-based set number this rep belongs to.
 * @property repNo  1-based rep number within its set.
 * @property valid  whether the rep is good form — `true` iff [failures] is empty.
 * @property failures rep-level hard-failure codes from [com.healthtrainer.core.exercise.ExerciseRule.aggregateRep].
 * @property startTimestampMs frame timestamp where the rep's descent began.
 * @property endTimestampMs   frame timestamp where the rep returned to the top (or, for a plank
 *                            hold, the last frame of the hold).
 */
data class RepRecord(
    val setNo: Int,
    val repNo: Int,
    val valid: Boolean,
    val failures: Set<FeedbackCode>,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
)

/** One set: its 1-based [setNo] and the [reps] it contains, in order. */
data class SetRecord(
    val setNo: Int,
    val reps: List<RepRecord>,
)

/** A whole exercise session: the [exerciseType], when it started, and the [sets] performed. */
data class ExerciseSession(
    val exerciseType: ExerciseType,
    val startedAtMs: Long,
    val sets: List<SetRecord>,
)

/**
 * A rep as segmented by [RepStateMachine], *before* the [SetTracker] assigns its set/rep numbers.
 * Carries everything except [RepRecord.setNo] / [RepRecord.repNo].
 *
 * [frameFeedbacks] is **transient** (in-memory only) — the per-frame [ExerciseFeedback]s buffered for
 * this rep, in order, ending with the closing TOP frame. It feeds the rep-level
 * [com.healthtrainer.core.features.RepFeatureExtractor] (push-up assist model) so features are built
 * from the rule's own metrics with zero angle recomputation. It is NOT part of the persisted
 * [RepRecord]; the default `emptyList()` keeps existing callers non-breaking.
 */
data class RepRecordData(
    val valid: Boolean,
    val failures: Set<FeedbackCode>,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val frameFeedbacks: List<ExerciseFeedback> = emptyList(),
)
