package com.healthtrainer.core.exercise

import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame

/**
 * Rule engine for the push-up, side view.
 *
 * Per-frame ([evaluate]): classify the phase from the elbow angle (shoulder-elbow-wrist) and emit a
 * per-frame hard failure [FeedbackCode.PUSH_UP_BODY_LINE_BROKEN] when the body line
 * (shoulder-hip-ankle) drops below [BODY_LINE_MIN_ANGLE].
 *
 * Rep-level ([aggregateRep]):
 * - [FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH] when the rep's minimum elbow angle stays above
 *   [DEPTH_MAX_ELBOW_ANGLE].
 * - [FeedbackCode.PUSH_UP_BODY_LINE_BROKEN] when the share of frames carrying that per-frame code
 *   exceeds [BODY_LINE_BROKEN_FRAME_RATIO].
 *
 * Thresholds come from the pose-rule-authoring skill (MVP side-view defaults).
 */
class PushUpRule : ExerciseRule {

    override val exerciseType: ExerciseType = ExerciseType.PUSH_UP

    override val mode: ExerciseMode = ExerciseMode.REP_COUNTED

    override fun evaluate(frame: PoseFrame): ExerciseFeedback {
        val elbow = averagedAngle(frame, ELBOW_TRIPLES)
        val bodyLine = averagedAngle(frame, BODY_LINE_TRIPLES)

        // Low-confidence guard: required joints not visible on either side -> can't evaluate.
        if (elbow == null || bodyLine == null) {
            return ExerciseFeedback(
                phase = MovementPhase.UNKNOWN,
                hardFailures = emptySet(),
                softWarnings = setOf(FeedbackCode.LOW_CONFIDENCE),
                metrics = emptyMap(),
            )
        }

        val phase = when {
            elbow >= TOP_MIN_ELBOW_ANGLE -> MovementPhase.TOP
            elbow in BOTTOM_MIN_ELBOW_ANGLE..BOTTOM_MAX_ELBOW_ANGLE -> MovementPhase.BOTTOM
            else -> MovementPhase.UNKNOWN
        }

        val hardFailures = buildSet {
            if (bodyLine < BODY_LINE_MIN_ANGLE) add(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
        }

        return ExerciseFeedback(
            phase = phase,
            hardFailures = hardFailures,
            softWarnings = emptySet(),
            metrics = mapOf(ELBOW_ANGLE to elbow, BODY_LINE_ANGLE to bodyLine),
        )
    }

    override fun aggregateRep(frameFeedbacks: List<ExerciseFeedback>): Set<FeedbackCode> {
        if (frameFeedbacks.isEmpty()) return emptySet()
        return buildSet {
            val minElbow = frameFeedbacks.mapNotNull { it.metrics[ELBOW_ANGLE] }.minOrNull()
            if (minElbow != null && minElbow > DEPTH_MAX_ELBOW_ANGLE) {
                add(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH)
            }

            // Denominator is the *visible* (evaluated) frames, per plan.md ("30% of visible frames").
            // Low-confidence frames carry no bodyLineAngle and must not dilute the ratio.
            val visibleFrames = frameFeedbacks.count { it.metrics.containsKey(BODY_LINE_ANGLE) }
            val brokenFrames = frameFeedbacks.count { FeedbackCode.PUSH_UP_BODY_LINE_BROKEN in it.hardFailures }
            if (visibleFrames > 0 && brokenFrames.toFloat() / visibleFrames > BODY_LINE_BROKEN_FRAME_RATIO) {
                add(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)
            }
        }
    }

    /**
     * Per-rep angle aggregate over the SAME per-frame metric keys [evaluate] emits and [aggregateRep]
     * reads ([ELBOW_ANGLE], [BODY_LINE_ANGLE] + the per-frame [FeedbackCode.PUSH_UP_BODY_LINE_BROKEN])
     * — no recomputation. The broken ratio uses the rule's own visible-frame denominator (frames
     * carrying a body-line angle); frames without a metric are excluded; if no frame carries a given
     * metric, that key is omitted.
     */
    override fun aggregateRepMetrics(frameFeedbacks: List<ExerciseFeedback>): Map<String, Float> {
        val elbows = frameFeedbacks.mapNotNull { it.metrics[ELBOW_ANGLE] }
        val bodyLines = frameFeedbacks.mapNotNull { it.metrics[BODY_LINE_ANGLE] }
        val broken = frameFeedbacks.count { FeedbackCode.PUSH_UP_BODY_LINE_BROKEN in it.hardFailures }
        return buildMap {
            elbows.minOrNull()?.let { put("min_elbow_angle", it) }
            if (bodyLines.isNotEmpty()) {
                put("min_body_line_angle", bodyLines.min())
                put("body_line_broken_ratio", broken.toFloat() / bodyLines.size)
            }
        }
    }

    /**
     * Rep-segmentation descent: looser than the good-form BOTTOM band (<= [BOTTOM_MAX_ELBOW_ANGLE])
     * so a shallow push-up still counts as a rep attempt and is later recorded as a depth failure.
     */
    override fun isDescent(feedback: ExerciseFeedback): Boolean {
        val elbow = feedback.metrics["elbowAngle"] ?: return false
        return elbow < REP_DESCENT_MAX_ELBOW_ANGLE
    }

    /** Averages the angle over the sides whose three joints are all confidently visible. */
    private fun averagedAngle(frame: PoseFrame, triples: List<Triple<LandmarkName, LandmarkName, LandmarkName>>): Float? {
        val angles = triples.mapNotNull { (a, b, c) -> sideAngle(frame, a, b, c) }
        return if (angles.isEmpty()) null else angles.average().toFloat()
    }

    private fun sideAngle(frame: PoseFrame, a: LandmarkName, b: LandmarkName, c: LandmarkName): Float? {
        val pa = visiblePoint(frame, a) ?: return null
        val pb = visiblePoint(frame, b) ?: return null
        val pc = visiblePoint(frame, c) ?: return null
        return AngleCalculator.angleDegrees(pa, pb, pc)
    }

    private fun visiblePoint(frame: PoseFrame, name: LandmarkName): Point3? =
        frame.landmarks[name]?.takeIf { it.visibility >= MIN_VISIBILITY }?.let { Point3(it.x, it.y, it.z) }

    companion object {
        /** Per-frame metric keys produced by [evaluate] — the single source for [aggregateRepMetrics]. */
        const val ELBOW_ANGLE = "elbowAngle"
        const val BODY_LINE_ANGLE = "bodyLineAngle"

        /** Minimum landmark visibility to trust a joint (health-trainer-conventions skill). */
        const val MIN_VISIBILITY = 0.55f

        /** Top (arms extended): elbow angle at or above this (pose-rule-authoring skill). */
        const val TOP_MIN_ELBOW_ANGLE = 155f

        /** Bottom (chest down): elbow angle within this inclusive band (pose-rule-authoring skill). */
        const val BOTTOM_MIN_ELBOW_ANGLE = 70f
        const val BOTTOM_MAX_ELBOW_ANGLE = 100f

        /** Body line broken when shoulder-hip-ankle drops below this (pose-rule-authoring skill). */
        const val BODY_LINE_MIN_ANGLE = 160f

        /**
         * Reference body-line angle for a badly broken (sagging/piking) trunk. The body-line score
         * ramps from 100 at [BODY_LINE_MIN_ANGLE] down to 0 here. Single source for the scorer's
         * body-line ramp. MVP default; tune with footage.
         */
        const val BODY_LINE_BROKEN_ANGLE = 120f

        /** Depth failure when the rep's minimum elbow angle stays above this (pose-rule-authoring). */
        const val DEPTH_MAX_ELBOW_ANGLE = 105f

        /**
         * Reference elbow angle for fully-extended arms (no descent). The depth score ramps from 100
         * at [DEPTH_MAX_ELBOW_ANGLE] down to 0 here. Single source for the scorer's depth ramp. MVP
         * default; tune with footage.
         */
        const val EXTENDED_ELBOW_ANGLE = 170f

        /**
         * Tracker rep-segmentation threshold: a frame counts as a descent when the elbow angle drops
         * below this. Deliberately LOOSER than the good-form BOTTOM band ([BOTTOM_MAX_ELBOW_ANGLE] =
         * 100) and the depth-failure cutoff ([DEPTH_MAX_ELBOW_ANGLE] = 105) so a shallow rep
         * (min elbow ~120) is still counted, then recorded as PUSH_UP_DEPTH_NOT_ENOUGH. MVP default;
         * tune with real footage. (pose-rule-authoring "rep state machine과의 계약")
         */
        const val REP_DESCENT_MAX_ELBOW_ANGLE = 130f

        /** Rep-level body-line failure when the broken-frame share exceeds this (pose-rule-authoring). */
        const val BODY_LINE_BROKEN_FRAME_RATIO = 0.30f

        private val ELBOW_TRIPLES = listOf(
            Triple(LandmarkName.LEFT_SHOULDER, LandmarkName.LEFT_ELBOW, LandmarkName.LEFT_WRIST),
            Triple(LandmarkName.RIGHT_SHOULDER, LandmarkName.RIGHT_ELBOW, LandmarkName.RIGHT_WRIST),
        )
        private val BODY_LINE_TRIPLES = listOf(
            Triple(LandmarkName.LEFT_SHOULDER, LandmarkName.LEFT_HIP, LandmarkName.LEFT_ANKLE),
            Triple(LandmarkName.RIGHT_SHOULDER, LandmarkName.RIGHT_HIP, LandmarkName.RIGHT_ANKLE),
        )
    }
}
