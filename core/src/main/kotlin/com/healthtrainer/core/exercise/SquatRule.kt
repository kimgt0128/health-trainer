package com.healthtrainer.core.exercise

import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame

/**
 * Rule engine for the back/air squat, side view.
 *
 * Per-frame ([evaluate]): classify the phase from the knee angle (hip-knee-ankle) and emit a torso
 * lean soft warning from the torso angle (shoulder-hip-ankle). No per-frame hard failures.
 *
 * Rep-level ([aggregateRep]): a rep that never gets deep enough (minimum knee angle stays above
 * [DEPTH_MAX_KNEE_ANGLE]) is flagged [FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH].
 *
 * Thresholds come from the pose-rule-authoring skill (MVP side-view defaults).
 */
class SquatRule : ExerciseRule {

    override val exerciseType: ExerciseType = ExerciseType.SQUAT

    override fun evaluate(frame: PoseFrame): ExerciseFeedback {
        val knee = averagedAngle(frame, KNEE_TRIPLES)
        val torso = averagedAngle(frame, TORSO_TRIPLES)

        // Low-confidence guard: required joints not visible on either side -> can't evaluate.
        if (knee == null || torso == null) {
            return ExerciseFeedback(
                phase = MovementPhase.UNKNOWN,
                hardFailures = emptySet(),
                softWarnings = setOf(FeedbackCode.LOW_CONFIDENCE),
                metrics = emptyMap(),
            )
        }

        val phase = when {
            knee >= TOP_MIN_KNEE_ANGLE -> MovementPhase.TOP
            knee in BOTTOM_MIN_KNEE_ANGLE..BOTTOM_MAX_KNEE_ANGLE -> MovementPhase.BOTTOM
            else -> MovementPhase.UNKNOWN
        }

        val softWarnings = buildSet {
            if (torso < TORSO_LEAN_MIN_ANGLE) add(FeedbackCode.SQUAT_TORSO_LEAN)
        }

        return ExerciseFeedback(
            phase = phase,
            hardFailures = emptySet(),
            softWarnings = softWarnings,
            metrics = mapOf("kneeAngle" to knee, "torsoAngle" to torso),
        )
    }

    override fun aggregateRep(frameFeedbacks: List<ExerciseFeedback>): Set<FeedbackCode> {
        val minKnee = frameFeedbacks.mapNotNull { it.metrics["kneeAngle"] }.minOrNull()
            ?: return emptySet()
        return buildSet {
            if (minKnee > DEPTH_MAX_KNEE_ANGLE) add(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
        }
    }

    /**
     * Averages the angle over the sides whose three joints are all confidently visible
     * (`>= MIN_VISIBILITY`). Returns `null` when no side is fully visible.
     */
    private fun averagedAngle(frame: PoseFrame, triples: List<Triple<LandmarkName, LandmarkName, LandmarkName>>): Float? {
        val angles = triples.mapNotNull { (a, b, c) -> sideAngle(frame, a, b, c) }
        return if (angles.isEmpty()) null else angles.average().toFloat()
    }

    /** Angle at vertex [b] for one side, or `null` if any of the three joints is missing/low-vis. */
    private fun sideAngle(frame: PoseFrame, a: LandmarkName, b: LandmarkName, c: LandmarkName): Float? {
        val pa = visiblePoint(frame, a) ?: return null
        val pb = visiblePoint(frame, b) ?: return null
        val pc = visiblePoint(frame, c) ?: return null
        return AngleCalculator.angleDegrees(pa, pb, pc)
    }

    private fun visiblePoint(frame: PoseFrame, name: LandmarkName): Point3? =
        frame.landmarks[name]?.takeIf { it.visibility >= MIN_VISIBILITY }?.let { Point3(it.x, it.y, it.z) }

    companion object {
        /** Minimum landmark visibility to trust a joint (health-trainer-conventions skill). */
        const val MIN_VISIBILITY = 0.55f

        /** Standing (TOP): knee angle at or above this (pose-rule-authoring skill). */
        const val TOP_MIN_KNEE_ANGLE = 160f

        /** Bottom of the squat: knee angle within this inclusive band (pose-rule-authoring skill). */
        const val BOTTOM_MIN_KNEE_ANGLE = 70f
        const val BOTTOM_MAX_KNEE_ANGLE = 110f

        /** Torso lean soft warning when shoulder-hip-ankle drops below this (pose-rule-authoring). */
        const val TORSO_LEAN_MIN_ANGLE = 145f

        /** Depth failure when the rep's minimum knee angle stays above this (pose-rule-authoring). */
        const val DEPTH_MAX_KNEE_ANGLE = 120f

        private val KNEE_TRIPLES = listOf(
            Triple(LandmarkName.LEFT_HIP, LandmarkName.LEFT_KNEE, LandmarkName.LEFT_ANKLE),
            Triple(LandmarkName.RIGHT_HIP, LandmarkName.RIGHT_KNEE, LandmarkName.RIGHT_ANKLE),
        )
        private val TORSO_TRIPLES = listOf(
            Triple(LandmarkName.LEFT_SHOULDER, LandmarkName.LEFT_HIP, LandmarkName.LEFT_ANKLE),
            Triple(LandmarkName.RIGHT_SHOULDER, LandmarkName.RIGHT_HIP, LandmarkName.RIGHT_ANKLE),
        )
    }
}
