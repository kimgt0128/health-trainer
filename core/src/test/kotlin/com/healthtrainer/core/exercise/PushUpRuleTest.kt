package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * Tests for [PushUpRule].
 *
 * Synthetic side-view frames in the x-y plane (z = 0), already normalized. The body lies roughly
 * along the x-axis (ankle -> hip -> shoulder). Elbow angle is shoulder-elbow-wrist; body-line angle
 * is shoulder-hip-ankle. Landmarks are mirrored onto both sides so the averaging path is exercised.
 */
class PushUpRuleTest {

    private val rule = PushUpRule()

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

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
        return PoseFrame(
            timestampMs = 0L,
            landmarks = pairs.map { (name, p) -> lm(name, p.first, p.second, vis) }.associateBy { it.name },
        )
    }

    /** Top: elbow ~170 (extended), straight body line (~180). */
    private fun topFrame(): PoseFrame = pushUpFrame(
        shoulder = 4f to 0f,
        elbow = 4f to -1f,
        wrist = 4.17633f to -2f,
        hip = 2f to 0f,
        ankle = 0f to 0f,
    )

    /** Bottom: elbow ~85 (chest down), straight body line. */
    private fun bottomFrame(): PoseFrame = pushUpFrame(
        shoulder = 4f to 0f,
        elbow = 4f to -1f,
        wrist = 4.9962f to -0.91284f,
        hip = 2f to 0f,
        ankle = 0f to 0f,
    )

    /** Shallow bottom: elbow ~120 (> the 105 depth threshold), straight body line. */
    private fun shallowFrame(): PoseFrame = pushUpFrame(
        shoulder = 4f to 0f,
        elbow = 4f to -1f,
        wrist = 4.86603f to -1.5f,
        hip = 2f to 0f,
        ankle = 0f to 0f,
    )

    /** Body line broken: hips piked up so shoulder-hip-ankle ~152 (< 160). Elbow extended (~170). */
    private fun brokenLineFrame(): PoseFrame = pushUpFrame(
        shoulder = 4f to 0f,
        elbow = 4f to -1f,
        wrist = 4.17633f to -2f,
        hip = 2f to 0.5f,
        ankle = 0f to 0f,
    )

    @Test
    fun extendedArms_classifiedAsTop() {
        val result = rule.evaluate(topFrame())
        assertThat(result.phase).isEqualTo(MovementPhase.TOP)
        assertThat(result.metrics["elbowAngle"]!!).isWithin(0.5f).of(170f)
    }

    @Test
    fun chestDown_classifiedAsBottom() {
        val result = rule.evaluate(bottomFrame())
        assertThat(result.phase).isEqualTo(MovementPhase.BOTTOM)
        assertThat(result.metrics["elbowAngle"]!!).isWithin(0.5f).of(85f)
    }

    @Test
    fun straightBody_noBodyLineHardFailure() {
        assertThat(rule.evaluate(topFrame()).hardFailures).isEmpty()
    }

    @Test
    fun brokenBodyLine_emitsPerFrameHardFailure() {
        val result = rule.evaluate(brokenLineFrame())
        assertThat(result.metrics["bodyLineAngle"]!!).isLessThan(160f)
        assertThat(result.hardFailures).contains(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
    }

    @Test
    fun lowConfidence_phaseUnknownAndLowConfidenceWarning() {
        val frame = pushUpFrame(
            shoulder = 4f to 0f, elbow = 4f to -1f, wrist = 4.17633f to -2f,
            hip = 2f to 0f, ankle = 0f to 0f, vis = 0.3f,
        )
        val result = rule.evaluate(frame)
        assertThat(result.phase).isEqualTo(MovementPhase.UNKNOWN)
        assertThat(result.softWarnings).contains(FeedbackCode.LOW_CONFIDENCE)
        assertThat(result.hardFailures).isEmpty()
    }

    @Test
    fun shallowRep_aggregateMarksDepthFailure() {
        // Minimum elbow angle stays ~120 (> 105) -> depth not enough.
        val frames = listOf(rule.evaluate(topFrame()), rule.evaluate(shallowFrame()), rule.evaluate(topFrame()))
        assertThat(rule.evaluate(shallowFrame()).metrics["elbowAngle"]!!).isGreaterThan(105f)
        assertThat(rule.aggregateRep(frames)).contains(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH)
    }

    @Test
    fun deepRep_aggregateDoesNotMarkDepthFailure() {
        val frames = listOf(rule.evaluate(topFrame()), rule.evaluate(bottomFrame()), rule.evaluate(topFrame()))
        assertThat(rule.aggregateRep(frames)).doesNotContain(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH)
    }

    @Test
    fun bodyLineBrokenOverThirtyPercent_aggregateMarksBodyLineBroken() {
        // 2 of 4 frames broken = 50% > 30% -> body line broken at rep level.
        val frames = listOf(
            rule.evaluate(topFrame()),
            rule.evaluate(brokenLineFrame()),
            rule.evaluate(brokenLineFrame()),
            rule.evaluate(bottomFrame()),
        )
        assertThat(rule.aggregateRep(frames)).contains(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
    }

    @Test
    fun bodyLineBrokenAtThirtyPercentOrLess_aggregateDoesNotMarkBodyLineBroken() {
        // 1 of 4 frames broken = 25% (<= 30%) -> not flagged at rep level.
        val frames = listOf(
            rule.evaluate(topFrame()),
            rule.evaluate(brokenLineFrame()),
            rule.evaluate(bottomFrame()),
            rule.evaluate(topFrame()),
        )
        assertThat(rule.aggregateRep(frames)).doesNotContain(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
    }

    private fun lowConfidenceFeedback() = rule.evaluate(
        pushUpFrame(
            shoulder = 4f to 0f, elbow = 4f to -1f, wrist = 4.17633f to -2f,
            hip = 2f to 0f, ankle = 0f to 0f, vis = 0.3f,
        ),
    )

    @Test
    fun bodyLineRatioUsesVisibleFramesNotTotal() {
        // 2 broken + 1 clean visible + 4 low-confidence. Over total (7): 2/7 = 28.6% (<=30%);
        // over visible (3): 2/3 = 66.7% (>30%) -> flagged. Pins the visible-frame denominator.
        val lc = lowConfidenceFeedback()
        val frames = listOf(
            rule.evaluate(brokenLineFrame()),
            rule.evaluate(brokenLineFrame()),
            rule.evaluate(topFrame()),
            lc, lc, lc, lc,
        )
        assertThat(rule.aggregateRep(frames)).contains(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
    }

    @Test
    fun aggregateRep_emptyOrAllLowConfidence_returnsNoFailures() {
        assertThat(rule.aggregateRep(emptyList())).isEmpty()
        val lc = lowConfidenceFeedback()
        assertThat(rule.aggregateRep(listOf(lc, lc))).isEmpty()
    }
}
