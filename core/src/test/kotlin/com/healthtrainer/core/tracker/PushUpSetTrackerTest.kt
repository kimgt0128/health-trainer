package com.healthtrainer.core.tracker

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PushUpRule
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.testutil.SyntheticPose
import org.junit.Test

/**
 * [SetTracker] coverage for the PUSH-UP rep-counted path — the parity gap next to the existing squat
 * and plank tracker tests (SetTrackerTest covered only those two). Drives synthetic side-view frames
 * at TARGET joint angles ([SyntheticPose.pointForAngle]) through the tracker and asserts the produced
 * [ExerciseSession] segments reps, identifies a failed rep by [RepRecord.setNo] / [RepRecord.repNo],
 * and persists the rule's per-rep angle aggregate onto [RepRecord.metrics].
 */
class PushUpSetTrackerTest {

    private fun frame(tsMs: Long, elbowDeg: Float, bodyLineDeg: Float): PoseFrame {
        val shoulder = Point3(0f, 2f, 0f)
        val elbow = Point3(0.5f, 1.6f, 0f)
        val wrist = SyntheticPose.pointForAngle(a = shoulder, b = elbow, degrees = elbowDeg)
        val hip = Point3(-1f, 2f, 0f)
        val ankle = SyntheticPose.pointForAngle(a = shoulder, b = hip, degrees = bodyLineDeg)
        return SyntheticPose.frame(
            tsMs,
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_WRIST to wrist, LandmarkName.RIGHT_WRIST to wrist,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
    }

    private fun top(ts: Long) = frame(ts, elbowDeg = 168f, bodyLineDeg = 178f)
    private fun deep(ts: Long) = frame(ts, elbowDeg = 85f, bodyLineDeg = 178f)
    private fun shallow(ts: Long) = frame(ts, elbowDeg = 120f, bodyLineDeg = 178f) // counted, depth-fault

    /** rep1 deep(valid) -> rep2 shallow(invalid depth) -> rep3 deep(valid). */
    private fun threeRepSet() = listOf(
        top(0),
        deep(100), top(200),
        shallow(300), top(400),
        deep(500), top(600),
    )

    @Test
    fun pushUpSet_secondRepDepthFailure_identifiedBySetAndRepNo() {
        val tracker = SetTracker(PushUpRule())
        tracker.startSet()
        threeRepSet().forEach { tracker.onFrame(it) }
        tracker.endSet()
        val records = tracker.build(startedAtMs = 0L).sets.single().reps

        assertThat(records).hasSize(3)
        // Headline: set 1, rep 2 failed on depth.
        assertThat(records[1].setNo).isEqualTo(1)
        assertThat(records[1].repNo).isEqualTo(2)
        assertThat(records[1].valid).isFalse()
        assertThat(records[1].failures).contains(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH)
        // The other two reps are valid and numbered 1 and 3.
        assertThat(records[0].valid).isTrue()
        assertThat(records[0].repNo).isEqualTo(1)
        assertThat(records[2].valid).isTrue()
        assertThat(records[2].repNo).isEqualTo(3)
    }

    @Test
    fun pushUpSet_carriesExerciseTypeAndPersistsRepMetrics() {
        val tracker = SetTracker(PushUpRule())
        tracker.startSet()
        listOf(top(0), deep(100), deep(200), top(300)).forEach { tracker.onFrame(it) }
        tracker.endSet()
        val session = tracker.build(startedAtMs = 7L)

        assertThat(session.exerciseType).isEqualTo(ExerciseType.PUSH_UP)
        assertThat(session.startedAtMs).isEqualTo(7L)
        val rep = session.sets.single().reps.single()
        // Persisted aggregate is the rule's own keys (min elbow ~85 from the deep bottom).
        assertThat(rep.metrics.keys)
            .containsExactly("min_elbow_angle", "min_body_line_angle", "body_line_broken_ratio")
        assertThat(rep.metrics["min_elbow_angle"]!!).isWithin(1f).of(85f)
        assertThat(rep.metrics["body_line_broken_ratio"]!!).isWithin(0.001f).of(0f)
    }

    @Test
    fun twoSets_repNoResetsPerSetAndSetNoIncrements() {
        val tracker = SetTracker(PushUpRule())
        tracker.startSet()
        listOf(top(0), deep(100), top(200)).forEach { tracker.onFrame(it) }
        tracker.endSet()
        tracker.startSet()
        listOf(top(1000), deep(1100), top(1200), deep(1300), top(1400)).forEach { tracker.onFrame(it) }
        tracker.endSet()
        val session = tracker.build(startedAtMs = 0L)

        assertThat(session.sets).hasSize(2)
        assertThat(session.sets[0].reps).hasSize(1)
        assertThat(session.sets[0].reps[0].setNo).isEqualTo(1)
        assertThat(session.sets[1].setNo).isEqualTo(2)
        assertThat(session.sets[1].reps).hasSize(2)
        assertThat(session.sets[1].reps.map { it.repNo }).containsExactly(1, 2).inOrder()
        assertThat(session.sets[1].reps.all { it.setNo == 2 }).isTrue()
    }
}
