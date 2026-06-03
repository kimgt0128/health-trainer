package com.healthtrainer.core.pose

import com.healthtrainer.core.geometry.Geometry
import com.healthtrainer.core.geometry.Point3

/**
 * Re-expresses a [PoseFrame] in a body-relative frame so the rule engines are invariant to where
 * the person stands and how large they appear (per the health-trainer-conventions skill):
 *
 *   hipCenter     = midpoint(LEFT_HIP, RIGHT_HIP)
 *   shoulderWidth = distance(LEFT_SHOULDER, RIGHT_SHOULDER)
 *   normalized    = (landmark - hipCenter) / shoulderWidth
 *
 * Visibility and timestamp are preserved. The transform is applied to every landmark, not just
 * the reference ones.
 */
object LandmarkNormalizer {

    /**
     * Returns a normalized copy of [frame], or [frame] **unchanged** when normalization is not
     * well-defined: any of the four reference landmarks (both hips, both shoulders) is missing, or
     * the shoulder width is degenerate (`< EPSILON`, which would divide by ~zero).
     */
    fun normalize(frame: PoseFrame): PoseFrame {
        val leftHip = frame.point(LandmarkName.LEFT_HIP)
        val rightHip = frame.point(LandmarkName.RIGHT_HIP)
        val leftShoulder = frame.point(LandmarkName.LEFT_SHOULDER)
        val rightShoulder = frame.point(LandmarkName.RIGHT_SHOULDER)

        // Degenerate guard: incomplete reference set -> can't define the body frame.
        if (leftHip == null || rightHip == null || leftShoulder == null || rightShoulder == null) {
            return frame
        }

        val shoulderWidth = Geometry.distance(leftShoulder, rightShoulder)
        // Degenerate guard: zero shoulder width -> scale undefined / divide-by-zero.
        if (shoulderWidth < EPSILON) {
            return frame
        }

        val hipCenter = Geometry.midpoint(leftHip, rightHip)

        val normalizedLandmarks = frame.landmarks.mapValues { (_, landmark) ->
            val normalized = normalizePoint(Point3(landmark.x, landmark.y, landmark.z), hipCenter, shoulderWidth)
            landmark.copy(x = normalized.x, y = normalized.y, z = normalized.z)
        }

        return frame.copy(landmarks = normalizedLandmarks)
    }

    /** `(p - hipCenter) / shoulderWidth`, component-wise. */
    private fun normalizePoint(p: Point3, hipCenter: Point3, shoulderWidth: Float): Point3 =
        Point3(
            x = (p.x - hipCenter.x) / shoulderWidth,
            y = (p.y - hipCenter.y) / shoulderWidth,
            z = (p.z - hipCenter.z) / shoulderWidth,
        )

    /** Shoulder widths below this are treated as degenerate. Matches the 1e-6 guard in the brief. */
    private const val EPSILON = 1e-6f
}
