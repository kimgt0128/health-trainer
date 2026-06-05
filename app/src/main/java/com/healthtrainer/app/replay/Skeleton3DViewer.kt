package com.healthtrainer.app.replay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtrainer.app.ui.SkeletonGraphics
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseLandmark
import kotlin.math.min

/**
 * Replays a captured pose sequence as a pseudo-3D skeleton with a frame-index slider, plus a back
 * affordance (button + system-back) to return to the result screen.
 *
 * Rendering is **self-fitting and orientation-robust** so it looks right regardless of the stored
 * coordinates' magnitude or MediaPipe's world y-axis sign:
 * 1. each landmark is pre-projected with a mild oblique depth skew (`x + z*DEPTH_SKEW`) for a 3D feel;
 * 2. vertical orientation is derived from anatomy (NOSE is rendered ABOVE the ankles), so the figure
 *    is always upright even if world-y points down;
 * 3. the projected points' bounding box is uniformly scaled + centered to fill the canvas (no fixed
 *    scale that could clip or shrink the figure).
 *
 * NOTE (requires device): Canvas rendering and slider interaction are unverified on an SDK-less
 * machine; the projection/auto-fit math and topology reuse are inspectable.
 *
 * @param onBack invoked by the back button and the system back gesture (returns to the result screen).
 */
@Composable
fun Skeleton3DViewer(
    frames: List<SkeletonReplayFrame>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 결과로") }
            Text("다시보기", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }

        if (frames.isEmpty()) {
            Text("재생할 프레임이 없습니다", modifier = Modifier.padding(16.dp))
            return@Column
        }

        var index by remember { mutableIntStateOf(0) }
        val safeIndex = index.coerceIn(0, frames.lastIndex)
        val frame = frames[safeIndex]
        val failures = frame.failureCodes()

        SkeletonCanvas(
            landmarks = frame.landmarkMap(),
            isFailed = failures.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
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
        if (landmarks.isEmpty()) return@Canvas

        // Orientation: render the figure upright (NOSE above ankles) whatever the world y sign is.
        val nose = landmarks[LandmarkName.NOSE]
        val ankle = landmarks[LandmarkName.LEFT_ANKLE] ?: landmarks[LandmarkName.RIGHT_ANKLE]
        val yUp = if (nose != null && ankle != null && nose.y < ankle.y) -1f else 1f

        // Pre-project: mild oblique depth skew on x; y carries the (oriented) vertical.
        val pre: Map<LandmarkName, Offset> = landmarks.mapValues { (_, lm) ->
            Offset(lm.x + lm.z * DEPTH_SKEW, lm.y * yUp)
        }

        val xs = pre.values.map { it.x }
        val ys = pre.values.map { it.y }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-3f)
        val spanY = (maxY - minY).coerceAtLeast(1e-3f)

        val pad = size.minDimension * 0.12f
        val fit = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val originX = (size.width - spanX * fit) / 2f
        val originY = (size.height - spanY * fit) / 2f

        fun screen(o: Offset) = Offset(
            x = originX + (o.x - minX) * fit,
            y = originY + (maxY - o.y) * fit, // larger (up) y -> nearer the top
        )

        for ((a, b) in SkeletonGraphics.BONES) {
            val pa = pre[a] ?: continue
            val pb = pre[b] ?: continue
            drawLine(color = color, start = screen(pa), end = screen(pb), strokeWidth = 6f)
        }
        for (o in pre.values) {
            drawCircle(color = color, radius = 8f, center = screen(o))
        }
    }
}

/** Mild oblique depth contribution to x (kept small so a side-view replay isn't distorted). */
private const val DEPTH_SKEW = 0.25f
