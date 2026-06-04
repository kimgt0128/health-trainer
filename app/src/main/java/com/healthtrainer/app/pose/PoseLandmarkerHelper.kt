package com.healthtrainer.app.pose

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Thin wrapper around MediaPipe's [PoseLandmarker] in **LIVE_STREAM** mode.
 *
 * Owns the landmarker lifecycle (create from the bundled `pose_landmarker_lite.task` asset, run
 * [detectAsync] per camera frame, [close] on teardown) and routes the asynchronous result/error back
 * through [onResult] / [onError]. Conversion of the result to a `:core` [PoseFrame] is NOT done here
 * — that is [MediaPipeAdapter]'s job (single boundary). This class stays Android/MediaPipe-only.
 *
 * In LIVE_STREAM mode MediaPipe invokes the result listener on its own worker thread, so [onResult]
 * runs off the main thread; the [com.healthtrainer.app.MainViewModel] is responsible for hopping to
 * the main dispatcher before touching Compose state.
 *
 * NOTE (requires device): the model asset, native inference, and the async callback timing cannot be
 * exercised without the Android SDK and a device. Only the option/listener wiring is structural here.
 *
 * @param context       used to read the model asset from `assets/`.
 * @param onResult      receives `(result, timestampMs)` for each detected frame, on the MediaPipe
 *                      worker thread. `timestampMs` echoes the value passed to [detectAsync].
 * @param onError       receives any inference error.
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val onResult: (PoseLandmarkerResult, Long) -> Unit,
    private val onError: (RuntimeException) -> Unit,
) {

    private var poseLandmarker: PoseLandmarker? = null

    /**
     * Build the [PoseLandmarker] with LIVE_STREAM options. Call once before streaming frames (e.g.
     * when the camera use cases bind). Re-callable; replaces any existing instance.
     *
     * requires device: actually loading [MODEL_ASSET] and allocating the delegate is unverified here.
     */
    fun setup() {
        clearLandmarker()

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setResultListener { result, _ ->
                // LIVE_STREAM hands back the result with its monotonic input timestamp.
                onResult(result, result.timestampMs())
            }
            .setErrorListener { error -> onError(RuntimeException(error)) }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    /**
     * Submit one frame for asynchronous detection. [timestampMs] MUST be monotonically increasing
     * across calls (MediaPipe rejects out-of-order timestamps in LIVE_STREAM mode) — use the camera
     * frame timestamp. The result arrives later via [onResult].
     *
     * requires device: the actual async inference path is unverified on an SDK-less machine.
     */
    fun detectAsync(mpImage: MPImage, timestampMs: Long) {
        poseLandmarker?.detectAsync(mpImage, timestampMs)
    }

    /** Release native resources. Call from the owner's teardown (e.g. `onCleared`). */
    fun close() {
        clearLandmarker()
    }

    private fun clearLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    companion object {
        /**
         * Bundled model. PREREQUISITE ASSET (not code, not fetchable on this box): place
         * `pose_landmarker_lite.task` under `app/src/main/assets/`. A binary download from the
         * MediaPipe model garden.
         */
        const val MODEL_ASSET = "pose_landmarker_lite.task"

        // LIVE_STREAM detection/tracking confidence floors (MVP defaults; tune with real footage).
        // Note: these gate MediaPipe's own detection, distinct from :core's 0.55 per-joint
        // visibility gate used by the rule engines.
        private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }
}
