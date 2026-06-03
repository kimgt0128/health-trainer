package com.healthtrainer.core.tracker

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PlankRule
import com.healthtrainer.core.exercise.SquatRule
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * Tests for [SetTracker] — the headline "1세트 2회차 실패" feature.
 *
 * Builds synthetic side-view sets and asserts the produced [ExerciseSession] identifies a failed rep
 * by [RepRecord.setNo] / [RepRecord.repNo]. Also covers the plank single-hold path.
 */
class SetTrackerTest {

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

    // --- Squat frames ------------------------------------------------------------------------

    private fun squatFrame(
        tsMs: Long,
        shoulder: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        knee: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
    ): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_KNEE to knee, LandmarkName.RIGHT_KNEE to knee,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
        return PoseFrame(tsMs, pairs.map { (n, p) -> lm(n, p.first, p.second) }.associateBy { it.name })
    }

    private fun standing(ts: Long) = squatFrame(
        ts, shoulder = 0f to 4f, hip = 0f to 3f, knee = 0.0875f to 2f, ankle = 0f to 1f,
    )
    private fun deep(ts: Long) = squatFrame(
        ts, shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.3f to 1f,
    )
    private fun shallow(ts: Long) = squatFrame(
        ts, shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.7f to 0.42f,
    )

    /** rep1 deep(valid) -> rep2 shallow(invalid) -> rep3 deep(valid). */
    private fun threeRepSquatSet() = listOf(
        standing(0),
        deep(100), standing(200),
        shallow(300), standing(400),
        deep(500), standing(600),
    )

    @Test
    fun squatSet_secondRepDepthFailure_identifiedBySetAndRepNo() {
        val tracker = SetTracker(SquatRule())
        tracker.startSet()
        threeRepSquatSet().forEach { tracker.onFrame(it) }
        tracker.endSet()
        val session = tracker.build(startedAtMs = 1_000L)

        val records = session.sets[0].reps
        assertThat(records).hasSize(3)

        // The headline assertions: set 1, rep 2 failed on depth.
        assertThat(records[1].setNo).isEqualTo(1)
        assertThat(records[1].repNo).isEqualTo(2)
        assertThat(records[1].valid).isFalse()
        assertThat(records[1].failures).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)

        // The other two reps are valid and numbered 1 and 3.
        assertThat(records[0].setNo).isEqualTo(1)
        assertThat(records[0].repNo).isEqualTo(1)
        assertThat(records[0].valid).isTrue()
        assertThat(records[2].repNo).isEqualTo(3)
        assertThat(records[2].valid).isTrue()
    }

    @Test
    fun build_carriesExerciseTypeAndStartTimestamp() {
        val tracker = SetTracker(SquatRule())
        tracker.startSet()
        threeRepSquatSet().forEach { tracker.onFrame(it) }
        tracker.endSet()
        val session = tracker.build(startedAtMs = 42L)
        assertThat(session.exerciseType).isEqualTo(ExerciseType.SQUAT)
        assertThat(session.startedAtMs).isEqualTo(42L)
        assertThat(session.sets).hasSize(1)
        assertThat(session.sets[0].setNo).isEqualTo(1)
    }

    @Test
    fun twoSets_repNoResetsPerSetAndSetNoIncrements() {
        val tracker = SetTracker(SquatRule())
        // Set 1: one deep rep.
        tracker.startSet()
        listOf(standing(0), deep(100), standing(200)).forEach { tracker.onFrame(it) }
        tracker.endSet()
        // Set 2: two deep reps.
        tracker.startSet()
        listOf(standing(1000), deep(1100), standing(1200), deep(1300), standing(1400))
            .forEach { tracker.onFrame(it) }
        tracker.endSet()
        val session = tracker.build(startedAtMs = 0L)

        assertThat(session.sets).hasSize(2)
        assertThat(session.sets[0].reps).hasSize(1)
        assertThat(session.sets[0].reps[0].setNo).isEqualTo(1)
        assertThat(session.sets[0].reps[0].repNo).isEqualTo(1)

        assertThat(session.sets[1].setNo).isEqualTo(2)
        assertThat(session.sets[1].reps).hasSize(2)
        assertThat(session.sets[1].reps[0].setNo).isEqualTo(2)
        assertThat(session.sets[1].reps[0].repNo).isEqualTo(1)
        assertThat(session.sets[1].reps[1].repNo).isEqualTo(2)
    }

    @Test
    fun onFrame_returnsClosedRepAndExposesLiveCount() {
        // Live rep counter for the UI: onFrame returns the just-closed rep (with set/rep numbers)
        // or null, and currentRepCount / currentSetReps reflect progress mid-set.
        val tracker = SetTracker(SquatRule())
        tracker.startSet()
        assertThat(tracker.currentRepCount).isEqualTo(0)

        assertThat(tracker.onFrame(standing(0))).isNull()
        assertThat(tracker.onFrame(deep(100))).isNull()
        val closed = tracker.onFrame(standing(200)) // closing "standing" completes rep 1
        assertThat(closed).isNotNull()
        assertThat(closed!!.setNo).isEqualTo(1)
        assertThat(closed.repNo).isEqualTo(1)
        assertThat(tracker.currentRepCount).isEqualTo(1)
        assertThat(tracker.currentSetReps).hasSize(1)

        assertThat(tracker.onFrame(deep(300))).isNull() // mid-rep -> no close
        assertThat(tracker.currentRepCount).isEqualTo(1)

        tracker.endSet()
        assertThat(tracker.currentRepCount).isEqualTo(0) // no set open
    }

    // --- Plank frames ------------------------------------------------------------------------

    private fun plankFrame(
        tsMs: Long,
        shoulder: Pair<Float, Float>,
        elbow: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
    ): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
        return PoseFrame(tsMs, pairs.map { (n, p) -> lm(n, p.first, p.second) }.associateBy { it.name })
    }

    /** Sagging plank: hip below the shoulder->ankle line (y-down) -> PLANK_HIPS_LOW; body line ~127. */
    private fun saggingPlank(ts: Long) = plankFrame(
        ts, shoulder = 0f to 1f, elbow = 0f to 1.2f, hip = -1f to 1.5f, ankle = -2f to 1f,
    )

    @Test
    fun plankSet_mostlySagging_oneInvalidHoldRecord() {
        val tracker = SetTracker(PlankRule())
        tracker.startSet()
        listOf(saggingPlank(0), saggingPlank(100), saggingPlank(200), saggingPlank(300))
            .forEach { tracker.onFrame(it) }
        tracker.endSet()
        val session = tracker.build(startedAtMs = 0L)

        val records = session.sets[0].reps
        // A plank "rep" is the whole hold -> exactly one record.
        assertThat(records).hasSize(1)
        assertThat(records[0].setNo).isEqualTo(1)
        assertThat(records[0].repNo).isEqualTo(1)
        assertThat(records[0].valid).isFalse()
        assertThat(records[0].failures).contains(FeedbackCode.PLANK_HIPS_LOW)
        // Hold spans the first to last frame timestamps.
        assertThat(records[0].startTimestampMs).isEqualTo(0L)
        assertThat(records[0].endTimestampMs).isEqualTo(300L)
    }

    @Test
    fun plankSet_empty_producesNoRecord() {
        // An empty hold (no frames) yields no rep record.
        val tracker = SetTracker(PlankRule())
        tracker.startSet()
        tracker.endSet()
        val session = tracker.build(startedAtMs = 0L)
        assertThat(session.sets[0].reps).isEmpty()
    }
}
