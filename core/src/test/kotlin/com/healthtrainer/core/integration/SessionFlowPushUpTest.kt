package com.healthtrainer.core.integration

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PushUpRule
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.scoring.SessionSummarizer
import com.healthtrainer.core.testutil.SyntheticPose
import com.healthtrainer.core.tracker.SetTracker
import org.junit.Test

/**
 * End-to-end ("통합테스트") for the PUSH-UP pipeline: a synthetic [PoseFrame] sequence driven through
 * the REAL composition — [SetTracker] (≥2 sets, mixed valid/invalid reps) -> [ExerciseSession] ->
 * [SessionSummarizer.summarize]. Unlike squat, push-up has BOTH rep-invalidating faults reach the
 * rep-level failures: depth (min elbow > 105) and a broken body line (> 30% of visible frames < 160°),
 * so the issue tally can carry two distinct codes.
 *
 * Frames are built at TARGET angles with [SyntheticPose.pointForAngle]: the elbow angle
 * (shoulder-elbow-wrist) drives phase/depth, the body-line angle (shoulder-hip-ankle) drives the
 * broken-line per-frame hard failure. Mirrored onto both sides for the left/right average path.
 */
class SessionFlowPushUpTest {

    private val rule = PushUpRule()

    /** One push-up frame with explicit [elbowDeg] (shoulder-elbow-wrist) and [bodyLineDeg] (shoulder-hip-ankle). */
    private fun frame(tsMs: Long, elbowDeg: Float, bodyLineDeg: Float, vis: Float = 0.9f): PoseFrame {
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
            visibility = vis,
        )
    }

    // TOP: elbow >= 155. Good BOTTOM: 70..100. Shallow descent counted (elbow < 130) but depth-fault
    // (min elbow > 105). Body line straight ~178; broken when < 160.
    private fun top(ts: Long, bodyLineDeg: Float = 178f) = frame(ts, elbowDeg = 168f, bodyLineDeg = bodyLineDeg)
    private fun deep(ts: Long, bodyLineDeg: Float = 178f) = frame(ts, elbowDeg = 85f, bodyLineDeg = bodyLineDeg)
    private fun shallow(ts: Long, bodyLineDeg: Float = 178f) = frame(ts, elbowDeg = 120f, bodyLineDeg = bodyLineDeg)

    @Test
    fun twoSets_depthAndBodyLineFaults_summaryIsCoherentAndIssuesReflectInjectedFaults() {
        val tracker = SetTracker(rule)

        // Set 1: one clean deep rep, then a shallow rep (depth fault), body line fine throughout.
        tracker.startSet()
        listOf(top(0), deep(100), top(200), shallow(300), top(400)).forEach { tracker.onFrame(it) }
        tracker.endSet()

        // Set 2: a deep rep whose body line is broken on the down frames (well over 30% of the rep's
        // visible frames < 160°) -> rep-level PUSH_UP_BODY_LINE_BROKEN; then a clean deep rep.
        tracker.startSet()
        listOf(
            top(1000),
            deep(1100, bodyLineDeg = 130f), deep(1150, bodyLineDeg = 130f), deep(1200, bodyLineDeg = 130f),
            top(1300),
            deep(1400), top(1500),
        ).forEach { tracker.onFrame(it) }
        tracker.endSet()

        val summary = SessionSummarizer.summarize(tracker.build(startedAtMs = 0L))

        // --- Counts: set1 = 2 reps (1 invalid), set2 = 2 reps (1 invalid) -> 4 total, 2 valid.
        assertThat(summary.exerciseType).isEqualTo(ExerciseType.PUSH_UP)
        assertThat(summary.totalReps).isEqualTo(4)
        assertThat(summary.validReps).isEqualTo(2)
        assertThat(summary.sets.map { it.setNo }).containsExactly(1, 2).inOrder()
        assertThat(summary.sets[0].reps).hasSize(2)
        assertThat(summary.sets[1].reps).hasSize(2)

        // --- Overall + axis means coherent (0..100), push-up axes present.
        assertThat(summary.overall).isIn(0..100)
        assertThat(summary.axes.map { it.key }).containsExactly("depth", "body_line")
        summary.axes.forEach { assertThat(it.score).isIn(0..100) }
        summary.sets.forEach { set ->
            assertThat(set.overall).isIn(0..100)
            set.axes.forEach { assertThat(it.score).isIn(0..100) }
        }

        // --- topIssues reflect BOTH injected faults: one depth (set 1) + one body-line (set 2).
        val issueCounts = summary.topIssues.associate { it.code to it.count }
        assertThat(issueCounts.keys)
            .containsExactly(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH, FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
        assertThat(issueCounts[FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH]).isEqualTo(1)
        assertThat(issueCounts[FeedbackCode.PUSH_UP_BODY_LINE_BROKEN]).isEqualTo(1)

        // --- Both axes are marked failed at session level (each failed in >=1 rep).
        assertThat(summary.axes.single { it.key == "depth" }.failed).isTrue()
        assertThat(summary.axes.single { it.key == "body_line" }.failed).isTrue()
    }

    @Test
    fun allCleanReps_summaryHasNoIssuesAndHighOverall() {
        val tracker = SetTracker(rule)
        tracker.startSet()
        listOf(top(0), deep(100), top(200), deep(300), top(400)).forEach { tracker.onFrame(it) }
        tracker.endSet()

        val summary = SessionSummarizer.summarize(tracker.build(startedAtMs = 0L))

        assertThat(summary.totalReps).isEqualTo(2)
        assertThat(summary.validReps).isEqualTo(2)
        assertThat(summary.topIssues).isEmpty()
        assertThat(summary.overall).isAtLeast(95)
        assertThat(summary.axes.none { it.failed }).isTrue()
    }
}
