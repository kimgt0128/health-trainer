package com.healthtrainer.core.features

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
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
import com.healthtrainer.core.pose.PoseLandmark
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Test

class PlankFeatureExtractorTest {

    private val extractor = PlankFeatureExtractor()

    @Test
    fun featureNames_areThe8ContractFeaturesInOrder() {
        assertThat(extractor.featureNames()).containsExactly(
            "body_line_angle",
            "knee_line_angle",
            "hip_perp_offset_signed",
            "hip_perp_offset_abs",
            "hip_axial_ratio",
            "knee_perp_offset_signed",
            "knee_axial_ratio",
            "required_visible_ratio",
        ).inOrder()
    }

    @Test
    fun exerciseType_isPlank() {
        assertThat(extractor.exerciseType).isEqualTo(ExerciseType.PLANK)
    }

    @Test
    fun extract_returnsVectorOfSize8() {
        val out = extractor.extract(plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0f, 0f), ankle = ANKLE))

        assertThat(out).isNotNull()
        assertThat(out!!.size).isEqualTo(8)
    }

    @Test
    fun horizontalSaggingHip_isPositive() {
        val out = extractor.extract(plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE))!!

        assertThat(out[idx("hip_perp_offset_signed")]).isGreaterThan(0f)
    }

    @Test
    fun horizontalPikedHip_isNegative() {
        val out = extractor.extract(plankFrame(shoulder = SHOULDER, hip = Point3(1f, -0.3f, 0f), ankle = ANKLE))!!

        assertThat(out[idx("hip_perp_offset_signed")]).isLessThan(0f)
    }

    @Test
    fun straightPlank_hasMaxAnglesAndZeroOffset() {
        val out = extractor.extract(plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0f, 0f), ankle = ANKLE))!!

        assertThat(out[idx("body_line_angle")]).isWithin(1e-3f).of(180f)
        assertThat(out[idx("knee_line_angle")]).isWithin(1e-3f).of(180f)
        assertThat(out[idx("hip_perp_offset_signed")]).isWithin(1e-6f).of(0f)
        assertThat(out[idx("hip_perp_offset_abs")]).isWithin(1e-6f).of(0f)
    }

    @Test
    fun featuresAreScaleInvariant() {
        val base = extractor.extract(plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE))!!
        val scaled = extractor.extract(
            plankFrame(
                shoulder = Point3(0f, 0f, 0f),
                hip = Point3(10f, 3f, 0f),
                ankle = Point3(20f, 0f, 0f),
            ),
        )!!

        for (name in extractor.featureNames()) {
            assertThat(scaled[idx(name)]).isWithin(1e-5f).of(base[idx(name)])
        }
    }

    @Test
    fun featuresAreRotationInvariant() {
        val theta = Math.toRadians(37.0)
        val shoulder = SHOULDER
        val hip = Point3(1f, 0.3f, 0f)
        val ankle = ANKLE
        val base = extractor.extract(plankFrame(shoulder = shoulder, hip = hip, ankle = ankle))!!
        val rotated = extractor.extract(
            plankFrame(
                shoulder = rotate(shoulder, theta),
                hip = rotate(hip, theta),
                ankle = rotate(ankle, theta),
            ),
        )!!

        for (name in listOf(
            "body_line_angle", "knee_line_angle",
            "hip_perp_offset_signed", "hip_perp_offset_abs", "hip_axial_ratio",
            "knee_perp_offset_signed", "knee_axial_ratio",
        )) {
            assertThat(rotated[idx(name)]).isWithin(1e-4f).of(base[idx(name)])
        }
    }

    @Test
    fun signSurvivesRotation() {
        val theta = Math.toRadians(50.0)
        val out = extractor.extract(
            plankFrame(
                shoulder = rotate(SHOULDER, theta),
                hip = rotate(Point3(1f, 0.3f, 0f), theta),
                ankle = rotate(ANKLE, theta),
            ),
        )!!

        assertThat(out[idx("hip_perp_offset_signed")]).isGreaterThan(0f)
    }

    /**
     * The training source is 2D (no depth). A frame with non-zero, conflicting z on every landmark
     * must produce the SAME angles (and offsets) as its z=0 projection — otherwise MediaPipe depth
     * would drift them away from the 2D values the model was trained on. Locks the z=0 projection.
     */
    @Test
    fun anglesAreComputedIn2D_ignoringDepth() {
        val flat = extractor.extract(plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE))!!
        val withDepth = extractor.extract(
            PoseFrame(
                timestampMs = 0L,
                landmarks = mapOf(
                    LEFT_SHOULDER to landmark(LEFT_SHOULDER, Point3(0f, 0f, 5f)),
                    RIGHT_SHOULDER to landmark(RIGHT_SHOULDER, Point3(0f, 0f, 5f)),
                    LEFT_HIP to landmark(LEFT_HIP, Point3(1f, 0.3f, -7f)),
                    RIGHT_HIP to landmark(RIGHT_HIP, Point3(1f, 0.3f, -7f)),
                    LEFT_KNEE to landmark(LEFT_KNEE, Point3(1.5f, 0.15f, 9f)),
                    RIGHT_KNEE to landmark(RIGHT_KNEE, Point3(1.5f, 0.15f, 9f)),
                    LEFT_ANKLE to landmark(LEFT_ANKLE, Point3(2f, 0f, 3f)),
                    RIGHT_ANKLE to landmark(RIGHT_ANKLE, Point3(2f, 0f, 3f)),
                ),
            ),
        )!!

        assertThat(withDepth[idx("body_line_angle")]).isWithin(1e-3f).of(flat[idx("body_line_angle")])
        assertThat(withDepth[idx("knee_line_angle")]).isWithin(1e-3f).of(flat[idx("knee_line_angle")])
        assertThat(withDepth[idx("hip_perp_offset_signed")]).isWithin(1e-6f).of(flat[idx("hip_perp_offset_signed")])
    }

    @Test
    fun missingOneAnkleStillExtractsWithLowerVisibleRatio() {
        val frame = plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE)
        val withoutRightAnkle = frame.copy(landmarks = frame.landmarks - RIGHT_ANKLE)

        val out = extractor.extract(withoutRightAnkle)!!

        assertThat(out[idx("required_visible_ratio")]).isWithin(1e-5f).of(7f / 8f)
        assertThat(out[idx("hip_perp_offset_signed")]).isGreaterThan(0f)
    }

    @Test
    fun missingBothAnkles_returnsNull() {
        val frame = plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE)
        val withoutAnkles = frame.copy(landmarks = frame.landmarks - LEFT_ANKLE - RIGHT_ANKLE)

        assertThat(extractor.extract(withoutAnkles)).isNull()
    }

    @Test
    fun missingBothKnees_returnsNull() {
        // The knee is a required (label-defining) joint now.
        val frame = plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE)
        val withoutKnees = frame.copy(landmarks = frame.landmarks - LEFT_KNEE - RIGHT_KNEE)

        assertThat(extractor.extract(withoutKnees)).isNull()
    }

    @Test
    fun lowConfidenceWholeRequiredPair_returnsNull() {
        val frame = plankFrame(shoulder = SHOULDER, hip = Point3(1f, 0.3f, 0f), ankle = ANKLE)
        val lowShoulders = frame.copy(
            landmarks = frame.landmarks.mapValues { (name, landmark) ->
                if (name == LEFT_SHOULDER || name == RIGHT_SHOULDER) {
                    landmark.copy(visibility = 0.2f)
                } else {
                    landmark
                }
            },
        )

        assertThat(extractor.extract(lowShoulders)).isNull()
    }

    @Test
    fun degenerateBodyAxis_returnsNull() {
        val same = Point3(0f, 0f, 0f)

        assertThat(extractor.extract(plankFrame(shoulder = same, hip = Point3(0f, 0.3f, 0f), ankle = same))).isNull()
    }

    private fun idx(name: String): Int = extractor.featureNames().indexOf(name)

    private fun rotate(point: Point3, theta: Double): Point3 {
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        return Point3(
            x = point.x * c - point.y * s,
            y = point.x * s + point.y * c,
            z = point.z,
        )
    }

    /** Knee defaults to the midpoint of hip and ankle (so it stays scale/rotation-consistent). */
    private fun plankFrame(shoulder: Point3, hip: Point3, ankle: Point3, knee: Point3? = null): PoseFrame {
        val k = knee ?: Point3((hip.x + ankle.x) / 2f, (hip.y + ankle.y) / 2f, (hip.z + ankle.z) / 2f)
        return PoseFrame(
            timestampMs = 0L,
            landmarks = mapOf(
                LEFT_SHOULDER to landmark(LEFT_SHOULDER, shoulder),
                RIGHT_SHOULDER to landmark(RIGHT_SHOULDER, shoulder),
                LEFT_HIP to landmark(LEFT_HIP, hip),
                RIGHT_HIP to landmark(RIGHT_HIP, hip),
                LEFT_KNEE to landmark(LEFT_KNEE, k),
                RIGHT_KNEE to landmark(RIGHT_KNEE, k),
                LEFT_ANKLE to landmark(LEFT_ANKLE, ankle),
                RIGHT_ANKLE to landmark(RIGHT_ANKLE, ankle),
            ),
        )
    }

    private fun landmark(name: LandmarkName, point: Point3, visibility: Float = 0.9f): PoseLandmark =
        PoseLandmark(name = name, x = point.x, y = point.y, z = point.z, visibility = visibility)

    private companion object {
        val SHOULDER = Point3(0f, 0f, 0f)
        val ANKLE = Point3(2f, 0f, 0f)
    }
}
