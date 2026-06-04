package com.healthtrainer.core.tracker

import com.healthtrainer.core.exercise.ExerciseRule
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.pose.PoseFrame

/**
 * Drives an exercise session: groups frames into sets and produces an [ExerciseSession].
 *
 * Usage: [startSet], feed frames with [onFrame], [endSet]; repeat per set; then [build]. During a
 * set, [currentRepCount] / [currentSetReps] expose live progress for the UI rep counter.
 *
 * - **Rep-counted exercises (squat / push-up):** within a set, frames feed a fresh
 *   [RepStateMachine]. Each completed rep is numbered with a 1-based [RepRecord.setNo] (incrementing
 *   per set) and a 1-based [RepRecord.repNo] (resetting each set).
 * - **Plank (hold):** frames are collected for the set; at [endSet] the whole hold is judged once via
 *   [ExerciseRule.aggregateRep], producing a single [RepRecord] (repNo = 1) spanning the first..last
 *   frame timestamps. An empty hold produces no record.
 *
 * Pure :core — timestamps come from the frames and the [build] parameter; never from a system clock.
 */
class SetTracker(private val rule: ExerciseRule) {

    private val isHold: Boolean = rule.exerciseType == ExerciseType.PLANK

    private val completedSets = mutableListOf<SetRecord>()

    // Per-set in-progress state.
    private var inSet = false
    private var currentSetNo = 0
    private var repMachine: RepStateMachine? = null
    private val currentReps = mutableListOf<RepRecord>()
    private val holdFrames = mutableListOf<PoseFrame>()

    /** Begin a new set (1-based set numbers increment across the session). */
    fun startSet() {
        inSet = true
        currentSetNo += 1
        currentReps.clear()
        holdFrames.clear()
        repMachine = if (isHold) null else RepStateMachine(rule)
    }

    /**
     * Feed one frame into the current set. Returns the [RepRecord] just closed by this frame
     * (rep-counted exercises), or null if no rep closed, no set is open, or this is a hold.
     */
    fun onFrame(frame: PoseFrame): RepRecord? {
        if (!inSet) return null
        if (isHold) {
            holdFrames.add(frame)
            return null
        }
        val data = repMachine?.onFrame(frame) ?: return null
        val record = data.toRecord(setNo = currentSetNo, repNo = currentReps.size + 1)
        currentReps.add(record)
        return record
    }

    /** Reps closed so far in the in-progress set (live snapshot for the UI). Empty when no set is open. */
    val currentSetReps: List<RepRecord> get() = if (inSet) currentReps.toList() else emptyList()

    /** Count of reps closed so far in the in-progress set (live rep counter). 0 when no set is open. */
    val currentRepCount: Int get() = if (inSet) currentReps.size else 0

    /** Finalize the current set, appending it (with its reps) to the session. */
    fun endSet() {
        if (!inSet) return
        if (isHold) {
            finalizeHold()
        }
        completedSets.add(SetRecord(setNo = currentSetNo, reps = currentReps.toList()))
        inSet = false
        repMachine = null
    }

    /** Build the session. [startedAtMs] is supplied by the caller (no system clock in :core). */
    fun build(startedAtMs: Long): ExerciseSession =
        ExerciseSession(
            exerciseType = rule.exerciseType,
            startedAtMs = startedAtMs,
            sets = completedSets.toList(),
        )

    /** Judge the whole plank hold once -> at most one [RepRecord] (repNo = 1). */
    private fun finalizeHold() {
        if (holdFrames.isEmpty()) return
        val feedbacks = holdFrames.map { rule.evaluate(it) }
        val failures = rule.aggregateRep(feedbacks)
        currentReps.add(
            RepRecord(
                setNo = currentSetNo,
                repNo = 1,
                valid = failures.isEmpty(),
                failures = failures,
                startTimestampMs = holdFrames.first().timestampMs,
                endTimestampMs = holdFrames.last().timestampMs,
            ),
        )
    }

    private fun RepRecordData.toRecord(setNo: Int, repNo: Int) = RepRecord(
        setNo = setNo,
        repNo = repNo,
        valid = valid,
        failures = failures,
        startTimestampMs = startTimestampMs,
        endTimestampMs = endTimestampMs,
    )
}
