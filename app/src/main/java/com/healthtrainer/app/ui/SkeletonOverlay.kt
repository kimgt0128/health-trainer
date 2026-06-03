package com.healthtrainer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseLandmark

/**
 * Draws the live pose skeleton (joints + bones) over the camera preview.
 *
 * Coordinates are the **image-normalized** landmarks ([MainViewModel.overlayLandmarks], `x,y in
 * [0,1]`, origin top-left, y down) so they align with the on-screen image — they are scaled to the
 * Canvas size directly (no y-flip; image space already grows downward like the Canvas).
 *
 * The whole skeleton is tinted by [SkeletonGraphics.overlayColor] from the live per-frame
 * [ExerciseFeedback]: green (ok) / yellow (soft warning) / red (hard failure) / gray (low confidence).
 *
 * NOTE (requires device): rendering and alignment against a real CameraX surface are unverified on an
 * SDK-less machine; this is the structural Canvas wiring only.
 */
@Composable
fun SkeletonOverlay(
    landmarks: Map<LandmarkName, PoseLandmark>,
    feedback: ExerciseFeedback?,
    modifier: Modifier = Modifier,
) {
    val color = SkeletonGraphics.overlayColor(feedback)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun project(lm: PoseLandmark): Offset = Offset(x = lm.x * w, y = lm.y * h)

        // Bones first, joints on top.
        for ((a, b) in SkeletonGraphics.BONES) {
            val pa = landmarks[a] ?: continue
            val pb = landmarks[b] ?: continue
            drawLine(
                color = color,
                start = project(pa),
                end = project(pb),
                strokeWidth = BONE_STROKE,
            )
        }

        for (lm in landmarks.values) {
            drawCircle(
                color = color,
                radius = JOINT_RADIUS,
                center = project(lm),
            )
        }
    }
}

private const val BONE_STROKE = 6f
private const val JOINT_RADIUS = 8f

/** Exposed for callers that want the same swatch without recomputing (kept trivial). */
internal fun overlayColorOf(feedback: ExerciseFeedback?): Color = SkeletonGraphics.overlayColor(feedback)
