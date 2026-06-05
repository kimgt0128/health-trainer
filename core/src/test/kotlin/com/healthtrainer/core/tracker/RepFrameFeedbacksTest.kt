package com.healthtrainer.core.tracker

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.MovementPhase
import com.healthtrainer.core.exercise.PushUpRule
import com.healthtrainer.core.exercise.SquatRule
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import com.healthtrainer.core.testutil.SyntheticPose
import org.junit.Test

/**
 * TDD spec for the rep-level feature seam's tracker plumbing.
 *
 * The push-up assist model is rep-level: it classifies a *completed* rep, not a single frame. To
 * feed it without recomputing angles (= zero drift), the tracker must surface the per-frame
 * [com.healthtrainer.core.exercise.ExerciseFeedback]s of the rep that just closed. These tests pin:
 * - [RepStateMachine] captures the closed rep's buffered feedbacks into
 *   [RepRecordData.frameFeedbacks] (before clearing the buffer).
 * - [SetTracker.lastClosedRepFrameFeedbacks] exposes the just-closed rep's feedbacks, and is null
 *   on frames that don't close a rep / outside a set.
 *
 * Additive: the squat frame-level path and the persisted [RepRecord] are untouched.
 */
class RepFrameFeedbacksTest {

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

    // --- Squat frames (reused shape from the other tracker tests) ----------------------------

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
    fun repStateMachine_closedRep_capturesAllBufferedFrameFeedbacks() {
        // TOP(0) -> deep(100) -> deep(200) -> TOP(300): the rep buffers descent + closing frames.
        val frames = listOf(standing(0), deep(100), deep(200), standing(300))
        val reps = RepStateMachine(SquatRule()).process(frames)

        assertThat(reps).hasSize(1)
        // Buffered: deep(100), deep(200), standing(300) = 3 frames (descent-open + mid + closing TOP).
        assertThat(reps[0].frameFeedbacks).hasSize(3)
        // The closing frame is a TOP frame.
        assertThat(reps[0].frameFeedbacks.last().phase).isEqualTo(MovementPhase.TOP)
    }

    @Test
    fun repRecordData_defaultFrameFeedbacks_areEmpty() {
        // RepRecordData built without the new arg keeps the old (non-breaking) behavior: empty list.
        val data = RepRecordData(
            valid = true,
            failures = emptySet(),
            startTimestampMs = 0L,
            endTimestampMs = 1L,
        )
        assertThat(data.frameFeedbacks).isEmpty()
    }

    @Test
    fun setTracker_lastClosedRepFrameFeedbacks_filledOnRepCloseAndNullOtherwise() {
        val tracker = SetTracker(SquatRule())
        tracker.startSet()
        assertThat(tracker.lastClosedRepFrameFeedbacks).isNull()

        tracker.onFrame(standing(0))
        // No rep closed yet -> still null.
        assertThat(tracker.lastClosedRepFrameFeedbacks).isNull()

        tracker.onFrame(deep(100)) // opens the rep, doesn't close
        assertThat(tracker.lastClosedRepFrameFeedbacks).isNull()

        tracker.onFrame(standing(200)) // closes the rep
        val captured = tracker.lastClosedRepFrameFeedbacks
        assertThat(captured).isNotNull()
        // Buffered deep(100) + standing(200) = 2 frames.
        assertThat(captured!!).hasSize(2)
        assertThat(captured.last().phase).isEqualTo(MovementPhase.TOP)
    }

    // --- Push-up: the real consumer (rep feedbacks carry elbow/body-line metrics) -------------

    private fun pushUpFrame(tsMs: Long, elbowDeg: Float, bodyLineDeg: Float): PoseFrame {
        // shoulder-elbow-wrist => elbow angle; shoulder-hip-ankle => body-line angle.
        val shoulder = Point3(0f, 2f, 0f)
        val elbow = Point3(0.5f, 1.6f, 0f)
        val wrist = SyntheticPose.pointForAngle(a = shoulder, b = elbow, degrees = elbowDeg)
        val hip = Point3(-1f, 2f, 0f)
        val ankle = SyntheticPose.pointForAngle(a = shoulder, b = hip, degrees = bodyLineDeg)
        return SyntheticPose.frame(
            timestampMs = tsMs,
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_WRIST to wrist, LandmarkName.RIGHT_WRIST to wrist,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
    }

    @Test
    fun pushUpRep_frameFeedbacksCarryElbowMetricsForTheRepLevelExtractor() {
        // up(170) -> down(90) -> up(170): a valid push-up rep.
        val frames = listOf(
            pushUpFrame(0, elbowDeg = 170f, bodyLineDeg = 178f),
            pushUpFrame(100, elbowDeg = 90f, bodyLineDeg = 178f),
            pushUpFrame(200, elbowDeg = 170f, bodyLineDeg = 178f),
        )
        val reps = RepStateMachine(PushUpRule()).process(frames)
        assertThat(reps).hasSize(1)
        // Buffered: down(100) + up(200) = 2 frames, each carrying an elbowAngle metric.
        val feedbacks = reps[0].frameFeedbacks
        assertThat(feedbacks).hasSize(2)
        assertThat(feedbacks.all { it.metrics.containsKey("elbowAngle") }).isTrue()
    }
}
