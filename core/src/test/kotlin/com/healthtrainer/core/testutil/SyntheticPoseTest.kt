package com.healthtrainer.core.testutil

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.geometry.AngleCalculator
import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import org.junit.Test

/**
 * Verifies the [SyntheticPose] test helper itself — if a fixture builder is wrong, every test built
 * on it inherits the error, so this is the one place that pins its correctness.
 */
class SyntheticPoseTest {

    @Test
    fun pointForAngle_producesTheRequestedAngle() {
        val a = Point3(1f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        for (target in listOf(30f, 60f, 90f, 120f, 160f, 180f)) {
            val c = SyntheticPose.pointForAngle(a, b, degrees = target, length = 2f)
            assertThat(AngleCalculator.angleDegrees(a, b, c)).isWithin(0.1f).of(target)
        }
    }

    @Test
    fun pointForAngle_respectsLengthFromVertex() {
        val a = Point3(2f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        val c = SyntheticPose.pointForAngle(a, b, degrees = 90f, length = 3f)
        // Distance from the vertex equals the requested length.
        val d = kotlin.math.sqrt((c.x - b.x) * (c.x - b.x) + (c.y - b.y) * (c.y - b.y))
        assertThat(d).isWithin(1e-4f).of(3f)
    }

    @Test
    fun frame_buildsLandmarksWithTimestampAndVisibility() {
        val frame = SyntheticPose.frame(
            timestampMs = 5L,
            LandmarkName.LEFT_HIP to Point3(0f, 0f, 0f),
            LandmarkName.LEFT_KNEE to Point3(0f, -1f, 0f),
            visibility = 0.8f,
        )
        assertThat(frame.timestampMs).isEqualTo(5L)
        assertThat(frame.landmarks[LandmarkName.LEFT_KNEE]!!.visibility).isEqualTo(0.8f)
        assertThat(frame.point(LandmarkName.LEFT_HIP)).isEqualTo(Point3(0f, 0f, 0f))
    }
}
