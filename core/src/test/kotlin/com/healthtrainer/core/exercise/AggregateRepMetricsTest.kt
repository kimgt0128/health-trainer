package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.testutil.SyntheticPose
import org.junit.Test

/**
 * TDD spec for [ExerciseRule.aggregateRepMetrics] — the per-rep angle aggregate that gets persisted
 * onto [com.healthtrainer.core.tracker.RepRecord.metrics] for scoring/reporting.
 *
 * The aggregate MUST be derived from the very same per-frame metric keys the rule already produces
 * in [ExerciseRule.evaluate] and consumes in [ExerciseRule.aggregateRep] (kneeAngle, torsoAngle,
 * elbowAngle, bodyLineAngle, elbowOffset). There is NO second angle-math path — these tests pin that
 * the aggregates equal min/mean/ratio over those existing per-frame metrics, gated by the rule's own
 * visible-frame conventions (low-confidence frames carry no metric and are excluded from denominators).
 */
class AggregateRepMetricsTest {

    // ---------- Squat ----------

    private val squat = SquatRule()

    private fun squatFrame(
        shoulder: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        knee: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
        vis: Float = 0.9f,
    ): PoseFrame = SyntheticPose.frame(
        timestampMs = 0L,
        LandmarkName.LEFT_SHOULDER to Point3(shoulder.first, shoulder.second, 0f),
        LandmarkName.RIGHT_SHOULDER to Point3(shoulder.first, shoulder.second, 0f),
        LandmarkName.LEFT_HIP to Point3(hip.first, hip.second, 0f),
        LandmarkName.RIGHT_HIP to Point3(hip.first, hip.second, 0f),
        LandmarkName.LEFT_KNEE to Point3(knee.first, knee.second, 0f),
        LandmarkName.RIGHT_KNEE to Point3(knee.first, knee.second, 0f),
        LandmarkName.LEFT_ANKLE to Point3(ankle.first, ankle.second, 0f),
        LandmarkName.RIGHT_ANKLE to Point3(ankle.first, ankle.second, 0f),
        visibility = vis,
    )

    private fun squatStanding() = squatFrame(
        shoulder = 0f to 4f, hip = 0f to 3f, knee = 0.0875f to 2f, ankle = 0f to 1f,
    )
    private fun squatDeep() = squatFrame(
        shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.3f to 1f,
    )

    @Test
    fun squat_aggregateMetrics_keysAndValuesMatchTheRulesOwnFrameMetrics() {
        val frames = listOf(squat.evaluate(squatStanding()), squat.evaluate(squatDeep()), squat.evaluate(squatStanding()))
        val agg = squat.aggregateRepMetrics(frames)

        assertThat(agg.keys).containsExactly("min_knee_angle", "mean_torso_angle", "min_torso_angle")

        val knees = frames.mapNotNull { it.metrics["kneeAngle"] }
        val torsos = frames.mapNotNull { it.metrics["torsoAngle"] }
        assertThat(agg["min_knee_angle"]!!).isWithin(0.01f).of(knees.min())
        assertThat(agg["mean_torso_angle"]!!).isWithin(0.01f).of(torsos.average().toFloat())
        assertThat(agg["min_torso_angle"]!!).isWithin(0.01f).of(torsos.min())
        // The deep bottom (~90) must be the rep minimum knee.
        assertThat(agg["min_knee_angle"]!!).isWithin(0.5f).of(90f)
    }

    @Test
    fun squat_aggregateMetrics_excludesLowConfidenceFramesFromDenominators() {
        val lc = squat.evaluate(squatFrame(
            shoulder = 0f to 4f, hip = 0f to 3f, knee = 0f to 2f, ankle = 0f to 1f, vis = 0.3f,
        ))
        assertThat(lc.metrics).isEmpty() // sanity: low-confidence carries no metric
        val frames = listOf(squat.evaluate(squatStanding()), squat.evaluate(squatDeep()), lc, lc)
        val agg = squat.aggregateRepMetrics(frames)

        val knees = frames.mapNotNull { it.metrics["kneeAngle"] } // only the 2 visible frames
        assertThat(agg["min_knee_angle"]!!).isWithin(0.01f).of(knees.min())
    }

    @Test
    fun squat_aggregateMetrics_emptyOrAllLowConfidence_returnsEmpty() {
        assertThat(squat.aggregateRepMetrics(emptyList())).isEmpty()
        val lc = squat.evaluate(squatFrame(
            shoulder = 0f to 4f, hip = 0f to 3f, knee = 0f to 2f, ankle = 0f to 1f, vis = 0.3f,
        ))
        assertThat(squat.aggregateRepMetrics(listOf(lc, lc))).isEmpty()
    }

    // ---------- PushUp ----------

    private val pushUp = PushUpRule()

    private fun pushUpFrame(elbowDeg: Float, bodyLineDeg: Float): PoseFrame {
        val shoulder = Point3(0f, 2f, 0f)
        val elbow = Point3(0.5f, 1.6f, 0f)
        val wrist = SyntheticPose.pointForAngle(a = shoulder, b = elbow, degrees = elbowDeg)
        val hip = Point3(-1f, 2f, 0f)
        val ankle = SyntheticPose.pointForAngle(a = shoulder, b = hip, degrees = bodyLineDeg)
        return SyntheticPose.frame(
            timestampMs = 0L,
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_ELBOW to elbow, LandmarkName.RIGHT_ELBOW to elbow,
            LandmarkName.LEFT_WRIST to wrist, LandmarkName.RIGHT_WRIST to wrist,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
    }

    @Test
    fun pushUp_aggregateMetrics_keysAndValuesMatchTheRulesOwnFrameMetrics() {
        // 2 of 4 frames have a broken body line (140 < 160). broken ratio over visible = 0.5.
        val frames = listOf(
            pushUp.evaluate(pushUpFrame(elbowDeg = 170f, bodyLineDeg = 178f)),
            pushUp.evaluate(pushUpFrame(elbowDeg = 90f, bodyLineDeg = 140f)),
            pushUp.evaluate(pushUpFrame(elbowDeg = 95f, bodyLineDeg = 140f)),
            pushUp.evaluate(pushUpFrame(elbowDeg = 170f, bodyLineDeg = 178f)),
        )
        val agg = pushUp.aggregateRepMetrics(frames)

        assertThat(agg.keys).containsExactly("min_elbow_angle", "min_body_line_angle", "body_line_broken_ratio")

        val elbows = frames.mapNotNull { it.metrics["elbowAngle"] }
        val bodyLines = frames.mapNotNull { it.metrics["bodyLineAngle"] }
        val broken = frames.count { FeedbackCode.PUSH_UP_BODY_LINE_BROKEN in it.hardFailures }
        assertThat(agg["min_elbow_angle"]!!).isWithin(0.01f).of(elbows.min())
        assertThat(agg["min_body_line_angle"]!!).isWithin(0.01f).of(bodyLines.min())
        assertThat(agg["body_line_broken_ratio"]!!).isWithin(0.001f).of(broken.toFloat() / bodyLines.size)
        assertThat(agg["body_line_broken_ratio"]!!).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun pushUp_aggregateMetrics_brokenRatioUsesVisibleFramesNotTotal() {
        // 2 broken + 1 clean visible + 3 low-confidence. Ratio over visible (3) = 2/3, not 2/6.
        val lc = pushUp.evaluate(pushUpFrame(elbowDeg = 170f, bodyLineDeg = 178f).withVisibility(0.3f))
        assertThat(lc.metrics).isEmpty()
        val frames = listOf(
            pushUp.evaluate(pushUpFrame(elbowDeg = 90f, bodyLineDeg = 140f)),
            pushUp.evaluate(pushUpFrame(elbowDeg = 95f, bodyLineDeg = 140f)),
            pushUp.evaluate(pushUpFrame(elbowDeg = 170f, bodyLineDeg = 178f)),
            lc, lc, lc,
        )
        val agg = pushUp.aggregateRepMetrics(frames)
        assertThat(agg["body_line_broken_ratio"]!!).isWithin(0.001f).of(2f / 3f)
    }

    @Test
    fun pushUp_aggregateMetrics_emptyOrAllLowConfidence_returnsEmpty() {
        assertThat(pushUp.aggregateRepMetrics(emptyList())).isEmpty()
        val lc = pushUp.evaluate(pushUpFrame(elbowDeg = 170f, bodyLineDeg = 178f).withVisibility(0.3f))
        assertThat(pushUp.aggregateRepMetrics(listOf(lc, lc))).isEmpty()
    }

    // ---------- Plank ----------

    private val plank = PlankRule()

    private fun plankFrame(
        shoulder: Pair<Float, Float>,
        elbow: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
        vis: Float = 0.9f,
    ): PoseFrame = SyntheticPose.frame(
        timestampMs = 0L,
        LandmarkName.LEFT_SHOULDER to Point3(shoulder.first, shoulder.second, 0f),
        LandmarkName.RIGHT_SHOULDER to Point3(shoulder.first, shoulder.second, 0f),
        LandmarkName.LEFT_ELBOW to Point3(elbow.first, elbow.second, 0f),
        LandmarkName.RIGHT_ELBOW to Point3(elbow.first, elbow.second, 0f),
        LandmarkName.LEFT_HIP to Point3(hip.first, hip.second, 0f),
        LandmarkName.RIGHT_HIP to Point3(hip.first, hip.second, 0f),
        LandmarkName.LEFT_ANKLE to Point3(ankle.first, ankle.second, 0f),
        LandmarkName.RIGHT_ANKLE to Point3(ankle.first, ankle.second, 0f),
        visibility = vis,
    )

    private fun plankValid() = plankFrame(
        shoulder = 0f to 1f, elbow = 0f to 1.6f, hip = 2f to 1.035f, ankle = 4f to 1f,
    )
    private fun plankSag() = plankFrame(
        shoulder = 0f to 1f, elbow = 0f to 1.6f, hip = 2f to 1.5f, ankle = 4f to 1f,
    )
    private fun plankElbowOut() = plankFrame(
        shoulder = 0f to 1f, elbow = 0.4f to 1.6f, hip = 2f to 1.035f, ankle = 4f to 1f,
    )

    @Test
    fun plank_aggregateMetrics_keysAndValuesMatchTheRulesOwnFrameMetrics() {
        val frames = listOf(plank.evaluate(plankValid()), plank.evaluate(plankSag()), plank.evaluate(plankElbowOut()))
        val agg = plank.aggregateRepMetrics(frames)

        assertThat(agg.keys).containsExactly("min_body_line_angle", "max_elbow_offset")

        val bodyLines = frames.mapNotNull { it.metrics["bodyLineAngle"] }
        val offsets = frames.mapNotNull { it.metrics["elbowOffset"] }
        assertThat(agg["min_body_line_angle"]!!).isWithin(0.01f).of(bodyLines.min())
        assertThat(agg["max_elbow_offset"]!!).isWithin(0.001f).of(offsets.max())
        // The elbow-out frame (~0.4) must be the max offset.
        assertThat(agg["max_elbow_offset"]!!).isWithin(0.01f).of(0.4f)
    }

    @Test
    fun plank_aggregateMetrics_emptyOrAllLowConfidence_returnsEmpty() {
        assertThat(plank.aggregateRepMetrics(emptyList())).isEmpty()
        val lc = plank.evaluate(plankFrame(
            shoulder = 0f to 1f, elbow = 0f to 1.6f, hip = 2f to 1.035f, ankle = 4f to 1f, vis = 0.3f,
        ))
        assertThat(plank.aggregateRepMetrics(listOf(lc, lc))).isEmpty()
    }

    /** Rebuild a frame with every landmark forced to [vis] (for the low-confidence path). */
    private fun PoseFrame.withVisibility(vis: Float): PoseFrame =
        PoseFrame(timestampMs, landmarks.mapValues { (_, lm) -> lm.copy(visibility = vis) })
}
