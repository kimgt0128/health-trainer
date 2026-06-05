package com.healthtrainer.core.features

import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.MovementPhase

/**
 * Builds the push-up-form classifier's input: a fixed 10-feature [FloatArray], in the order the model
 * was trained on. This is a **rep-level** extractor ([RepFeatureExtractor]) — it summarizes a whole
 * completed rep, unlike the frame-level [SquatFeatureExtractor].
 *
 * ## Train↔inference contract (NAMES + ORDER)
 * [featureNames] is the contract with the ml track's push-up rep dataset (`FEATURE_COLUMNS`, surfaced
 * as the generated `feature_config.json`):
 * ```
 * min_elbow_angle, max_elbow_angle, mean_elbow_angle, elbow_angle_range,
 * min_body_line_angle, mean_body_line_angle, body_line_broken_ratio,
 * visible_frame_ratio, rep_duration_ms, down_phase_ratio
 * ```
 * The NAMES and ORDER are the contract. [extract] returns values in exactly this order; this class is
 * the single place that pins it, so a contract change is one reviewable edit, not scattered indices.
 *
 * ## Zero-recomputation source
 * The inputs are the rep's per-frame [ExerciseFeedback]s, already produced by
 * [com.healthtrainer.core.exercise.PushUpRule.evaluate]: `metrics["elbowAngle"]`,
 * `metrics["bodyLineAngle"]`, [ExerciseFeedback.phase], and the per-frame
 * [FeedbackCode.PUSH_UP_BODY_LINE_BROKEN] hard failure. No angle is recomputed here, so there is no
 * second math path to drift from the rule engine.
 *
 * ## Visibility gate
 * If no frame carried an `elbowAngle` (every requested elbow joint occluded across the whole rep),
 * [extract] returns `null` — the model can't run on this rep, so the app defers to the rule engine.
 * Body-line samples are not gated the same way: when they are simply absent (but elbows present), the
 * body-line features fall back to `0f` rather than nulling the whole rep.
 *
 * Pure :core — no Android / MediaPipe.
 */
class PushUpFeatureExtractor : RepFeatureExtractor {

    override val exerciseType: ExerciseType = ExerciseType.PUSH_UP

    override fun featureNames(): List<String> = FEATURE_NAMES

    override fun extract(frameFeedbacks: List<ExerciseFeedback>, repDurationMs: Long): FloatArray? {
        val elbows = frameFeedbacks.mapNotNull { it.metrics[ELBOW_ANGLE] }
        // Gate: no elbow samples at all -> every requested joint occluded -> model can't run.
        if (elbows.isEmpty()) return null

        val bodyLines = frameFeedbacks.mapNotNull { it.metrics[BODY_LINE_ANGLE] }
        val total = frameFeedbacks.size

        val minElbow = elbows.min()
        val maxElbow = elbows.max()
        val meanElbow = elbows.average().toFloat()
        val elbowRange = maxElbow - minElbow

        // Body-line min/mean: 0f when there are no body-line samples (elbows still present).
        val minBodyLine = if (bodyLines.isEmpty()) 0f else bodyLines.min()
        val meanBodyLine = if (bodyLines.isEmpty()) 0f else bodyLines.average().toFloat()

        // Broken ratio denominator is the body-line-bearing (visible) frames, per the rule's
        // body-line ratio contract. Low-confidence frames (no bodyLineAngle) must not dilute it.
        val brokenFrames = frameFeedbacks.count { FeedbackCode.PUSH_UP_BODY_LINE_BROKEN in it.hardFailures }
        val bodyLineBrokenRatio = if (bodyLines.isEmpty()) 0f else brokenFrames.toFloat() / bodyLines.size

        // Visible ratio: elbow-bearing frames over all frames.
        val visibleFrameRatio = if (total == 0) 0f else elbows.size.toFloat() / total

        val repDurationFloat = repDurationMs.toFloat()

        // Down ratio: BOTTOM frames over all frames.
        val bottomFrames = frameFeedbacks.count { it.phase == MovementPhase.BOTTOM }
        val downPhaseRatio = if (total == 0) 0f else bottomFrames.toFloat() / total

        return floatArrayOf(
            minElbow, maxElbow, meanElbow, elbowRange,
            minBodyLine, meanBodyLine, bodyLineBrokenRatio,
            visibleFrameRatio, repDurationFloat, downPhaseRatio,
        )
    }

    companion object {
        /** Metric keys produced by [com.healthtrainer.core.exercise.PushUpRule.evaluate]. */
        private const val ELBOW_ANGLE = "elbowAngle"
        private const val BODY_LINE_ANGLE = "bodyLineAngle"

        /**
         * The 10 feature names, in model order. THIS IS THE CONTRACT — keep identical (names + order)
         * to the ml track's push-up `FEATURE_COLUMNS` / generated `feature_config.json`.
         */
        val FEATURE_NAMES: List<String> = listOf(
            "min_elbow_angle", "max_elbow_angle", "mean_elbow_angle", "elbow_angle_range",
            "min_body_line_angle", "mean_body_line_angle", "body_line_broken_ratio",
            "visible_frame_ratio", "rep_duration_ms", "down_phase_ratio",
        )
    }
}
