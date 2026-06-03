package com.healthtrainer.core.pose

/**
 * One detected body landmark: its identity, 3D position, and detection [visibility] in `[0, 1]`.
 *
 * Pure :core type. Coordinates are in the upstream pose model's space until
 * [LandmarkNormalizer.normalize] re-expresses them hip-centered / shoulder-scaled.
 */
data class PoseLandmark(
    val name: LandmarkName,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
)
