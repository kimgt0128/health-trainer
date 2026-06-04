package com.healthtrainer.core.features

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Geometry
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.LandmarkName.LEFT_ANKLE
import com.healthtrainer.core.pose.LandmarkName.LEFT_FOOT_INDEX
import com.healthtrainer.core.pose.LandmarkName.LEFT_HIP
import com.healthtrainer.core.pose.LandmarkName.LEFT_KNEE
import com.healthtrainer.core.pose.LandmarkName.LEFT_SHOULDER
import com.healthtrainer.core.pose.LandmarkName.RIGHT_ANKLE
import com.healthtrainer.core.pose.LandmarkName.RIGHT_FOOT_INDEX
import com.healthtrainer.core.pose.LandmarkName.RIGHT_HIP
import com.healthtrainer.core.pose.LandmarkName.RIGHT_KNEE
import com.healthtrainer.core.pose.LandmarkName.RIGHT_SHOULDER
import com.healthtrainer.core.pose.PoseFrame
import kotlin.math.abs

/**
 * Builds the squat-form classifier's input: a fixed 12-feature [FloatArray], in the order the model
 * was trained on.
 *
 * ## Train↔inference contract (NAMES + ORDER)
 * [featureNames] mirrors the ml track's squat dataset contract
 * (`ml/src/healthtrainer_ml/squat_pose_dataset.py` `FEATURE_COLUMNS`, surfaced as the generated
 * `feature_config.json`):
 * ```
 * left_knee_angle, right_knee_angle, left_hip_angle, right_hip_angle,
 * left_ankle_angle, right_ankle_angle, spine_angle, torso_lean,
 * left_knee_lateral, right_knee_lateral, symmetry_score, hip_depth
 * ```
 * The NAMES and ORDER are the contract. [extract] returns values in exactly this order; this class
 * is the single place that pins it, so a contract change is one reviewable edit, not scattered
 * indices.
 *
 * ## ⚠️ DRIFT RISK — these formulas are OURS, not the dataset's
 * The Kaggle source (`thashmiladewmini/squat-exercise-pose-dataset`) ships these columns
 * **already computed**; its exact formulas are **not in this repo**. The Python side
 * (`features.py` / `squat_pose_dataset.py`) only pins the column *names*, never the math. So the
 * definitions below are :core's documented best-effort interpretation, NOT a verified match to how
 * the training values were produced. This is the train↔inference drift point: each formula must be
 * validated against real dataset rows before trusting the model's output in production. We do not
 * silently guess — every assumption is spelled out per-feature below. Highest-risk items:
 * `torso_lean` (axis convention / sign), `*_knee_lateral` (coordinate frame, sign, normalization),
 * `hip_depth` (sign, normalization), and `symmetry_score` (the /360 scaling is a guess).
 *
 * ## Visibility gate
 * If any required landmark is below [MIN_VISIBILITY] (or absent), [extract] returns `null` — the
 * model can't run on this frame, so the app defers to the rule engine. Required landmarks are all
 * those listed in [REQUIRED_LANDMARKS]; the full feature vector needs every one of them.
 *
 * Pure :core — no Android / MediaPipe. The frame is assumed already normalized.
 */
class SquatFeatureExtractor : ExerciseFeatureExtractor {

    override val exerciseType: ExerciseType = ExerciseType.SQUAT

    override fun featureNames(): List<String> = FEATURE_NAMES

    override fun extract(frame: PoseFrame): FloatArray? {
        // Resolve every required landmark above the visibility gate, else bail (model can't run).
        val p = HashMap<LandmarkName, Point3>(REQUIRED_LANDMARKS.size)
        for (name in REQUIRED_LANDMARKS) {
            p[name] = visiblePoint(frame, name) ?: return null
        }
        // Non-null by construction: every REQUIRED_LANDMARKS key was just populated.
        fun at(name: LandmarkName): Point3 = p.getValue(name)

        // left_knee_angle = angle(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE); right analogous.
        val leftKneeAngle = AngleCalculator.angleDegrees(at(LEFT_HIP), at(LEFT_KNEE), at(LEFT_ANKLE))
        val rightKneeAngle = AngleCalculator.angleDegrees(at(RIGHT_HIP), at(RIGHT_KNEE), at(RIGHT_ANKLE))

        // left_hip_angle = angle(LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE); right analogous.
        val leftHipAngle = AngleCalculator.angleDegrees(at(LEFT_SHOULDER), at(LEFT_HIP), at(LEFT_KNEE))
        val rightHipAngle = AngleCalculator.angleDegrees(at(RIGHT_SHOULDER), at(RIGHT_HIP), at(RIGHT_KNEE))

        // left_ankle_angle = angle(LEFT_KNEE, LEFT_ANKLE, LEFT_FOOT_INDEX); right analogous.
        val leftAnkleAngle = AngleCalculator.angleDegrees(at(LEFT_KNEE), at(LEFT_ANKLE), at(LEFT_FOOT_INDEX))
        val rightAnkleAngle = AngleCalculator.angleDegrees(at(RIGHT_KNEE), at(RIGHT_ANKLE), at(RIGHT_FOOT_INDEX))

        // Body-line centers (midpoints of the L/R pairs).
        val shoulderCenter = Geometry.midpoint(at(LEFT_SHOULDER), at(RIGHT_SHOULDER))
        val hipCenter = Geometry.midpoint(at(LEFT_HIP), at(RIGHT_HIP))
        val kneeCenter = Geometry.midpoint(at(LEFT_KNEE), at(RIGHT_KNEE))

        // spine_angle = interior angle at the hip of shoulderCenter–hipCenter–kneeCenter (≈180° = straight).
        val spineAngle = AngleCalculator.angleDegrees(shoulderCenter, hipCenter, kneeCenter)

        // torso_lean = angle (deg) between the hipCenter→shoulderCenter vector and the vertical (+y)
        // axis. Reuse the angle primitive with a synthetic point one unit straight up from the hip.
        val verticalRef = Point3(hipCenter.x, hipCenter.y + 1f, hipCenter.z)
        val torsoLean = AngleCalculator.angleDegrees(shoulderCenter, hipCenter, verticalRef)

        // left_knee_lateral = LEFT_KNEE.x − LEFT_ANKLE.x (signed, normalized coords); right analogous.
        val leftKneeLateral = at(LEFT_KNEE).x - at(LEFT_ANKLE).x
        val rightKneeLateral = at(RIGHT_KNEE).x - at(RIGHT_ANKLE).x

        // symmetry_score: 1 = perfectly symmetric. /360 normalizes two angle gaps (each ≤180°).
        val symmetryRaw = 1f - ((abs(leftKneeAngle - rightKneeAngle) + abs(leftHipAngle - rightHipAngle)) / 360f)
        val symmetryScore = symmetryRaw.coerceIn(0f, 1f)

        // hip_depth = vertical hip-to-knee gap (normalized coords); the model learns the sign.
        val hipDepth = hipCenter.y - kneeCenter.y

        return floatArrayOf(
            leftKneeAngle, rightKneeAngle, leftHipAngle, rightHipAngle,
            leftAnkleAngle, rightAnkleAngle, spineAngle, torsoLean,
            leftKneeLateral, rightKneeLateral, symmetryScore, hipDepth,
        )
    }

    /** The frame's [name] as a [Point3] iff present and at or above [MIN_VISIBILITY], else `null`. */
    private fun visiblePoint(frame: PoseFrame, name: LandmarkName): Point3? =
        frame.landmarks[name]?.takeIf { it.visibility >= MIN_VISIBILITY }?.let { Point3(it.x, it.y, it.z) }

    companion object {
        /** Visibility gate — a joint below this is untrusted (health-trainer-conventions: 0.55). */
        const val MIN_VISIBILITY = 0.55f

        /**
         * The 12 feature names, in model order. THIS IS THE CONTRACT — keep identical (names + order)
         * to the ml track's `FEATURE_COLUMNS` / generated `feature_config.json`.
         */
        val FEATURE_NAMES: List<String> = listOf(
            "left_knee_angle", "right_knee_angle", "left_hip_angle", "right_hip_angle",
            "left_ankle_angle", "right_ankle_angle", "spine_angle", "torso_lean",
            "left_knee_lateral", "right_knee_lateral", "symmetry_score", "hip_depth",
        )

        /** Every landmark the full feature vector needs; all must clear [MIN_VISIBILITY]. */
        val REQUIRED_LANDMARKS: List<LandmarkName> = listOf(
            LEFT_SHOULDER, RIGHT_SHOULDER,
            LEFT_HIP, RIGHT_HIP,
            LEFT_KNEE, RIGHT_KNEE,
            LEFT_ANKLE, RIGHT_ANKLE,
            LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
        )
    }
}
