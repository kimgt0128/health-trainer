package com.healthtrainer.app.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.healthtrainer.app.pose.PoseLandmarkerHelper
import java.util.concurrent.Executors

/**
 * CameraX [PreviewView] embedded in Compose via [AndroidView], plus an [ImageAnalysis] use case that
 * forwards each frame (converted to an [MPImage]) into [PoseLandmarkerHelper.detectAsync] with the
 * frame timestamp. Bound to the composition's lifecycle.
 *
 * Data flow (mvp-4 plan §3): CameraX ImageAnalysis -> ImageProxy -> MPImage ->
 * `helper.detectAsync(mpImage, ts)`. The async result returns through the helper's `onResult`
 * (wired in [com.healthtrainer.app.MainViewModel]).
 *
 * NOTE (requires device): the camera provider, native MediaPipe inference, and the whole streaming
 * path are unverified on an SDK-less machine. The `imageProxy.toMpImage()` conversion below uses
 * CameraX's `ImageProxy.toBitmap()` + a rotation [Matrix]; the code is compile-oriented but its
 * runtime behavior (real RGBA frame delivery, rotation correctness) is unverified here.
 */
@Composable
fun CameraPreview(
    helper: PoseLandmarkerHelper,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val cameraProvider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor) { proxy -> proxy.feedToHelper(helper) } }

                // requires device: actual bind/unbind + camera selection unverified here.
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

/**
 * Convert one [ImageProxy] to an [MPImage] and submit it for detection, using the proxy's timestamp
 * (ns -> ms) as the monotonic LIVE_STREAM timestamp. ALWAYS closes the proxy (in `finally`), even on
 * failure — a leaked proxy makes MediaPipe reject subsequent frames. If conversion throws, the frame
 * is skipped rather than allowed to crash the analysis loop.
 *
 * requires device: real RGBA frame delivery + the async inference path are unverified here.
 */
private fun ImageProxy.feedToHelper(helper: PoseLandmarkerHelper) {
    try {
        val timestampMs = imageInfo.timestamp / 1_000_000L
        val mpImage = toMpImage()
        if (mpImage != null) {
            helper.detectAsync(mpImage, timestampMs)
        }
    } catch (e: RuntimeException) {
        // Skip this frame on any conversion/submit failure; don't tear down the analyzer.
        // requires device: which failures actually occur is unverified on an SDK-less machine.
    } finally {
        close()
    }
}

/**
 * Convert an RGBA_8888 [ImageProxy] to an upright [MPImage].
 *
 * The [ImageAnalysis] use case is configured with `OUTPUT_IMAGE_FORMAT_RGBA_8888`, so CameraX 1.3+/
 * 1.4.x can hand back an ARGB_8888 [Bitmap] via [ImageProxy.toBitmap]. We then rotate it upright by
 * the proxy's [androidx.camera.core.ImageInfo.getRotationDegrees] before building the [MPImage], so
 * MediaPipe sees the pose in the correct orientation.
 *
 * requires device: [ImageProxy.toBitmap] correctness and the rotation result are unverified on an
 * SDK-less machine.
 */
private fun ImageProxy.toMpImage(): MPImage? {
    val bitmap = toBitmap()
    val upright = bitmap.rotated(imageInfo.rotationDegrees)
    return BitmapImageBuilder(upright).build()
}

/**
 * Return this [Bitmap] rotated by [degrees] (clockwise). Returns the receiver unchanged when
 * [degrees] is 0 to avoid an allocation/copy for the common already-upright case.
 */
private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
