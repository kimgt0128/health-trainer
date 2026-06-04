package com.healthtrainer.core.features

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
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
import com.healthtrainer.core.pose.PoseLandmark
import com.healthtrainer.core.testutil.SyntheticPose
import org.junit.Test

/**
 * TDD spec for [SquatFeatureExtractor] — the :core producer of the squat-form model's 12-feature
 * input vector. The feature NAMES + ORDER are the train↔inference contract (mirrors the ml track's
 * `squat_pose_dataset.FEATURE_COLUMNS` / generated `feature_config.json`); the numeric formulas are
 * :core's documented best-effort interpretation (see the extractor's KDoc DRIFT note).
 *
 * Fixtures use [SyntheticPose] so knee angles are construction-guaranteed (no hand-computed coords).
 */
class SquatFeatureExtractorTest {

    private val extractor = SquatFeatureExtractor()

    // ---- contract: names + order + size ---------------------------------------------------------

    @Test
    fun featureNames_areThe12ContractFeaturesInOrder() {
        assertThat(extractor.featureNames()).containsExactly(
            "left_knee_angle", "right_knee_angle", "left_hip_angle", "right_hip_angle",
            "left_ankle_angle", "right_ankle_angle", "spine_angle", "torso_lean",
            "left_knee_lateral", "right_knee_lateral", "symmetry_score", "hip_depth",
        ).inOrder()
    }

    @Test
    fun exerciseType_isSquat() {
        assertThat(extractor.exerciseType).isEqualTo(ExerciseType.SQUAT)
    }

    @Test
    fun extract_returnsVectorOfSize12() {
        val out = extractor.extract(deepSquat())
        assertThat(out).isNotNull()
        assertThat(out!!.size).isEqualTo(12)
    }

    // ---- a known knee angle flows to the correct slot -------------------------------------------

    @Test
    fun deepSquat_leftKneeAngleSlotIsAbout90() {
        val out = extractor.extract(deepSquat(leftKnee = 90f, rightKnee = 90f))!!
        // slot 0 == left_knee_angle.
        assertThat(out[0]).isWithin(1f).of(90f)
        // slot 1 == right_knee_angle.
        assertThat(out[1]).isWithin(1f).of(90f)
    }

    // ---- low confidence -> null (model can't run; app defers to rules) --------------------------

    @Test
    fun lowConfidenceRequiredJoint_returnsNull() {
        // Drop LEFT_KNEE visibility well below the 0.55 gate; everything else fully visible.
        val frame = deepSquat()
        val degraded = frame.copy(
            landmarks = frame.landmarks.mapValues { (name, lm) ->
                if (name == LEFT_KNEE) lm.copy(visibility = 0.3f) else lm
            },
        )
        assertThat(extractor.extract(degraded)).isNull()
    }

    @Test
    fun missingRequiredJoint_returnsNull() {
        val frame = deepSquat()
        val withoutAnkle = frame.copy(landmarks = frame.landmarks - LEFT_ANKLE)
        assertThat(extractor.extract(withoutAnkle)).isNull()
    }

    // ---- symmetry_score --------------------------------------------------------------------------

    @Test
    fun asymmetricPose_symmetryScoreBelow1AndLowerThanSymmetric() {
        val symmetric = extractor.extract(deepSquat(leftKnee = 90f, rightKnee = 90f))!!
        val asymmetric = extractor.extract(deepSquat(leftKnee = 90f, rightKnee = 140f))!!
        val symIdx = extractor.featureNames().indexOf("symmetry_score")

        assertThat(asymmetric[symIdx]).isLessThan(1f)
        assertThat(asymmetric[symIdx]).isLessThan(symmetric[symIdx])
        // Symmetric pose: L/R identical -> score == 1 (perfectly symmetric).
        assertThat(symmetric[symIdx]).isWithin(1e-4f).of(1f)
    }

    @Test
    fun symmetryScore_isClampedIntoUnitInterval() {
        val out = extractor.extract(deepSquat(leftKnee = 70f, rightKnee = 170f))!!
        val symIdx = extractor.featureNames().indexOf("symmetry_score")
        assertThat(out[symIdx]).isAtLeast(0f)
        assertThat(out[symIdx]).isAtMost(1f)
    }

    // ---- torso_lean: upright (~0) < leaning -----------------------------------------------------

    @Test
    fun torsoLean_uprightIsNearZeroAndLessThanLeaning() {
        val leanIdx = extractor.featureNames().indexOf("torso_lean")
        // Upright: shoulder center directly above hip center -> lean ~ 0°.
        val upright = extractor.extract(deepSquat(shoulderDx = 0f))!![leanIdx]
        // Leaning: shoulder center pushed forward in x -> lean clearly > 0°.
        val leaning = extractor.extract(deepSquat(shoulderDx = 0.6f))!![leanIdx]

        assertThat(upright).isWithin(1f).of(0f)
        assertThat(leaning).isGreaterThan(upright)
        assertThat(leaning).isGreaterThan(10f)
    }

    // ---- spine_angle: finite, and straighter for an upright pose --------------------------------

    @Test
    fun spineAngle_isFiniteAndStraighterWhenUpright() {
        val spineIdx = extractor.featureNames().indexOf("spine_angle")
        val upright = extractor.extract(deepSquat(shoulderDx = 0f))!![spineIdx]
        val leaning = extractor.extract(deepSquat(shoulderDx = 0.6f))!![spineIdx]

        assertThat(upright).isFinite()
        assertThat(leaning).isFinite()
        // shoulderCenter-hipCenter-kneeCenter: pushing the shoulder forward bends this angle away
        // from straight (180°), so upright is the larger (straighter) angle.
        assertThat(upright).isGreaterThan(leaning)
    }

    // ---- hip_depth: monotonic with how low the hips sit relative to the knees --------------------

    @Test
    fun hipDepth_isFiniteAndMonotonicWithDepth() {
        val depthIdx = extractor.featureNames().indexOf("hip_depth")
        // hipCenterY closer to (or below) kneeCenterY = deeper squat.
        val shallow = extractor.extract(deepSquat(hipY = 2.0f, kneeY = 1.0f))!![depthIdx]
        val deep = extractor.extract(deepSquat(hipY = 1.1f, kneeY = 1.0f))!![depthIdx]

        assertThat(shallow).isFinite()
        assertThat(deep).isFinite()
        // hip_depth = hipCenter.y - kneeCenter.y, so a deeper squat (smaller gap) is the smaller value.
        assertThat(deep).isLessThan(shallow)
    }

    // ---- knee_lateral: signed horizontal knee-over-ankle offset ---------------------------------

    @Test
    fun kneeLateral_isSignedKneeMinusAnkleX() {
        val leftIdx = extractor.featureNames().indexOf("left_knee_lateral")
        // Build a frame where the left knee is +0.20 in x relative to the left ankle.
        val frame = deepSquat()
        val knee = frame.point(LEFT_KNEE)!!
        val shifted = frame.copy(
            landmarks = frame.landmarks.toMutableMap().also { m ->
                val ankleLm = m.getValue(LEFT_ANKLE)
                m[LEFT_ANKLE] = ankleLm.copy(x = knee.x - 0.20f)
            },
        )
        val out = extractor.extract(shifted)!!
        assertThat(out[leftIdx]).isWithin(1e-4f).of(0.20f)
    }

    // ---- fixture --------------------------------------------------------------------------------

    /**
     * A fully-visible synthetic squat frame.
     *
     * Coordinates are already-normalized (as [extract] assumes). The body is mirrored L/R about
     * x = 0 by default so symmetric paths are exercised; [leftKnee]/[rightKnee] independently set
     * each side's hip-knee-ankle angle via [SyntheticPose.pointForAngle]. [shoulderDx] offsets the
     * shoulder center in +x to create torso lean; [hipY]/[kneeY] control squat depth.
     */
    private fun deepSquat(
        leftKnee: Float = 90f,
        rightKnee: Float = 90f,
        shoulderDx: Float = 0f,
        hipY: Float = 2.0f,
        kneeY: Float = 1.0f,
    ): PoseFrame {
        val dxHip = 0.5f         // half hip width
        val dxShoulder = 0.5f    // half shoulder width

        val leftHip = Point3(-dxHip, hipY, 0f)
        val rightHip = Point3(dxHip, hipY, 0f)
        val leftKneePt = Point3(-dxHip, kneeY, 0f)
        val rightKneePt = Point3(dxHip, kneeY, 0f)

        // Ankles placed so hip-knee-ankle equals the requested per-side knee angle.
        val leftAnkle = SyntheticPose.pointForAngle(a = leftHip, b = leftKneePt, degrees = leftKnee)
        val rightAnkle = SyntheticPose.pointForAngle(a = rightHip, b = rightKneePt, degrees = rightKnee)

        // Shoulders above the hips (optionally pushed forward by shoulderDx for torso lean).
        val shoulderY = hipY + 1.0f
        val leftShoulder = Point3(-dxShoulder + shoulderDx, shoulderY, 0f)
        val rightShoulder = Point3(dxShoulder + shoulderDx, shoulderY, 0f)

        // Feet pointing forward (+x) from each ankle so the ankle angle (knee-ankle-foot) is defined.
        val leftFoot = Point3(leftAnkle.x + 0.4f, leftAnkle.y, 0f)
        val rightFoot = Point3(rightAnkle.x + 0.4f, rightAnkle.y, 0f)

        return SyntheticPose.frame(
            timestampMs = 0L,
            LEFT_SHOULDER to leftShoulder, RIGHT_SHOULDER to rightShoulder,
            LEFT_HIP to leftHip, RIGHT_HIP to rightHip,
            LEFT_KNEE to leftKneePt, RIGHT_KNEE to rightKneePt,
            LEFT_ANKLE to leftAnkle, RIGHT_ANKLE to rightAnkle,
            LEFT_FOOT_INDEX to leftFoot, RIGHT_FOOT_INDEX to rightFoot,
        )
    }
}
