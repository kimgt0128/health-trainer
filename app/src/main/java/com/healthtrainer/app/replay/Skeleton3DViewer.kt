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
 * Crucially, that orientation + bounding box are computed **once over every frame** (a stable
 * [ReplayProjection]) rather than per frame: a per-frame box would re-normalize each pose to fill
 * the canvas, cancelling out the very motion (e.g. squat depth) the replay exists to show. With one
 * shared projection the scale/center stay fixed and the body visibly rises and sinks while scrubbing.
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

        // One projection for the whole sequence -> fixed scale/center so motion stays visible.
        val projection = remember(frames) { computeReplayProjection(frames) }

        var index by remember { mutableIntStateOf(0) }
        val safeIndex = index.coerceIn(0, frames.lastIndex)
        val frame = frames[safeIndex]
        val failures = frame.failureCodes()

        SkeletonCanvas(
            landmarks = frame.landmarkMap(),
            isFailed = failures.isNotEmpty(),
            projection = projection,
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

/**
 * The projection shared by every replay frame: the anatomical vertical sign plus the bounding box of
 * all pre-projected landmarks across the whole sequence. Holding these fixed (rather than recomputing
 * per frame) is what keeps the skeleton's scale and center stable while scrubbing.
 */
private data class ReplayProjection(
    val yUp: Float,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
) {
    val spanX: Float get() = (maxX - minX).coerceAtLeast(1e-3f)
    val spanY: Float get() = (maxY - minY).coerceAtLeast(1e-3f)

    /** Pre-project a landmark into the oblique 2D space (mild depth skew on x, oriented vertical on y). */
    fun pre(lm: PoseLandmark): Offset = Offset(lm.x + lm.z * DEPTH_SKEW, lm.y * yUp)
}

/**
 * Derive the [ReplayProjection] from every frame: the vertical sign from average NOSE-vs-ankle height
 * (robust to a few missing landmarks) and the bounding box from all pre-projected points.
 */
private fun computeReplayProjection(frames: List<SkeletonReplayFrame>): ReplayProjection {
    var noseSum = 0f
    var noseN = 0
    var ankleSum = 0f
    var ankleN = 0
    for (f in frames) {
        val lms = f.landmarkMap()
        lms[LandmarkName.NOSE]?.let { noseSum += it.y; noseN++ }
        (lms[LandmarkName.LEFT_ANKLE] ?: lms[LandmarkName.RIGHT_ANKLE])?.let { ankleSum += it.y; ankleN++ }
    }
    val yUp = if (noseN > 0 && ankleN > 0 && noseSum / noseN < ankleSum / ankleN) -1f else 1f

    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var any = false
    for (f in frames) {
        for (lm in f.landmarkMap().values) {
            val px = lm.x + lm.z * DEPTH_SKEW
            val py = lm.y * yUp
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
            any = true
        }
    }
    if (!any) {
        return ReplayProjection(yUp, 0f, 1f, 0f, 1f)
    }
    return ReplayProjection(yUp, minX, maxX, minY, maxY)
}

@Composable
private fun SkeletonCanvas(
    landmarks: Map<LandmarkName, PoseLandmark>,
    isFailed: Boolean,
    projection: ReplayProjection,
    modifier: Modifier = Modifier,
) {
    val color = if (isFailed) SkeletonGraphics.RED else SkeletonGraphics.GREEN

    Canvas(modifier = modifier) {
        if (landmarks.isEmpty()) return@Canvas

        // Fixed scale/center from the sequence-wide bounding box (same for every frame).
        val pad = size.minDimension * 0.12f
        val fit = min(
            (size.width - 2 * pad) / projection.spanX,
            (size.height - 2 * pad) / projection.spanY,
        )
        val originX = (size.width - projection.spanX * fit) / 2f
        val originY = (size.height - projection.spanY * fit) / 2f

        fun screen(lm: PoseLandmark): Offset {
            val o = projection.pre(lm)
            return Offset(
                x = originX + (o.x - projection.minX) * fit,
                y = originY + (projection.maxY - o.y) * fit, // larger (up) y -> nearer the top
            )
        }

        for ((a, b) in SkeletonGraphics.BONES) {
            val pa = landmarks[a] ?: continue
            val pb = landmarks[b] ?: continue
            drawLine(color = color, start = screen(pa), end = screen(pb), strokeWidth = 6f)
        }
        for (lm in landmarks.values) {
            drawCircle(color = color, radius = 8f, center = screen(lm))
        }
    }
}

/** Mild oblique depth contribution to x (kept small so a side-view replay isn't distorted). */
private const val DEPTH_SKEW = 0.25f
