package com.healthtrainer.app.pose

import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark

/**
 * The single `:app -> :core` boundary for pose data.
 *
 * MediaPipe's BlazePose topology emits **33** landmarks in a fixed index order; `:core` reasons
 * about a **17-name subset** ([LandmarkName]). This adapter maps the MediaPipe indices onto those
 * names and produces `:core` [PoseFrame] / [PoseLandmark] values. It is a pure mapper: it performs
 * NO geometry, NO normalization, and NO visibility filtering. `:core`
 * ([com.healthtrainer.core.pose.LandmarkNormalizer] + the rule engines) owns all of that and gates
 * on `visibility >= 0.55` itself.
 *
 * MediaPipe returns two parallel landmark lists per detected pose:
 * - [PoseLandmarkerResult.landmarks] — normalized **image** landmarks (`x,y in [0,1]`, origin
 *   top-left, y increases downward; `z` a relative depth). Used for the on-screen overlay so it
 *   lines up with the camera image.
 * - [PoseLandmarkerResult.worldLandmarks] — **world** landmarks in meters, hip-origin, real 3D.
 *   Used to build the rule-evaluation [PoseFrame] (angles are 3D and view-stable, which is what
 *   [com.healthtrainer.core.pose.LandmarkNormalizer] and the rules expect).
 *
 * NOTE (requires device): the MediaPipe result types and their accessor shapes are only resolvable
 * with the Android SDK + `com.google.mediapipe:tasks-vision` on the classpath; this is unverified on
 * an SDK-less machine.
 */
object MediaPipeAdapter {

    /**
     * MediaPipe BlazePose index -> `:core` [LandmarkName]. Exactly the 17 names `:core` uses.
     *
     * Indices intentionally omitted (face/hands `:core` doesn't reason about): eyes 1-6, ears 7-8,
     * mouth 9-10, and hand points 17-22.
     */
    private val INDEX_TO_NAME: Map<Int, LandmarkName> = mapOf(
        0 to LandmarkName.NOSE,
        11 to LandmarkName.LEFT_SHOULDER,
        12 to LandmarkName.RIGHT_SHOULDER,
        13 to LandmarkName.LEFT_ELBOW,
        14 to LandmarkName.RIGHT_ELBOW,
        15 to LandmarkName.LEFT_WRIST,
        16 to LandmarkName.RIGHT_WRIST,
        23 to LandmarkName.LEFT_HIP,
        24 to LandmarkName.RIGHT_HIP,
        25 to LandmarkName.LEFT_KNEE,
        26 to LandmarkName.RIGHT_KNEE,
        27 to LandmarkName.LEFT_ANKLE,
        28 to LandmarkName.RIGHT_ANKLE,
        29 to LandmarkName.LEFT_HEEL,
        30 to LandmarkName.RIGHT_HEEL,
        31 to LandmarkName.LEFT_FOOT_INDEX,
        32 to LandmarkName.RIGHT_FOOT_INDEX,
    )

    /**
     * Build the rule-evaluation [PoseFrame] from the **world** landmarks of the first detected pose
     * (the helper runs with `numPoses = 1`). Returns a frame with an empty landmark map if no pose
     * was detected — `:core` tolerates absent landmarks (rules emit `LOW_CONFIDENCE`).
     *
     * [timestampMs] is supplied by `:app` (the frame timestamp); `:core` has no clock.
     */
    fun toPoseFrame(result: PoseLandmarkerResult, timestampMs: Long): PoseFrame {
        val worldPoses = result.worldLandmarks()
        val world = worldPoses.firstOrNull().orEmpty()

        val landmarks = INDEX_TO_NAME.entries.mapNotNull { (index, name) ->
            // Defensive: index out of range (shouldn't happen with the fixed 33-point topology).
            // Omit it — an absent landmark reads as occluded to :core (point() returns null).
            val lm = world.getOrNull(index) ?: return@mapNotNull null
            name to PoseLandmark(
                name = name,
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                visibility = lm.worldVisibility(),
            )
        }.toMap()

        return PoseFrame(timestampMs = timestampMs, landmarks = landmarks)
    }

    /**
     * Build the overlay landmark map from the **image-normalized** landmarks of the first pose, keyed
     * by [LandmarkName]. `x,y in [0,1]` image-relative; the overlay scales these to the Canvas. Not
     * fed to the rules. Empty map when no pose is detected.
     */
    fun toOverlayLandmarks(result: PoseLandmarkerResult): Map<LandmarkName, PoseLandmark> {
        val imagePoses = result.landmarks()
        val image = imagePoses.firstOrNull().orEmpty()

        return INDEX_TO_NAME.entries.mapNotNull { (index, name) ->
            val lm = image.getOrNull(index) ?: return@mapNotNull null
            name to PoseLandmark(
                name = name,
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                visibility = lm.imageVisibility(),
            )
        }.toMap()
    }

    /** MediaPipe world-landmark visibility is `Optional<Float>`; absent -> treat as 0 (occluded). */
    private fun Landmark.worldVisibility(): Float = visibility().orElse(0f)

    /** MediaPipe image-landmark visibility is `Optional<Float>`; absent -> treat as 0 (occluded). */
    private fun NormalizedLandmark.imageVisibility(): Float = visibility().orElse(0f)
}
