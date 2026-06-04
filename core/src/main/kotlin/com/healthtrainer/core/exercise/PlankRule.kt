package com.healthtrainer.core.exercise

import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Geometry
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import kotlin.math.abs

/**
 * Rule engine for the forearm plank — a static hold, not a counted rep.
 *
 * Per-frame ([evaluate]): when the required joints are confident, the phase is [MovementPhase.HOLD].
 * If the body line (shoulder-hip-ankle) drops below [BODY_LINE_MIN_ANGLE] the hip is either sagging
 * or piking; which one is decided by the hip's position relative to the straight shoulder->ankle
 * line (see below). A horizontal elbow offset beyond [ELBOW_OFFSET_MAX] raises the advisory
 * [FeedbackCode.PLANK_ELBOW_MISALIGNED] soft warning.
 *
 * **Sag / pike convention** (image coordinates, y increases downward): `lineY` is the y of the
 * straight shoulder->ankle line at the hip's x. With `hipDrop = hip.y - lineY`:
 * - `hipDrop > 0` — hip is **below** the line (larger y, toward the ground) -> [PLANK_HIPS_LOW].
 * - `hipDrop < 0` — hip is **above** the line (smaller y) -> [PLANK_HIPS_HIGH].
 *
 * Rep-level ([aggregateRep]): a plank "rep" is the whole hold; returns the union of failure codes
 * that appear in more than [FAILURE_FRAME_RATIO] of the frames.
 *
 * Thresholds come from the pose-rule-authoring skill (MVP side-view defaults). The input frame is
 * assumed already normalized (shoulder width ~ 1), which is what makes the elbow offset comparable.
 */
class PlankRule : ExerciseRule {

    override val exerciseType: ExerciseType = ExerciseType.PLANK

    override fun evaluate(frame: PoseFrame): ExerciseFeedback {
        val shoulder = averagedPoint(frame, LandmarkName.LEFT_SHOULDER, LandmarkName.RIGHT_SHOULDER)
        val elbow = averagedPoint(frame, LandmarkName.LEFT_ELBOW, LandmarkName.RIGHT_ELBOW)
        val hip = averagedPoint(frame, LandmarkName.LEFT_HIP, LandmarkName.RIGHT_HIP)
        val ankle = averagedPoint(frame, LandmarkName.LEFT_ANKLE, LandmarkName.RIGHT_ANKLE)

        // Low-confidence guard: required joints not visible on either side -> can't evaluate.
        if (shoulder == null || elbow == null || hip == null || ankle == null) {
            return ExerciseFeedback(
                phase = MovementPhase.UNKNOWN,
                hardFailures = emptySet(),
                softWarnings = setOf(FeedbackCode.LOW_CONFIDENCE),
                metrics = emptyMap(),
            )
        }

        val bodyLine = AngleCalculator.angleDegrees(shoulder, hip, ankle)
        val elbowOffset = abs(elbow.x - shoulder.x)

        val hardFailures = buildSet {
            if (bodyLine < BODY_LINE_MIN_ANGLE) {
                // y-down: hip below the shoulder->ankle line (hipDrop > 0) = sag = HIPS_LOW.
                val hipDrop = hip.y - lineYAt(hip.x, shoulder, ankle)
                if (hipDrop > 0f) add(FeedbackCode.PLANK_HIPS_LOW) else add(FeedbackCode.PLANK_HIPS_HIGH)
            }
        }

        val softWarnings = buildSet {
            if (elbowOffset > ELBOW_OFFSET_MAX) add(FeedbackCode.PLANK_ELBOW_MISALIGNED)
        }

        return ExerciseFeedback(
            phase = MovementPhase.HOLD,
            hardFailures = hardFailures,
            softWarnings = softWarnings,
            metrics = mapOf("bodyLineAngle" to bodyLine, "elbowOffset" to elbowOffset),
        )
    }

    override fun aggregateRep(frameFeedbacks: List<ExerciseFeedback>): Set<FeedbackCode> {
        // Denominator is the *visible* (evaluated) frames; low-confidence frames carry no
        // bodyLineAngle and must not dilute the hold-quality ratio.
        val visible = frameFeedbacks.count { it.metrics.containsKey("bodyLineAngle") }
        if (visible == 0) return emptySet()
        return FAILURE_CODES.filterTo(mutableSetOf()) { code ->
            val count = frameFeedbacks.count { code in it.hardFailures }
            count.toFloat() / visible > FAILURE_FRAME_RATIO
        }
    }

    /** y on the straight line through [shoulder] and [ankle] at abscissa [x]. */
    private fun lineYAt(x: Float, shoulder: Point3, ankle: Point3): Float {
        val dx = ankle.x - shoulder.x
        // Degenerate (near-vertical body): fall back to the shoulder/ankle midpoint height.
        if (abs(dx) < EPSILON) return (shoulder.y + ankle.y) / 2f
        val t = (x - shoulder.x) / dx
        return shoulder.y + t * (ankle.y - shoulder.y)
    }

    /** Midpoint of a left/right landmark pair, using only the sides that are confidently visible. */
    private fun averagedPoint(frame: PoseFrame, left: LandmarkName, right: LandmarkName): Point3? {
        val pl = visiblePoint(frame, left)
        val pr = visiblePoint(frame, right)
        return when {
            pl != null && pr != null -> Geometry.midpoint(pl, pr)
            pl != null -> pl
            pr != null -> pr
            else -> null
        }
    }

    private fun visiblePoint(frame: PoseFrame, name: LandmarkName): Point3? =
        frame.landmarks[name]?.takeIf { it.visibility >= MIN_VISIBILITY }?.let { Point3(it.x, it.y, it.z) }

    companion object {
        /** Minimum landmark visibility to trust a joint (health-trainer-conventions skill). */
        const val MIN_VISIBILITY = 0.55f

        /** Valid body alignment: shoulder-hip-ankle at or above this (pose-rule-authoring skill). */
        const val BODY_LINE_MIN_ANGLE = 160f

        /** Elbow misaligned soft warning when the normalized horizontal offset exceeds this. */
        const val ELBOW_OFFSET_MAX = 0.25f

        /** Rep-level (hold) failure when a code's frame share exceeds this (pose-rule-authoring). */
        const val FAILURE_FRAME_RATIO = 0.30f

        /** The hard-failure codes a plank hold can accumulate (for the rep-level union). */
        private val FAILURE_CODES = listOf(FeedbackCode.PLANK_HIPS_LOW, FeedbackCode.PLANK_HIPS_HIGH)

        private const val EPSILON = 1e-6f
    }
}
