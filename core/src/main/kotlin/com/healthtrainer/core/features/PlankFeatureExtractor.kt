package com.healthtrainer.core.features

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Geometry
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.LandmarkName.LEFT_ANKLE
import com.healthtrainer.core.pose.LandmarkName.LEFT_HIP
import com.healthtrainer.core.pose.LandmarkName.LEFT_KNEE
import com.healthtrainer.core.pose.LandmarkName.LEFT_SHOULDER
import com.healthtrainer.core.pose.LandmarkName.RIGHT_ANKLE
import com.healthtrainer.core.pose.LandmarkName.RIGHT_HIP
import com.healthtrainer.core.pose.LandmarkName.RIGHT_KNEE
import com.healthtrainer.core.pose.LandmarkName.RIGHT_SHOULDER
import com.healthtrainer.core.pose.PoseFrame
import kotlin.math.abs

/**
 * Builds the plank-form classifier's input: a fixed 8-feature [FloatArray], in the order the model
 * was trained on.
 *
 * ## Train↔inference contract (NAMES + ORDER)
 * [featureNames] mirrors the ml track's `plank_pose_dataset.py` `FEATURE_COLUMNS`:
 * ```
 * body_line_angle, knee_line_angle, hip_perp_offset_signed, hip_perp_offset_abs,
 * hip_axial_ratio, knee_perp_offset_signed, knee_axial_ratio, required_visible_ratio
 * ```
 *
 * The offset features are expressed in a body frame: shoulder-center -> ankle-center is the body
 * axis, and distances are normalized by that axis length (`L^2`). This matches the plank dataset
 * adapter, removing pixel-vs-MediaPipe **scale** drift and frame-**rotation** drift.
 *
 * ## The knee (label-defining joint)
 * The source label was generated from the **shoulder-hip-knee** back angle, so an ankle-only feature
 * set under-performed (~0.57 macro-F1). `knee_line_angle` (shoulder-hip-knee) + the knee body-frame
 * offsets are complementary to the ankle features and lift the model over the ship bar (~0.76-0.79).
 *
 * ## 2D projection (depth is dropped)
 * The training source is 2D image keypoints — it has **no z**. So every feature is computed in the
 * image plane: [visiblePoint] projects each landmark to `z = 0` before any geometry. Without this,
 * [AngleCalculator] (3D) would fold MediaPipe depth into the angles at inference, drifting them from
 * the 2D angles the model trained on. The offset features already use only x/y.
 *
 * ## Visibility gate
 * Each center may be built from both sides or the one visible side. A frame is extractable if it has
 * at least one visible shoulder, hip, knee, and ankle; if a whole required pair is absent (or below
 * [MIN_VISIBILITY]), [extract] returns `null` so the app defers to the rule engine.
 *
 * Pure :core — no Android / MediaPipe. The frame is assumed already normalized (the normalizer is a
 * translation + uniform scale, which these rotation/scale-invariant features are unaffected by).
 */
class PlankFeatureExtractor : ExerciseFeatureExtractor {

    override val exerciseType: ExerciseType = ExerciseType.PLANK

    override fun featureNames(): List<String> = FEATURE_NAMES

    override fun extract(frame: PoseFrame): FloatArray? {
        val leftShoulder = visiblePoint(frame, LEFT_SHOULDER)
        val rightShoulder = visiblePoint(frame, RIGHT_SHOULDER)
        val leftHip = visiblePoint(frame, LEFT_HIP)
        val rightHip = visiblePoint(frame, RIGHT_HIP)
        val leftKnee = visiblePoint(frame, LEFT_KNEE)
        val rightKnee = visiblePoint(frame, RIGHT_KNEE)
        val leftAnkle = visiblePoint(frame, LEFT_ANKLE)
        val rightAnkle = visiblePoint(frame, RIGHT_ANKLE)

        val visibleCount = listOf(
            leftShoulder, rightShoulder,
            leftHip, rightHip,
            leftKnee, rightKnee,
            leftAnkle, rightAnkle,
        ).count { it != null }
        val visibleRatio = visibleCount.toFloat() / REQUIRED_LANDMARKS.size

        val shoulder = midpointOrSingle(leftShoulder, rightShoulder) ?: return null
        val hip = midpointOrSingle(leftHip, rightHip) ?: return null
        val knee = midpointOrSingle(leftKnee, rightKnee) ?: return null
        val ankle = midpointOrSingle(leftAnkle, rightAnkle) ?: return null

        val axisX = ankle.x - shoulder.x
        val axisY = ankle.y - shoulder.y
        val lengthSquared = axisX * axisX + axisY * axisY
        if (lengthSquared < EPSILON) return null

        val hipX = hip.x - shoulder.x
        val hipY = hip.y - shoulder.y
        val kneeX = knee.x - shoulder.x
        val kneeY = knee.y - shoulder.y

        // 2D cross product A x (point - shoulder), divided by L^2:
        // canonical y-down horizontal plank -> sagging hips (hips_low) are positive.
        val hipPerpOffsetSigned = (axisX * hipY - axisY * hipX) / lengthSquared
        val kneePerpOffsetSigned = (axisX * kneeY - axisY * kneeX) / lengthSquared
        val hipAxialRatio = (hipX * axisX + hipY * axisY) / lengthSquared
        val kneeAxialRatio = (kneeX * axisX + kneeY * axisY) / lengthSquared
        // points were projected to z = 0 in visiblePoint, so these are 2D angles.
        val bodyLineAngle = AngleCalculator.angleDegrees(shoulder, hip, ankle)
        val kneeLineAngle = AngleCalculator.angleDegrees(shoulder, hip, knee)

        return floatArrayOf(
            bodyLineAngle,
            kneeLineAngle,
            hipPerpOffsetSigned,
            abs(hipPerpOffsetSigned),
            hipAxialRatio,
            kneePerpOffsetSigned,
            kneeAxialRatio,
            visibleRatio,
        )
    }

    private fun midpointOrSingle(a: Point3?, b: Point3?): Point3? = when {
        a != null && b != null -> Geometry.midpoint(a, b)
        a != null -> a
        b != null -> b
        else -> null
    }

    /**
     * The frame's [name] as a [Point3] iff present and at or above [MIN_VISIBILITY], **projected to
     * `z = 0`** (the training source is 2D image coordinates — see the class doc).
     */
    private fun visiblePoint(frame: PoseFrame, name: LandmarkName): Point3? =
        frame.landmarks[name]?.takeIf { it.visibility >= MIN_VISIBILITY }?.let { Point3(it.x, it.y, 0f) }

    companion object {
        /** Visibility gate shared with the rule engines and other feature extractors. */
        const val MIN_VISIBILITY = 0.55f

        /**
         * The 8 feature names, in model order. THIS IS THE CONTRACT — keep identical (names + order)
         * to `ml/src/healthtrainer_ml/plank_pose_dataset.py` `FEATURE_COLUMNS`.
         */
        val FEATURE_NAMES: List<String> = listOf(
            "body_line_angle",
            "knee_line_angle",
            "hip_perp_offset_signed",
            "hip_perp_offset_abs",
            "hip_axial_ratio",
            "knee_perp_offset_signed",
            "knee_axial_ratio",
            "required_visible_ratio",
        )

        val REQUIRED_LANDMARKS: List<LandmarkName> = listOf(
            LEFT_SHOULDER, RIGHT_SHOULDER,
            LEFT_HIP, RIGHT_HIP,
            LEFT_KNEE, RIGHT_KNEE,
            LEFT_ANKLE, RIGHT_ANKLE,
        )

        private const val EPSILON = 1e-6f
    }
}
