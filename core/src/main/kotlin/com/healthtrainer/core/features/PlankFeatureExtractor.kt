package com.healthtrainer.core.features

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Geometry
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.LandmarkName.LEFT_ANKLE
import com.healthtrainer.core.pose.LandmarkName.LEFT_HIP
import com.healthtrainer.core.pose.LandmarkName.LEFT_SHOULDER
import com.healthtrainer.core.pose.LandmarkName.RIGHT_ANKLE
import com.healthtrainer.core.pose.LandmarkName.RIGHT_HIP
import com.healthtrainer.core.pose.LandmarkName.RIGHT_SHOULDER
import com.healthtrainer.core.pose.PoseFrame
import kotlin.math.abs

/**
 * Builds the plank-form classifier's input: a fixed 5-feature [FloatArray], in the order the model
 * was trained on.
 *
 * ## Train↔inference contract (NAMES + ORDER)
 * [featureNames] mirrors the ml track's `plank_pose_dataset.py` `FEATURE_COLUMNS`:
 * ```
 * body_line_angle, hip_perp_offset_signed, hip_perp_offset_abs,
 * hip_axial_ratio, required_visible_ratio
 * ```
 *
 * The offset features are expressed in a body frame: shoulder-center -> ankle-center is the body
 * axis, and distances are normalized by that axis length (`L^2`). This matches the Task 1.5 plank
 * dataset adapter, removing pixel-vs-MediaPipe **scale** drift and frame-**rotation** drift.
 *
 * ## 2D projection (depth is dropped)
 * The Vollkorn training source is 2D image keypoints — it has **no z**. So every feature is computed
 * in the image plane: [visiblePoint] projects each landmark to `z = 0` before any geometry. Without
 * this, [AngleCalculator] (which is 3D) would fold MediaPipe's depth into `body_line_angle` at
 * inference, drifting it away from the 2D angle the model was trained on. The offset features already
 * use only x/y, so the projection only changes (and corrects) `body_line_angle`.
 *
 * ## Visibility gate
 * Each center may be built from both sides or the one visible side. This mirrors the Python adapter's
 * `required_visible_ratio`: a frame is extractable if it has at least one visible shoulder, one
 * visible hip, and one visible ankle. If a whole required pair is absent (or below [MIN_VISIBILITY]),
 * [extract] returns `null` so the app defers to the rule engine rather than feeding the model a
 * fabricated input.
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
        val leftAnkle = visiblePoint(frame, LEFT_ANKLE)
        val rightAnkle = visiblePoint(frame, RIGHT_ANKLE)

        val visibleCount = listOf(
            leftShoulder, rightShoulder,
            leftHip, rightHip,
            leftAnkle, rightAnkle,
        ).count { it != null }
        val visibleRatio = visibleCount.toFloat() / REQUIRED_LANDMARKS.size

        val shoulder = midpointOrSingle(leftShoulder, rightShoulder) ?: return null
        val hip = midpointOrSingle(leftHip, rightHip) ?: return null
        val ankle = midpointOrSingle(leftAnkle, rightAnkle) ?: return null

        val axisX = ankle.x - shoulder.x
        val axisY = ankle.y - shoulder.y
        val lengthSquared = axisX * axisX + axisY * axisY
        if (lengthSquared < EPSILON) return null

        val hipX = hip.x - shoulder.x
        val hipY = hip.y - shoulder.y

        // 2D cross product A x (hip - shoulder), divided by L^2:
        // canonical y-down horizontal plank -> sagging hips (hips_low) are positive.
        val hipPerpOffsetSigned = (axisX * hipY - axisY * hipX) / lengthSquared
        val hipAxialRatio = (hipX * axisX + hipY * axisY) / lengthSquared
        // shoulder/hip/ankle were projected to z = 0 in visiblePoint, so this is the 2D angle.
        val bodyLineAngle = AngleCalculator.angleDegrees(shoulder, hip, ankle)

        return floatArrayOf(
            bodyLineAngle,
            hipPerpOffsetSigned,
            abs(hipPerpOffsetSigned),
            hipAxialRatio,
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
         * The 5 feature names, in model order. THIS IS THE CONTRACT — keep identical (names + order)
         * to `ml/src/healthtrainer_ml/plank_pose_dataset.py` `FEATURE_COLUMNS`.
         */
        val FEATURE_NAMES: List<String> = listOf(
            "body_line_angle",
            "hip_perp_offset_signed",
            "hip_perp_offset_abs",
            "hip_axial_ratio",
            "required_visible_ratio",
        )

        val REQUIRED_LANDMARKS: List<LandmarkName> = listOf(
            LEFT_SHOULDER, RIGHT_SHOULDER,
            LEFT_HIP, RIGHT_HIP,
            LEFT_ANKLE, RIGHT_ANKLE,
        )

        private const val EPSILON = 1e-6f
    }
}
