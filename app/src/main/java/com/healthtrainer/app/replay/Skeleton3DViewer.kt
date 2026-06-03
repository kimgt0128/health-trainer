package com.healthtrainer.app.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.SkeletonGraphics
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseLandmark

/**
 * Replays a captured pose sequence as a pseudo-3D skeleton with a frame-index slider.
 *
 * The stored coordinates are normalized **world** coords (hip-centered, y-up). They are projected to
 * the Canvas with a simple oblique projection (mvp-4 plan §5):
 * ```
 * screenX = centerX + x * scale + z * depthScale
 * screenY = centerY - y * scale     // world y-up -> screen y-down
 * ```
 * A frame whose [SkeletonReplayFrame.failures] is non-empty renders its skeleton red (highlighting
 * the failed moment); otherwise green. A per-frame metric readout is omitted from the persisted frame
 * (metrics aren't stored), so the readout shows the frame's set/rep and failure labels.
 *
 * NOTE (requires device): Canvas rendering and the slider interaction are unverified on an SDK-less
 * machine. The projection math and topology reuse are inspectable.
 */
@Composable
fun Skeleton3DViewer(
    frames: List<SkeletonReplayFrame>,
    modifier: Modifier = Modifier,
) {
    if (frames.isEmpty()) {
        Text("재생할 프레임이 없습니다", modifier = modifier.padding(16.dp))
        return
    }

    var index by remember { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, frames.lastIndex)
    val frame = frames[safeIndex]
    val failures = frame.failureCodes()
    val landmarks = frame.landmarkMap()

    Column(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        SkeletonCanvas(
            landmarks = landmarks,
            isFailed = failures.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(360.dp),
        )

        Slider(
            value = safeIndex.toFloat(),
            onValueChange = { index = it.toInt() },
            valueRange = 0f..frames.lastIndex.toFloat().coerceAtLeast(0f),
            steps = (frames.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("프레임 ${safeIndex + 1} / ${frames.size}")
        Text("${frame.setNo}세트 ${frame.repNo}회차")
        if (failures.isNotEmpty()) {
            Text("실패: " + failures.joinToString("·") { it.name })
        }
    }
}

@Composable
private fun SkeletonCanvas(
    landmarks: Map<LandmarkName, PoseLandmark>,
    isFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isFailed) SkeletonGraphics.RED else SkeletonGraphics.GREEN

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        // Normalized coords are ~unit-scaled (shoulder width = 1), so amplify to fill the canvas.
        val scale = size.minDimension * 0.30f
        val depthScale = scale * 0.5f

        fun project(lm: PoseLandmark): Offset = Offset(
            x = centerX + lm.x * scale + lm.z * depthScale,
            y = centerY - lm.y * scale, // world y-up -> screen y-down
        )

        for ((a, b) in SkeletonGraphics.BONES) {
            val pa = landmarks[a] ?: continue
            val pb = landmarks[b] ?: continue
            drawLine(
                color = color,
                start = project(pa),
                end = project(pb),
                strokeWidth = 6f,
            )
        }
        for (lm in landmarks.values) {
            drawCircle(color = color, radius = 8f, center = project(lm))
        }
    }
}
