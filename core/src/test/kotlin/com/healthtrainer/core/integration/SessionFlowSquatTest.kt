package com.healthtrainer.core.integration

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.SquatRule
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.scoring.SessionSummarizer
import com.healthtrainer.core.testutil.SyntheticPose
import com.healthtrainer.core.tracker.SetTracker
import org.junit.Test

/**
 * End-to-end ("통합테스트") for the SQUAT pipeline: a synthetic [PoseFrame] sequence driven through
 * the REAL composition — [SetTracker] (≥2 sets, mixed valid/invalid reps) -> [ExerciseSession] ->
 * [SessionSummarizer.summarize] -> [SessionSummary]. This proves rule -> tracker -> scorer ->
 * summarizer actually compose; the per-stage unit tests only pin each box in isolation.
 *
 * Frames are built at TARGET joint angles with [SyntheticPose.pointForAngle] (no hand-computed
 * coordinates): the knee angle (hip-knee-ankle) drives phase/depth, the torso angle
 * (shoulder-hip-ankle) drives the lean warning. Mirrored onto both sides so the rule's left/right
 * average path runs.
 */
class SessionFlowSquatTest {

    private val rule = SquatRule()

    /**
     * One squat frame with an explicit [kneeDeg] (hip-knee-ankle) and [torsoDeg] (shoulder-hip-ankle).
     * Hip and knee sit on a vertical line; the ankle is placed for the knee angle and the shoulder
     * for the torso angle, both via [SyntheticPose.pointForAngle].
     */
    private fun frame(tsMs: Long, kneeDeg: Float, torsoDeg: Float, vis: Float = 0.9f): PoseFrame {
        val hip = Point3(0f, 2f, 0f)
        val knee = Point3(0f, 1f, 0f)
        val ankle = SyntheticPose.pointForAngle(a = hip, b = knee, degrees = kneeDeg)
        val shoulder = SyntheticPose.pointForAngle(a = ankle, b = hip, degrees = torsoDeg)
        return SyntheticPose.frame(
            tsMs,
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_KNEE to knee, LandmarkName.RIGHT_KNEE to knee,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
            visibility = vis,
        )
    }

    // Phase anchors: TOP needs knee >= 160; a good BOTTOM is 70..110; a shallow descent is counted
    // (knee < REP_DESCENT_MAX=140) but flagged depth (min knee > DEPTH_MAX=120). Torso < 145 leans.
    private fun top(ts: Long, torsoDeg: Float = 175f) = frame(ts, kneeDeg = 170f, torsoDeg = torsoDeg)
    private fun deep(ts: Long, torsoDeg: Float = 175f) = frame(ts, kneeDeg = 90f, torsoDeg = torsoDeg)
    private fun shallow(ts: Long, torsoDeg: Float = 175f) = frame(ts, kneeDeg = 130f, torsoDeg = torsoDeg)

    @Test
    fun twoSets_mixedReps_summaryIsCoherentAndIssuesReflectInjectedFaults() {
        val tracker = SetTracker(rule)

        // Set 1: a clean deep rep, then a shallow (depth-fault) rep -> set 1 has 1 invalid rep.
        tracker.startSet()
        listOf(top(0), deep(100), top(200), shallow(300), top(400)).forEach { tracker.onFrame(it) }
        tracker.endSet()

        // Set 2: two clean deep reps, but one is performed with a leaning torso (110° < 145°). The
        // torso lean is a SOFT warning, NOT a rep-invalidating failure — only SquatRule.aggregateRep
        // decides validity and it only emits SQUAT_DEPTH_NOT_ENOUGH. So a leaning-but-deep rep stays
        // valid and does NOT enter topIssues; it only depresses the rep's torso-axis SCORE.
        tracker.startSet()
        listOf(top(1000), deep(1100, torsoDeg = 110f), top(1200, torsoDeg = 110f), deep(1300), top(1400))
            .forEach { tracker.onFrame(it) }
        tracker.endSet()

        val session = tracker.build(startedAtMs = 0L)
        val summary = SessionSummarizer.summarize(session)

        // --- Counts: 4 reps total (2 per set), 3 valid (only the shallow rep is invalid).
        assertThat(summary.exerciseType).isEqualTo(ExerciseType.SQUAT)
        assertThat(summary.totalReps).isEqualTo(4)
        assertThat(summary.validReps).isEqualTo(3)
        assertThat(summary.sets.map { it.setNo }).containsExactly(1, 2).inOrder()
        assertThat(summary.sets[0].reps).hasSize(2)
        assertThat(summary.sets[1].reps).hasSize(2)

        // --- Overall + axis means are coherent (0..100) and the squat axes are present.
        assertThat(summary.overall).isIn(0..100)
        assertThat(summary.axes.map { it.key }).containsExactly("depth", "torso")
        summary.axes.forEach { assertThat(it.score).isIn(0..100) }
        summary.sets.forEach { set ->
            assertThat(set.overall).isIn(0..100)
            set.axes.forEach { assertThat(it.score).isIn(0..100) }
        }

        // --- topIssues reflect the injected HARD faults only: the single depth fault. Torso lean is
        // soft (advisory) and never reaches the rep-level failures the tally is built from.
        val issueCounts = summary.topIssues.associate { it.code to it.count }
        assertThat(issueCounts.keys).containsExactly(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
        assertThat(issueCounts[FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH]).isEqualTo(1)
        assertThat(summary.topIssues.map { it.code }).doesNotContain(FeedbackCode.SQUAT_TORSO_LEAN)

        // --- The depth axis is marked failed at the session level (it failed in >=1 rep); the torso
        // axis is NOT failed (no rep was invalidated on torso) but its mean score is pulled below the
        // depth axis by the leaning rep — the honest "score vs. fault" split.
        assertThat(summary.axes.single { it.key == "depth" }.failed).isTrue()
        assertThat(summary.axes.single { it.key == "torso" }.failed).isFalse()
        val torsoMean = summary.axes.single { it.key == "torso" }.score
        assertThat(torsoMean).isLessThan(100)
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
        // Deep + upright -> both axes near full -> high overall.
        assertThat(summary.overall).isAtLeast(95)
        assertThat(summary.axes.none { it.failed }).isTrue()
    }
}
