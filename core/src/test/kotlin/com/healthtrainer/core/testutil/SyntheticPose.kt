package com.healthtrainer.core.testutil

import com.healthtrainer.core.geometry.Point3
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Test helpers for building synthetic, already-normalized [PoseFrame]s at *target angles*.
 *
 * Why: rule/tracker tests need frames where a joint angle is a specific value (e.g. "knee at 90°").
 * Hand-computing the landmark coordinates by trial and error (and re-verifying them numerically) is
 * repetitive and error-prone — every rule test in this project did it independently. [pointForAngle]
 * does the trig once so a test can just say "place the knee so the hip-knee-ankle angle is 90°".
 *
 * All points lie in the z = 0 plane (side-view MVP). Angles are degrees in [0, 180].
 */
object SyntheticPose {

    /**
     * Returns a point C such that the angle A-B-C (vertex at [b]) equals [degrees].
     *
     * C is placed in the z=0 plane at distance [length] from [b], along the ray B->A rotated by
     * [degrees]. Because the angle between ray B->A and ray B->C is exactly [degrees], you get
     * `AngleCalculator.angleDegrees(a, b, c) ≈ degrees` by construction (verified in SyntheticPoseTest).
     *
     * Example — a 90° knee: `val knee = ...; val ankle = pointForAngle(a = hip, b = knee, degrees = 90f)`.
     */
    fun pointForAngle(a: Point3, b: Point3, degrees: Float, length: Float = 1f): Point3 {
        val ax = a.x - b.x
        val ay = a.y - b.y
        val mag = sqrt(ax * ax + ay * ay)
        // Unit vector from B toward A (fallback to +x if A == B).
        val ux = if (mag == 0f) 1f else ax / mag
        val uy = if (mag == 0f) 0f else ay / mag
        // Rotate that direction by `degrees` (counter-clockwise) and scale by length.
        val r = Math.toRadians(degrees.toDouble())
        val cosT = cos(r).toFloat()
        val sinT = sin(r).toFloat()
        val rx = ux * cosT - uy * sinT
        val ry = ux * sinT + uy * cosT
        return Point3(b.x + rx * length, b.y + ry * length, 0f)
    }

    /** A visible [PoseLandmark] at (x, y, 0). */
    fun lm(name: LandmarkName, x: Float, y: Float, visibility: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, visibility)

    /**
     * Build a [PoseFrame] from (name -> point) pairs at [timestampMs], all with [visibility].
     * Pass a low [visibility] (< 0.55) to exercise the low-confidence path.
     */
    fun frame(
        timestampMs: Long,
        vararg points: Pair<LandmarkName, Point3>,
        visibility: Float = 0.9f,
    ): PoseFrame =
        PoseFrame(
            timestampMs,
            points.associate { (name, p) -> name to lm(name, p.x, p.y, visibility) },
        )
}
