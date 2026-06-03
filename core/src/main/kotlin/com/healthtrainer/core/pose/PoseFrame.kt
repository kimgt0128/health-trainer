package com.healthtrainer.core.pose

import com.healthtrainer.core.geometry.Point3

/**
 * One frame of detected pose: a capture [timestampMs] and the landmarks present in that frame,
 * keyed by [LandmarkName]. A landmark may be absent (occluded / not detected).
 */
data class PoseFrame(
    val timestampMs: Long,
    val landmarks: Map<LandmarkName, PoseLandmark>,
) {
    /**
     * The position of [name] as a geometry [Point3], or `null` if the landmark isn't present.
     * This is the bridge rule engines and the normalizer use to feed pose data into [Geometry]
     * and [com.healthtrainer.core.geometry.AngleCalculator].
     */
    fun point(name: LandmarkName): Point3? =
        landmarks[name]?.let { Point3(it.x, it.y, it.z) }
}
