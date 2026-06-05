package com.healthtrainer.core.tracker

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.PlankRule
import com.healthtrainer.core.exercise.SquatRule
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * TDD spec for persisting the per-rep angle aggregate ([com.healthtrainer.core.exercise.ExerciseRule.aggregateRepMetrics])
 * onto the rep records that survive the session ([RepRecord.metrics]).
 *
 * Unlike the transient [RepRecordData.frameFeedbacks] (cleared after the rep), [RepRecord.metrics]
 * is the persisted aggregate the result report / scorer reads. These tests pin:
 * - [RepStateMachine] fills [RepRecordData.metrics] at rep close via the rule's aggregate.
 * - [RepRecordData.toRecord] carries metrics through to the persisted [RepRecord].
 * - the plank hold path ([SetTracker.endSet]) also persists the hold's aggregate.
 * - additive: a [RepRecordData]/[RepRecord] built without metrics defaults to empty (non-breaking).
 */
class RepMetricsPersistenceTest {

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

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

    @Test
    fun repStateMachine_closedRep_populatesMetricsFromRuleAggregate() {
        val frames = listOf(standing(0), deep(100), deep(200), standing(300))
        val reps = RepStateMachine(SquatRule()).process(frames)

        assertThat(reps).hasSize(1)
        val metrics = reps[0].metrics
        assertThat(metrics.keys).containsExactly("min_knee_angle", "mean_torso_angle", "min_torso_angle")
        // The deep bottom (~90) is the rep minimum knee.
        assertThat(metrics["min_knee_angle"]!!).isWithin(0.5f).of(90f)
    }

    @Test
    fun repRecordData_defaultMetrics_areEmpty() {
        val data = RepRecordData(
            valid = true,
            failures = emptySet(),
            startTimestampMs = 0L,
            endTimestampMs = 1L,
        )
        assertThat(data.metrics).isEmpty()
    }

    @Test
    fun setTracker_closedRep_persistsMetricsOntoRepRecord() {
        val tracker = SetTracker(SquatRule())
        tracker.startSet()
        tracker.onFrame(standing(0))
        tracker.onFrame(deep(100))
        val rep = tracker.onFrame(standing(200))
        tracker.endSet()

        assertThat(rep).isNotNull()
        assertThat(rep!!.metrics.keys).containsExactly("min_knee_angle", "mean_torso_angle", "min_torso_angle")
        assertThat(rep.metrics["min_knee_angle"]!!).isWithin(0.5f).of(90f)

        // And it survives into the built session (persisted, not transient).
        val session = tracker.build(startedAtMs = 0L)
        val persisted = session.sets.single().reps.single()
        assertThat(persisted.metrics).isEqualTo(rep.metrics)
    }

    // --- Plank hold path -----------------------------------------------------------------------

    private fun plankFrame(tsMs: Long, hipY: Float): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to (0f to 1f), LandmarkName.RIGHT_SHOULDER to (0f to 1f),
            LandmarkName.LEFT_ELBOW to (0f to 1.6f), LandmarkName.RIGHT_ELBOW to (0f to 1.6f),
            LandmarkName.LEFT_HIP to (2f to hipY), LandmarkName.RIGHT_HIP to (2f to hipY),
            LandmarkName.LEFT_ANKLE to (4f to 1f), LandmarkName.RIGHT_ANKLE to (4f to 1f),
        )
        return PoseFrame(tsMs, pairs.map { (n, p) -> lm(n, p.first, p.second) }.associateBy { it.name })
    }

    @Test
    fun setTracker_plankHold_persistsAggregateMetrics() {
        val tracker = SetTracker(PlankRule())
        tracker.startSet()
        tracker.onFrame(plankFrame(0, hipY = 1.035f))  // valid
        tracker.onFrame(plankFrame(100, hipY = 1.5f))  // sag
        tracker.onFrame(plankFrame(200, hipY = 1.035f)) // valid
        tracker.endSet()

        val hold = tracker.build(startedAtMs = 0L).sets.single().reps.single()
        assertThat(hold.metrics.keys).containsExactly("min_body_line_angle", "max_elbow_offset")
    }
}
