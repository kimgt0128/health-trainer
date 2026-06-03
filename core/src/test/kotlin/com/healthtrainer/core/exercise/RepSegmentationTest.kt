package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * Tests for the rep-segmentation hooks [ExerciseRule.isTop] / [ExerciseRule.isDescent].
 *
 * These thresholds are deliberately LOOSER than the good-form BOTTOM bands: a shallow (bad-depth)
 * rep must still register as a descent so the tracker counts it and later records it as a failure
 * (see the "rep state machine과의 계약" section of the pose-rule-authoring skill).
 *
 * Frames are synthetic side-view poses (z = 0), built like SquatRuleTest / PushUpRuleTest.
 */
class RepSegmentationTest {

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

    // --- Squat -------------------------------------------------------------------------------

    private val squat = SquatRule()

    private fun squatFrame(
        shoulder: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        knee: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
        vis: Float = 0.9f,
    ): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_KNEE to knee, LandmarkName.RIGHT_KNEE to knee,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
        return PoseFrame(0L, pairs.map { (n, p) -> lm(n, p.first, p.second, vis) }.associateBy { it.name })
    }

    /** Knee ~130 (shallow squat): above the good-form BOTTOM band (<=110) but below descent (140). */
    private fun squatKnee130(): PoseFrame = squatFrame(
        shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f,
        ankle = 0.7f to 0.42f,
    )

    /** Knee ~155 (barely bent): above the descent threshold -> not a descent. */
    private fun squatKnee155(): PoseFrame = squatFrame(
        shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f,
        ankle = 0.42f to 0.1f,
    )

    /** Standing: knee ~170. */
    private fun squatStanding(): PoseFrame = squatFrame(
        shoulder = 0f to 4f, hip = 0f to 3f, knee = 0.0875f to 2f, ankle = 0f to 1f,
    )

    @Test
    fun squatKnee130_isDescentTrue() {
        val fb = squat.evaluate(squatKnee130())
        // Sanity: this is a shallow rep — too shallow for the good-form BOTTOM band but a real descent.
        assertThat(fb.metrics["kneeAngle"]!!).isGreaterThan(SquatRule.BOTTOM_MAX_KNEE_ANGLE)
        assertThat(fb.metrics["kneeAngle"]!!).isLessThan(SquatRule.REP_DESCENT_MAX_KNEE_ANGLE)
        assertThat(squat.isDescent(fb)).isTrue()
    }

    @Test
    fun squatKnee155_isDescentFalse() {
        val fb = squat.evaluate(squatKnee155())
        assertThat(fb.metrics["kneeAngle"]!!).isGreaterThan(SquatRule.REP_DESCENT_MAX_KNEE_ANGLE)
        assertThat(squat.isDescent(fb)).isFalse()
    }

    @Test
    fun squatStanding_isTopTrueIsDescentFalse() {
        val fb = squat.evaluate(squatStanding())
        assertThat(squat.isTop(fb)).isTrue()
        assertThat(squat.isDescent(fb)).isFalse()
    }

    @Test
    fun squatLowConfidence_isDescentFalse() {
        // No kneeAngle metric -> cannot be a descent.
        val fb = squat.evaluate(squatFrame(0f to 4f, 0f to 3f, 0f to 2f, 0f to 1f, vis = 0.3f))
        assertThat(squat.isDescent(fb)).isFalse()
        assertThat(squat.isTop(fb)).isFalse()
    }

    // --- Push-up -----------------------------------------------------------------------------

    private val pushUp = PushUpRule()

    private fun pushUpFrame(
        shoulder: Pair<Float, Float>,
        elbow: Pair<Float, Float>,
        wrist: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
        vis: Float = 0.9f,
    ): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_WRIST to wrist, LandmarkName.RIGHT_WRIST to wrist,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
        return PoseFrame(0L, pairs.map { (n, p) -> lm(n, p.first, p.second, vis) }.associateBy { it.name })
    }

    // Elbow vertex at origin, forearm straight down to the floor; shoulder rotated off straight-down
    // by the target angle. Hip & ankle are placed collinear with the shoulder so the body line is a
    // straight 180 regardless of shoulder position (decouples elbow angle from body-line angle).

    /** Elbow ~120 (shallow push-up): above the good-form BOTTOM band (<=100) but below descent (130). */
    private fun pushUpElbow120(): PoseFrame = pushUpFrame(
        shoulder = -0.866f to 0.5f, elbow = 0f to 0f, wrist = 0f to -1f,
        hip = -1.866f to 0.45f, ankle = -2.866f to 0.4f,
    )

    /** Elbow ~145 (barely bent): above descent threshold -> not a descent. */
    private fun pushUpElbow145(): PoseFrame = pushUpFrame(
        shoulder = -0.574f to 0.819f, elbow = 0f to 0f, wrist = 0f to -1f,
        hip = -1.574f to 0.769f, ankle = -2.574f to 0.719f,
    )

    /** Top (arms extended): elbow ~180. */
    private fun pushUpTop(): PoseFrame = pushUpFrame(
        shoulder = 0f to 1f, elbow = 0f to 0f, wrist = 0f to -1f,
        hip = -1f to 0.95f, ankle = -2f to 0.9f,
    )

    @Test
    fun pushUpElbow120_isDescentTrue() {
        val fb = pushUp.evaluate(pushUpElbow120())
        assertThat(fb.metrics["elbowAngle"]!!).isGreaterThan(PushUpRule.BOTTOM_MAX_ELBOW_ANGLE)
        assertThat(fb.metrics["elbowAngle"]!!).isLessThan(PushUpRule.REP_DESCENT_MAX_ELBOW_ANGLE)
        assertThat(pushUp.isDescent(fb)).isTrue()
    }

    @Test
    fun pushUpElbow145_isDescentFalse() {
        val fb = pushUp.evaluate(pushUpElbow145())
        assertThat(fb.metrics["elbowAngle"]!!).isGreaterThan(PushUpRule.REP_DESCENT_MAX_ELBOW_ANGLE)
        assertThat(pushUp.isDescent(fb)).isFalse()
    }

    @Test
    fun pushUpTop_isTopTrueIsDescentFalse() {
        val fb = pushUp.evaluate(pushUpTop())
        assertThat(pushUp.isTop(fb)).isTrue()
        assertThat(pushUp.isDescent(fb)).isFalse()
    }

    // --- Plank (inherits defaults) -----------------------------------------------------------

    @Test
    fun plankHold_neverTopNeverDescent() {
        // Plank phase is HOLD -> inherited defaults make it neither a top nor a descent,
        // so it is never rep-counted (it is handled as a hold by the SetTracker).
        val plank = PlankRule()
        val frame = pushUpFrame(
            shoulder = 0f to 1f, elbow = 0f to 0.8f, wrist = 0f to 0.8f,
            hip = -1f to 1f, ankle = -2f to 1f,
        )
        val fb = plank.evaluate(frame)
        assertThat(fb.phase).isEqualTo(MovementPhase.HOLD)
        assertThat(plank.isTop(fb)).isFalse()
        assertThat(plank.isDescent(fb)).isFalse()
    }
}
