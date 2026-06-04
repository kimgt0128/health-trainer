package com.healthtrainer.core.geometry

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Computes the interior angle at a joint, the primary primitive the exercise rule engines use
 * (knee angle, elbow angle, torso angle, ...).
 *
 * Pure :core — no Android dependency.
 */
object AngleCalculator {

    /**
     * Angle at vertex [b] between the rays b->[a] and b->[c], in degrees within `[0, 180]`.
     *
     * Uses the dot-product / acos form. The cosine is coerced into `[-1f, 1f]` to absorb
     * floating-point error before [acos] (which would otherwise yield NaN). If either ray has
     * (near) zero length the angle is undefined, so we return `0f` rather than NaN.
     */
    fun angleDegrees(a: Point3, b: Point3, c: Point3): Float {
        val baX = a.x - b.x
        val baY = a.y - b.y
        val baZ = a.z - b.z

        val bcX = c.x - b.x
        val bcY = c.y - b.y
        val bcZ = c.z - b.z

        val baLen = sqrt(baX * baX + baY * baY + baZ * baZ)
        val bcLen = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)

        // Zero-length ray -> angle undefined. Guard against division by zero / NaN.
        if (baLen < EPSILON || bcLen < EPSILON) return 0f

        val dot = baX * bcX + baY * bcY + baZ * bcZ
        val cosine = (dot / (baLen * bcLen)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    /** Below this length a ray is treated as degenerate (zero-length). */
    private const val EPSILON = 1e-6f
}
