package com.healthtrainer.core.geometry

/**
 * A point / vector in 3D space using the pose coordinate system.
 *
 * Pure :core type — no Android or MediaPipe dependency. MediaPipe results are converted into
 * these by the :app adapter. Coordinates are [Float] to match the upstream pose model.
 */
data class Point3(val x: Float, val y: Float, val z: Float)
