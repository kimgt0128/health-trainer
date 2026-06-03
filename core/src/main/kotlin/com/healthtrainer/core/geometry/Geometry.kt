package com.healthtrainer.core.geometry

import kotlin.math.sqrt

/**
 * Pure 3D vector helpers used by the landmark normalizer and the exercise rule engines.
 *
 * Kept as an [object] so call sites read as [Geometry.distance] / [Geometry.midpoint].
 */
object Geometry {

    /** Euclidean (L2) distance between [a] and [b] in 3D. */
    fun distance(a: Point3, b: Point3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Component-wise average of [a] and [b]. */
    fun midpoint(a: Point3, b: Point3): Point3 =
        Point3(
            x = (a.x + b.x) / 2f,
            y = (a.y + b.y) / 2f,
            z = (a.z + b.z) / 2f,
        )
}
