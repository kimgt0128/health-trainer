package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * Tests for [PlankRule].
 *
 * Synthetic forearm-plank frames in the x-y plane (z = 0), already normalized, in **image
 * coordinates: y increases downward** (the runtime convention). The body lies roughly along the
 * x-axis with the shoulders on the left (x = 0) and ankles on the right (x = 4); the hip sits in
 * the middle. Body-line angle is shoulder-hip-ankle. Elbow offset is |elbow.x - shoulder.x| in
 * normalized (shoulder-width) units. Landmarks are mirrored onto both sides.
 *
 * Sag / pike convention (locked by [hipsSag_marksHipsLow] and [hipsPike_marksHipsHigh]): the hip's
 * y is compared to the straight shoulder->ankle line at the hip's x. Hip *below* that line (larger
 * y, toward the ground) -> HIPS_LOW; hip *above* it (smaller y) -> HIPS_HIGH.
 */
class PlankRuleTest {

    private val rule = PlankRule()

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

    private fun plankFrame(
        shoulder: Pair<Float, Float>,
        elbow: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
        vis: Float = 0.9f,
    ): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
        return PoseFrame(
            timestampMs = 0L,
            landmarks = pairs.map { (name, p) -> lm(name, p.first, p.second, vis) }.associateBy { it.name },
        )
    }

    /** Valid hold: body line ~178, elbow directly under the shoulder (offset ~0). */
    private fun validFrame(): PoseFrame = plankFrame(
        shoulder = 0f to 1f,
        elbow = 0f to 1.6f, // below the shoulder (larger y), same x -> offset 0
        hip = 2f to 1.035f, // just off the straight line -> ~178 deg
        ankle = 4f to 1f,
    )

    /** Hips sagging toward the ground (larger y at the hip) -> body line ~152. */
    private fun sagFrame(): PoseFrame = plankFrame(
        shoulder = 0f to 1f,
        elbow = 0f to 1.6f,
        hip = 2f to 1.5f,
        ankle = 4f to 1f,
    )

    /** Hips piking up (smaller y at the hip) -> body line ~152. */
    private fun pikeFrame(): PoseFrame = plankFrame(
        shoulder = 0f to 1f,
        elbow = 0f to 1.6f,
        hip = 2f to 0.5f,
        ankle = 4f to 1f,
    )

    @Test
    fun confidentValidHold_classifiedAsHold() {
        assertThat(rule.evaluate(validFrame()).phase).isEqualTo(MovementPhase.HOLD)
    }

    @Test
    fun validHold_hasNoFailures() {
        val result = rule.evaluate(validFrame())
        assertThat(result.metrics["bodyLineAngle"]!!).isAtLeast(160f)
        assertThat(result.metrics["elbowOffset"]!!).isLessThan(0.25f)
        assertThat(result.hardFailures).isEmpty()
        assertThat(result.softWarnings).isEmpty()
    }

    @Test
    fun hipsSag_marksHipsLow() {
        val result = rule.evaluate(sagFrame())
        assertThat(result.metrics["bodyLineAngle"]!!).isLessThan(160f)
        assertThat(result.hardFailures).contains(FeedbackCode.PLANK_HIPS_LOW)
        assertThat(result.hardFailures).doesNotContain(FeedbackCode.PLANK_HIPS_HIGH)
    }

    @Test
    fun hipsPike_marksHipsHigh() {
        val result = rule.evaluate(pikeFrame())
        assertThat(result.metrics["bodyLineAngle"]!!).isLessThan(160f)
        assertThat(result.hardFailures).contains(FeedbackCode.PLANK_HIPS_HIGH)
        assertThat(result.hardFailures).doesNotContain(FeedbackCode.PLANK_HIPS_LOW)
    }

    @Test
    fun elbowAheadOfShoulder_emitsElbowMisalignedSoftWarning() {
        // Valid body line, but elbow shifted forward of the shoulder by 0.4 (> 0.25).
        val frame = plankFrame(
            shoulder = 0f to 1f,
            elbow = 0.4f to 1.6f,
            hip = 2f to 1.035f,
            ankle = 4f to 1f,
        )
        val result = rule.evaluate(frame)
        assertThat(result.metrics["elbowOffset"]!!).isGreaterThan(0.25f)
        assertThat(result.softWarnings).contains(FeedbackCode.PLANK_ELBOW_MISALIGNED)
        // Elbow misalignment is advisory, not a rep-invalidating hard failure.
        assertThat(result.hardFailures).doesNotContain(FeedbackCode.PLANK_ELBOW_MISALIGNED)
    }

    @Test
    fun lowConfidence_phaseUnknownAndLowConfidenceWarning() {
        val frame = plankFrame(
            shoulder = 0f to 1f, elbow = 0f to 1.6f, hip = 2f to 1.035f, ankle = 4f to 1f,
            vis = 0.3f,
        )
        val result = rule.evaluate(frame)
        assertThat(result.phase).isEqualTo(MovementPhase.UNKNOWN)
        assertThat(result.softWarnings).contains(FeedbackCode.LOW_CONFIDENCE)
        assertThat(result.hardFailures).isEmpty()
    }

    @Test
    fun holdMostlyValid_aggregateHasNoFailures() {
        // 1 of 4 frames sagging = 25% (<= 30%) -> no rep-level failure.
        val frames = listOf(
            rule.evaluate(validFrame()),
            rule.evaluate(sagFrame()),
            rule.evaluate(validFrame()),
            rule.evaluate(validFrame()),
        )
        assertThat(rule.aggregateRep(frames)).isEmpty()
    }

    @Test
    fun holdMostlySagging_aggregateMarksHipsLow() {
        // 3 of 4 frames sagging = 75% (> 30%) -> rep-level HIPS_LOW.
        val frames = listOf(
            rule.evaluate(sagFrame()),
            rule.evaluate(sagFrame()),
            rule.evaluate(sagFrame()),
            rule.evaluate(validFrame()),
        )
        val result = rule.aggregateRep(frames)
        assertThat(result).contains(FeedbackCode.PLANK_HIPS_LOW)
        assertThat(result).doesNotContain(FeedbackCode.PLANK_HIPS_HIGH)
    }

    @Test
    fun holdSaggingAndPikingMix_aggregateUnionsCodesOverThreshold() {
        // 2/5 sag (40%) + 2/5 pike (40%): both exceed 30% -> union of both codes.
        val frames = listOf(
            rule.evaluate(sagFrame()),
            rule.evaluate(sagFrame()),
            rule.evaluate(pikeFrame()),
            rule.evaluate(pikeFrame()),
            rule.evaluate(validFrame()),
        )
        val result = rule.aggregateRep(frames)
        assertThat(result).containsAtLeast(FeedbackCode.PLANK_HIPS_LOW, FeedbackCode.PLANK_HIPS_HIGH)
    }

    private fun lowConfidenceFeedback() = rule.evaluate(
        plankFrame(shoulder = 0f to 1f, elbow = 0f to 1.6f, hip = 2f to 1.035f, ankle = 4f to 1f, vis = 0.3f),
    )

    @Test
    fun holdRatioUsesVisibleFramesNotTotal() {
        // 2 sag + 2 valid (visible=4) + 3 low-confidence. Over total (7): 2/7 = 28.6% (<=30%);
        // over visible (4): 2/4 = 50% (>30%) -> HIPS_LOW flagged. Pins the visible-frame denominator.
        val lc = lowConfidenceFeedback()
        val frames = listOf(
            rule.evaluate(sagFrame()),
            rule.evaluate(sagFrame()),
            rule.evaluate(validFrame()),
            rule.evaluate(validFrame()),
            lc, lc, lc,
        )
        assertThat(rule.aggregateRep(frames)).contains(FeedbackCode.PLANK_HIPS_LOW)
    }

    @Test
    fun aggregateRep_emptyOrAllLowConfidence_returnsNoFailures() {
        assertThat(rule.aggregateRep(emptyList())).isEmpty()
        val lc = lowConfidenceFeedback()
        assertThat(rule.aggregateRep(listOf(lc, lc))).isEmpty()
    }
}
