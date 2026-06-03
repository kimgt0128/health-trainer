package com.healthtrainer.core.pose

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.geometry.Geometry
import org.junit.Test

/**
 * Tests for [LandmarkNormalizer.normalize].
 *
 * Normalization (per health-trainer-conventions):
 *   hipCenter     = midpoint(LEFT_HIP, RIGHT_HIP)
 *   shoulderWidth = distance(LEFT_SHOULDER, RIGHT_SHOULDER)
 *   normalized    = (landmark - hipCenter) / shoulderWidth
 * with visibility and timestamp preserved. Degenerate frames are returned unchanged.
 */
class LandmarkNormalizerTest {

    private fun lm(name: LandmarkName, x: Float, y: Float, z: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, z, vis)

    /**
     * A synthetic frame placed off-origin and asymmetric so the translation and scale both do
     * real work. Hips are centered at (10, 20, 30); shoulders are 4 units apart in x.
     */
    private fun syntheticFrame(): PoseFrame = PoseFrame(
        timestampMs = 123L,
        landmarks = listOf(
            lm(LandmarkName.LEFT_HIP, 8f, 20f, 30f, vis = 0.95f),
            lm(LandmarkName.RIGHT_HIP, 12f, 20f, 30f, vis = 0.85f),
            lm(LandmarkName.LEFT_SHOULDER, 8f, 24f, 30f, vis = 0.80f),
            lm(LandmarkName.RIGHT_SHOULDER, 12f, 24f, 30f, vis = 0.70f),
            lm(LandmarkName.NOSE, 10f, 28f, 31f, vis = 0.60f),
        ).associateBy { it.name },
    )

    @Test
    fun normalize_movesHipMidpointToOrigin() {
        val out = LandmarkNormalizer.normalize(syntheticFrame())
        val left = out.point(LandmarkName.LEFT_HIP)!!
        val right = out.point(LandmarkName.RIGHT_HIP)!!
        val mid = Geometry.midpoint(left, right)
        assertThat(mid.x).isWithin(1e-4f).of(0f)
        assertThat(mid.y).isWithin(1e-4f).of(0f)
        assertThat(mid.z).isWithin(1e-4f).of(0f)
    }

    @Test
    fun normalize_makesShoulderDistanceOne() {
        val out = LandmarkNormalizer.normalize(syntheticFrame())
        val ls = out.point(LandmarkName.LEFT_SHOULDER)!!
        val rs = out.point(LandmarkName.RIGHT_SHOULDER)!!
        assertThat(Geometry.distance(ls, rs)).isWithin(1e-3f).of(1.0f)
    }

    @Test
    fun normalize_preservesVisibilityAndTimestamp() {
        val out = LandmarkNormalizer.normalize(syntheticFrame())
        assertThat(out.timestampMs).isEqualTo(123L)
        assertThat(out.landmarks[LandmarkName.LEFT_HIP]!!.visibility).isWithin(1e-6f).of(0.95f)
        assertThat(out.landmarks[LandmarkName.RIGHT_SHOULDER]!!.visibility).isWithin(1e-6f).of(0.70f)
        assertThat(out.landmarks[LandmarkName.NOSE]!!.visibility).isWithin(1e-6f).of(0.60f)
    }

    @Test
    fun normalize_preservesLandmarkNamesAndCount() {
        val out = LandmarkNormalizer.normalize(syntheticFrame())
        assertThat(out.landmarks.keys).containsExactlyElementsIn(syntheticFrame().landmarks.keys)
        // Each entry's key matches its PoseLandmark.name.
        out.landmarks.forEach { (key, value) -> assertThat(value.name).isEqualTo(key) }
    }

    @Test
    fun normalize_translatesNonReferenceLandmarkCorrectly() {
        // NOSE at (10,28,31): minus hipCenter (10,20,30) = (0,8,1); /shoulderWidth (4) = (0,2,0.25).
        val out = LandmarkNormalizer.normalize(syntheticFrame())
        val nose = out.point(LandmarkName.NOSE)!!
        assertThat(nose.x).isWithin(1e-4f).of(0f)
        assertThat(nose.y).isWithin(1e-4f).of(2f)
        assertThat(nose.z).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun normalize_missingHip_returnsFrameUnchanged() {
        // Drop RIGHT_HIP -> reference set incomplete -> degenerate guard returns original.
        val degenerate = PoseFrame(
            timestampMs = 7L,
            landmarks = listOf(
                lm(LandmarkName.LEFT_HIP, 8f, 20f, 30f),
                lm(LandmarkName.LEFT_SHOULDER, 8f, 24f, 30f),
                lm(LandmarkName.RIGHT_SHOULDER, 12f, 24f, 30f),
            ).associateBy { it.name },
        )
        val out = LandmarkNormalizer.normalize(degenerate)
        assertThat(out).isSameInstanceAs(degenerate)
    }

    @Test
    fun normalize_zeroShoulderWidth_returnsFrameUnchanged() {
        // Both shoulders coincide -> shoulderWidth ~ 0 -> guard returns original (no divide-by-zero).
        val degenerate = PoseFrame(
            timestampMs = 9L,
            landmarks = listOf(
                lm(LandmarkName.LEFT_HIP, 8f, 20f, 30f),
                lm(LandmarkName.RIGHT_HIP, 12f, 20f, 30f),
                lm(LandmarkName.LEFT_SHOULDER, 10f, 24f, 30f),
                lm(LandmarkName.RIGHT_SHOULDER, 10f, 24f, 30f),
            ).associateBy { it.name },
        )
        val out = LandmarkNormalizer.normalize(degenerate)
        assertThat(out).isSameInstanceAs(degenerate)
    }
}
