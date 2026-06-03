package com.healthtrainer.core.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [AngleCalculator.angleDegrees] — the angle at vertex b between rays b->a and b->c,
 * in degrees [0,180]. Angle tolerance is 0.5f per the health-trainer-conventions skill.
 */
class AngleCalculatorTest {

    @Test
    fun rightAngle_returns90Degrees() {
        // b at origin; a along +x, c along +y -> 90 degrees.
        val a = Point3(1f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        val c = Point3(0f, 1f, 0f)
        assertThat(AngleCalculator.angleDegrees(a, b, c)).isWithin(0.5f).of(90f)
    }

    @Test
    fun straightLine_returns180Degrees() {
        // a and c on opposite sides of b -> straight angle.
        val a = Point3(-1f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        val c = Point3(1f, 0f, 0f)
        assertThat(AngleCalculator.angleDegrees(a, b, c)).isWithin(0.5f).of(180f)
    }

    @Test
    fun collinearSameDirection_returns0Degrees() {
        // a and c on the same side of b (rays overlap) -> 0 degrees.
        val a = Point3(1f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        val c = Point3(2f, 0f, 0f)
        assertThat(AngleCalculator.angleDegrees(a, b, c)).isWithin(0.5f).of(0f)
    }

    @Test
    fun obliqueRays_returnsAbout45Degrees() {
        // b->a along +x, b->c along the (1,1) diagonal -> 45 degrees.
        val a = Point3(1f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        val c = Point3(1f, 1f, 0f)
        assertThat(AngleCalculator.angleDegrees(a, b, c)).isWithin(0.5f).of(45f)
    }

    @Test
    fun angleAtNonOriginVertex_returns90Degrees() {
        // Vertex b away from origin; verify it's the vertex, not the origin, that matters.
        val b = Point3(5f, 5f, 5f)
        val a = Point3(6f, 5f, 5f) // +x from b
        val c = Point3(5f, 6f, 5f) // +y from b
        assertThat(AngleCalculator.angleDegrees(a, b, c)).isWithin(0.5f).of(90f)
    }

    @Test
    fun degenerateZeroLengthRay_returnsZeroNotNaN() {
        // a coincides with b -> ray b->a has zero length. Must be guarded (no NaN).
        val a = Point3(0f, 0f, 0f)
        val b = Point3(0f, 0f, 0f)
        val c = Point3(1f, 0f, 0f)
        val angle = AngleCalculator.angleDegrees(a, b, c)
        assertThat(angle.isNaN()).isFalse()
        assertThat(angle).isWithin(1e-4f).of(0f)
    }
}
