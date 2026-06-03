package com.healthtrainer.core.tracker

import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseRule
import com.healthtrainer.core.pose.PoseFrame

/**
 * Segments a stream of [PoseFrame]s into reps for a rep-counted exercise (squat / push-up).
 *
 * A rep is counted on **TOP -> descent -> TOP**. The machine has two states and starts at [State.TOP]:
 * - At [State.TOP], a frame for which [ExerciseRule.isDescent] holds opens a rep: the machine
 *   buffers that frame's [ExerciseFeedback] and remembers its timestamp as the rep start.
 * - At [State.DOWN], every frame's feedback is buffered. The first frame for which
 *   [ExerciseRule.isTop] holds closes the rep: validity is decided SOLELY by
 *   [ExerciseRule.aggregateRep] over the buffered feedbacks, and the machine returns to [State.TOP].
 *
 * Low-confidence frames mid-rep are still buffered ([ExerciseRule.aggregateRep] ignores them, since
 * they carry no metrics) so they neither split nor invalidate a rep.
 *
 * Rep validity follows the pose-rule-authoring contract: it is `aggregateRep().isEmpty()`, never an
 * OR of per-frame hard failures. The descent threshold is intentionally looser than the good-form
 * BOTTOM band so shallow reps are counted and then recorded as failures.
 *
 * Pure :core — no clock; timestamps are read from the frames.
 */
class RepStateMachine(private val rule: ExerciseRule) {

    private enum class State { TOP, DOWN }

    private var state = State.TOP
    private val buffer = mutableListOf<ExerciseFeedback>()
    private var repStartMs = 0L

    /**
     * Feed one frame. Returns a [RepRecordData] when this frame *closes* a rep (a TOP frame after a
     * descent), otherwise `null`. The returned rep has no set/rep numbers — the [SetTracker] assigns
     * those.
     */
    fun onFrame(frame: PoseFrame): RepRecordData? {
        val feedback = rule.evaluate(frame)
        return when (state) {
            State.TOP -> {
                if (rule.isDescent(feedback)) {
                    state = State.DOWN
                    buffer.clear()
                    buffer.add(feedback)
                    repStartMs = frame.timestampMs
                }
                null
            }
            State.DOWN -> {
                buffer.add(feedback)
                if (rule.isTop(feedback)) {
                    val failures = rule.aggregateRep(buffer)
                    val rep = RepRecordData(
                        valid = failures.isEmpty(),
                        failures = failures,
                        startTimestampMs = repStartMs,
                        endTimestampMs = frame.timestampMs,
                    )
                    state = State.TOP
                    buffer.clear()
                    rep
                } else {
                    null
                }
            }
        }
    }

    /**
     * Convenience for tests / batch processing: run [onFrame] over [frames] in order and collect the
     * completed reps. An in-progress rep at the end (never returned to TOP) is not emitted.
     */
    fun process(frames: List<PoseFrame>): List<RepRecordData> =
        frames.mapNotNull { onFrame(it) }
}
