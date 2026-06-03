package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * Tests for [SquatRule].
 *
 * Synthetic side-view frames are built in the x-y plane (z = 0), already normalized. Knee angle is
 * hip-knee-ankle; torso angle is shoulder-hip-ankle. Both left and right landmarks are placed at
 * the same coordinates so the left/right averaging path is exercised (average == single value).
 *
 * Angle tolerance is 0.5f per the health-trainer-conventions skill.
 */
class SquatRuleTest {

    private val rule = SquatRule()

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

    /**
     * Builds a side-view squat frame from joint positions given once and mirrored onto both sides.
     * Shoulder, hip, knee, ankle are vertically stacked (varying x lets us bend the knee/torso).
     */
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
        return PoseFrame(
            timestampMs = 0L,
            landmarks = pairs.map { (name, p) -> lm(name, p.first, p.second, vis) }.associateBy { it.name },
        )
    }

    /** Standing: knee ~170 (slight bend), torso straight (~180). Vertical stack. */
    private fun standingFrame(): PoseFrame = squatFrame(
        shoulder = 0f to 4f,
        hip = 0f to 3f,
        // Knee pushed slightly forward (+x) so hip-knee-ankle ~= 170, not a perfect 180.
        knee = 0.0875f to 2f,
        ankle = 0f to 1f,
    )

    /** Deep squat: knee ~90 (right angle at the knee), torso reasonably upright (~163). */
    private fun deepFrame(): PoseFrame = squatFrame(
        // hip directly above knee; ankle just in front of knee at same height -> 90 deg at knee.
        shoulder = 0f to 3f,
        hip = 0f to 2f,
        knee = 0f to 1f,
        ankle = 0.3f to 1f,
    )

    @Test
    fun standing_classifiedAsTop() {
        val result = rule.evaluate(standingFrame())
        assertThat(result.phase).isEqualTo(MovementPhase.TOP)
        assertThat(result.metrics["kneeAngle"]!!).isWithin(0.5f).of(170f)
    }

    @Test
    fun deepSquat_classifiedAsBottom() {
        val result = rule.evaluate(deepFrame())
        assertThat(result.phase).isEqualTo(MovementPhase.BOTTOM)
        assertThat(result.metrics["kneeAngle"]!!).isWithin(0.5f).of(90f)
    }

    @Test
    fun deepSquat_hasNoHardFailures() {
        // SquatRule emits no per-frame hard failures.
        assertThat(rule.evaluate(deepFrame()).hardFailures).isEmpty()
    }

    @Test
    fun leaningTorso_emitsTorsoLeanSoftWarning() {
        // Bottom pose but torso pitched forward so shoulder-hip-ankle < 145.
        val leaning = squatFrame(
            shoulder = 1.2f to 2.6f, // shoulder thrown forward over the toes
            hip = 0f to 2f,
            knee = 0f to 1f,
            ankle = 1f to 1f,
        )
        val result = rule.evaluate(leaning)
        assertThat(result.metrics["torsoAngle"]!!).isLessThan(145f)
        assertThat(result.softWarnings).contains(FeedbackCode.SQUAT_TORSO_LEAN)
        assertThat(result.hardFailures).isEmpty()
    }

    @Test
    fun uprightTorso_noTorsoLeanWarning() {
        // Deep frame keeps torso ~ upright (shoulder-hip-ankle well above 145).
        val result = rule.evaluate(deepFrame())
        assertThat(result.metrics["torsoAngle"]!!).isAtLeast(145f)
        assertThat(result.softWarnings).doesNotContain(FeedbackCode.SQUAT_TORSO_LEAN)
    }

    @Test
    fun lowConfidence_phaseUnknownAndLowConfidenceWarning() {
        // All required joints at visibility 0.3 (< 0.55) -> cannot evaluate.
        val frame = squatFrame(
            shoulder = 0f to 4f, hip = 0f to 3f, knee = 0f to 2f, ankle = 0f to 1f,
            vis = 0.3f,
        )
        val result = rule.evaluate(frame)
        assertThat(result.phase).isEqualTo(MovementPhase.UNKNOWN)
        assertThat(result.softWarnings).contains(FeedbackCode.LOW_CONFIDENCE)
        assertThat(result.hardFailures).isEmpty()
    }

    @Test
    fun oneSideVisible_usesVisibleSide() {
        // Right side occluded (0.3); left side good (0.9). Should still classify from the left.
        val full = standingFrame().landmarks.toMutableMap()
        for (name in listOf(LandmarkName.RIGHT_SHOULDER, LandmarkName.RIGHT_HIP, LandmarkName.RIGHT_KNEE, LandmarkName.RIGHT_ANKLE)) {
            full[name] = full[name]!!.copy(visibility = 0.3f)
        }
        val result = rule.evaluate(PoseFrame(0L, full))
        assertThat(result.phase).isEqualTo(MovementPhase.TOP)
        assertThat(result.metrics["kneeAngle"]!!).isWithin(0.5f).of(170f)
    }

    @Test
    fun shallowRep_aggregateMarksDepthFailure() {
        // A rep whose minimum knee angle stays ~130 (> 120) -> depth not enough.
        // knee ~130: place ankle so hip-knee-ankle ~= 130.
        val shallowBottom = squatFrame(
            shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f,
            ankle = 0.7f to 0.42f, // tan: gives ~130 at the knee
        )
        val frames = listOf(rule.evaluate(standingFrame()), rule.evaluate(shallowBottom), rule.evaluate(standingFrame()))
        // Sanity: the shallow bottom really is ~130 and above the 120 threshold.
        assertThat(rule.evaluate(shallowBottom).metrics["kneeAngle"]!!).isGreaterThan(120f)
        assertThat(rule.aggregateRep(frames)).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
    }

    @Test
    fun deepRep_aggregateDoesNotMarkDepthFailure() {
        // A rep reaching ~90 at the bottom -> deep enough, no depth failure.
        val frames = listOf(rule.evaluate(standingFrame()), rule.evaluate(deepFrame()), rule.evaluate(standingFrame()))
        assertThat(rule.aggregateRep(frames)).doesNotContain(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
    }
}
