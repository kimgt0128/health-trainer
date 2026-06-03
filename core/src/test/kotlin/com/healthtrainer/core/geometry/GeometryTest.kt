package com.healthtrainer.core.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the pure vector helpers ([Geometry.distance], [Geometry.midpoint]) used by the
 * normalizer and later rule engines. Floating-point comparisons always use [assertThat]+isWithin
 * per the health-trainer-conventions skill.
 */
class GeometryTest {

    @Test
    fun distance_threeFourFive_returnsFive() {
        val a = Point3(0f, 0f, 0f)
        val b = Point3(3f, 4f, 0f)
        assertThat(Geometry.distance(a, b)).isWithin(1e-4f).of(5.0f)
    }

    @Test
    fun distance_alongZ_returnsAxisLength() {
        val a = Point3(1f, 1f, -2f)
        val b = Point3(1f, 1f, 3f)
        assertThat(Geometry.distance(a, b)).isWithin(1e-4f).of(5.0f)
    }

    @Test
    fun distance_samePoint_returnsZero() {
        val a = Point3(7f, -3f, 2f)
        assertThat(Geometry.distance(a, a)).isWithin(1e-4f).of(0.0f)
    }

    @Test
    fun distance_isSymmetric() {
        val a = Point3(-1f, 2f, 0.5f)
        val b = Point3(4f, -2f, 3f)
        assertThat(Geometry.distance(a, b)).isWithin(1e-4f).of(Geometry.distance(b, a))
    }

    @Test
    fun midpoint_symmetricAboutOrigin_returnsOrigin() {
        val a = Point3(2f, -4f, 6f)
        val b = Point3(-2f, 4f, -6f)
        val mid = Geometry.midpoint(a, b)
        assertThat(mid.x).isWithin(1e-4f).of(0f)
        assertThat(mid.y).isWithin(1e-4f).of(0f)
        assertThat(mid.z).isWithin(1e-4f).of(0f)
    }

    @Test
    fun midpoint_isComponentWiseAverage() {
        val a = Point3(1f, 2f, 3f)
        val b = Point3(3f, 6f, 11f)
        val mid = Geometry.midpoint(a, b)
        assertThat(mid.x).isWithin(1e-4f).of(2f)
        assertThat(mid.y).isWithin(1e-4f).of(4f)
        assertThat(mid.z).isWithin(1e-4f).of(7f)
    }
}
