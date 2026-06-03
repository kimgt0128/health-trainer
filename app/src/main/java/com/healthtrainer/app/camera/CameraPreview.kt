package com.healthtrainer.app.camera

import android.graphics.Bitmap
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
 * NOTE (requires device): the camera provider, the [ImageProxy] -> [MPImage] conversion, frame
 * orientation, and the whole streaming path are unverified on an SDK-less machine. The
 * `imageProxy.toMpImage()` conversion below is a skeleton — a production build must handle the real
 * `ImageProxy` format/rotation. Marked accordingly.
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
 * (ns -> ms) as the monotonic LIVE_STREAM timestamp. Always closes the proxy.
 *
 * requires device: the [ImageProxy] -> [Bitmap] conversion below is a placeholder. A real
 * implementation must convert the RGBA_8888 buffer (and apply rotation) correctly; this skeleton
 * only establishes the call shape into MediaPipe.
 */
private fun ImageProxy.feedToHelper(helper: PoseLandmarkerHelper) {
    try {
        val timestampMs = imageInfo.timestamp / 1_000_000L
        val mpImage = toMpImage()
        if (mpImage != null) {
            helper.detectAsync(mpImage, timestampMs)
        }
    } finally {
        close()
    }
}

/**
 * Skeleton conversion of an RGBA_8888 [ImageProxy] to an [MPImage]. requires device: not a
 * production-correct buffer copy/rotation — establishes the [BitmapImageBuilder] -> [MPImage] shape.
 */
private fun ImageProxy.toMpImage(): MPImage? {
    val plane = planes.firstOrNull() ?: return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(plane.buffer)
    return BitmapImageBuilder(bitmap).build()
}
