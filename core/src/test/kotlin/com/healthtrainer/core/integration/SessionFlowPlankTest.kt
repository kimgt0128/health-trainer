package com.healthtrainer.core.integration

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PlankRule
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.scoring.SessionSummarizer
import com.healthtrainer.core.testutil.SyntheticPose
import com.healthtrainer.core.tracker.SetTracker
import org.junit.Test

/**
 * End-to-end ("통합테스트") for the PLANK pipeline — the HOLD path, a different [ExerciseMode] from the
 * rep-counted exercises. A synthetic [PoseFrame] sequence is driven through the REAL composition:
 * [SetTracker] (≥2 sets, each a hold) -> [ExerciseSession] -> [SessionSummarizer.summarize]. Because
 * plank's mode is HOLD, each SET yields exactly ONE rep record (the whole hold judged once at
 * [SetTracker.endSet] via [com.healthtrainer.core.exercise.ExerciseRule.aggregateRep]), so totalReps
 * == number of holds. This proves the hold branch of the tracker composes with the scorer/summarizer.
 *
 * Body-line frames use [SyntheticPose.pointForAngle] to place the hip at a TARGET shoulder-hip-ankle
 * angle (no hand-computed angle). Sag vs. pike is then the rule's OWN convention — the hip's y
 * relative to the straight shoulder->ankle line (y-down): a hip toward the ground (larger y) sags.
 */
class SessionFlowPlankTest {

    private val rule = PlankRule()

    private val shoulder = Point3(0f, 1f, 0f)
    private val ankle = Point3(4f, 1f, 0f)

    /**
     * One plank frame whose shoulder-hip-ankle body line is [bodyLineDeg]. The hip is placed by
     * [SyntheticPose.pointForAngle] at that angle; [sag] flips it below (toward the ground, larger y)
     * vs. above the straight line so the rule classifies HIPS_LOW vs HIPS_HIGH. The elbow sits under
     * the shoulder (offset 0, no misalignment) unless [elbowX] is given.
     */
    private fun frame(
        tsMs: Long,
        bodyLineDeg: Float,
        sag: Boolean = true,
        elbowX: Float = 0f,
        vis: Float = 0.9f,
    ): PoseFrame {
        // pointForAngle gives a hip at the target shoulder-hip-ankle angle; it lands above the line by
        // construction here. Reflect it across the straight shoulder->ankle line (y=1) to make it sag.
        val hipAbove = SyntheticPose.pointForAngle(a = shoulder, b = Point3(2f, 1f, 0f), degrees = bodyLineDeg, length = 0.6f)
        // Keep x at the midpoint; choose y above (<1) or below (>1) the straight line at the hip's x.
        val dyAbove = 1f - hipAbove.y // positive distance above the line
        val hipY = if (sag) 1f + kotlin.math.abs(dyAbove) else 1f - kotlin.math.abs(dyAbove)
        val hip = Point3(2f, hipY, 0f)
        val elbow = Point3(elbowX, 1.6f, 0f)
        return SyntheticPose.frame(
            tsMs,
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
            visibility = vis,
        )
    }

    // A valid hold: body line near-straight (>= 160°). A sagging hold: body line well under 160°,
    // hip toward the ground -> PLANK_HIPS_LOW.
    private fun good(ts: Long) = frame(ts, bodyLineDeg = 178f)
    private fun sagging(ts: Long) = frame(ts, bodyLineDeg = 120f, sag = true)

    @Test
    fun twoHolds_oneGoodOneSagging_eachSetIsOneHoldAndIssuesReflectTheSag() {
        val tracker = SetTracker(rule)

        // Set 1: a clean hold (all frames near-straight) -> one valid hold record.
        tracker.startSet()
        listOf(good(0), good(100), good(200), good(300)).forEach { tracker.onFrame(it) }
        tracker.endSet()

        // Set 2: a mostly-sagging hold (3 of 4 frames sag, > 30%) -> one invalid hold (HIPS_LOW).
        tracker.startSet()
        listOf(sagging(1000), sagging(1100), sagging(1200), good(1300)).forEach { tracker.onFrame(it) }
        tracker.endSet()

        val summary = SessionSummarizer.summarize(tracker.build(startedAtMs = 0L))

        // --- HOLD accounting: 2 sets, each exactly ONE hold record -> totalReps = 2, validReps = 1.
        assertThat(summary.exerciseType).isEqualTo(ExerciseType.PLANK)
        assertThat(summary.totalReps).isEqualTo(2)
        assertThat(summary.validReps).isEqualTo(1)
        assertThat(summary.sets.map { it.setNo }).containsExactly(1, 2).inOrder()
        assertThat(summary.sets[0].reps).hasSize(1)
        assertThat(summary.sets[1].reps).hasSize(1)
        // The single hold is always rep 1 of its set.
        assertThat(summary.sets[1].reps.single().repNo).isEqualTo(1)

        // --- Overall + axis means coherent (0..100), plank axes present.
        assertThat(summary.overall).isIn(0..100)
        assertThat(summary.axes.map { it.key }).containsExactly("body_line", "elbow")
        summary.axes.forEach { assertThat(it.score).isIn(0..100) }
        summary.sets.forEach { set ->
            assertThat(set.overall).isIn(0..100)
            set.axes.forEach { assertThat(it.score).isIn(0..100) }
        }

        // --- topIssues reflect the injected sag exactly: HIPS_LOW once (set 2's hold), nothing else.
        val issueCounts = summary.topIssues.associate { it.code to it.count }
        assertThat(issueCounts.keys).containsExactly(FeedbackCode.PLANK_HIPS_LOW)
        assertThat(issueCounts[FeedbackCode.PLANK_HIPS_LOW]).isEqualTo(1)

        // --- The body_line axis is failed at session level (the sagging hold failed it); the good
        // hold's set scores higher overall than the sagging set.
        assertThat(summary.axes.single { it.key == "body_line" }.failed).isTrue()
        assertThat(summary.sets[0].overall).isGreaterThan(summary.sets[1].overall)
    }

    @Test
    fun allGoodHold_summaryHasNoIssuesAndHighOverall() {
        val tracker = SetTracker(rule)
        tracker.startSet()
        listOf(good(0), good(100), good(200)).forEach { tracker.onFrame(it) }
        tracker.endSet()

        val summary = SessionSummarizer.summarize(tracker.build(startedAtMs = 0L))

        assertThat(summary.totalReps).isEqualTo(1)
        assertThat(summary.validReps).isEqualTo(1)
        assertThat(summary.topIssues).isEmpty()
        // Straight body line + elbow under shoulder -> both axes full -> overall 100.
        assertThat(summary.overall).isAtLeast(95)
        assertThat(summary.axes.none { it.failed }).isTrue()
    }
}
