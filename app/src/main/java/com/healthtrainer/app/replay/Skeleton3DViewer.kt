package com.healthtrainer.app.replay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.FeedbackText
import com.healthtrainer.app.ui.components.CoachNote
import com.healthtrainer.app.ui.components.GhostButton
import com.healthtrainer.app.ui.components.IssueChip
import com.healthtrainer.app.ui.components.MiniStatRow
import com.healthtrainer.app.ui.components.PrimaryButton
import com.healthtrainer.app.ui.components.ReplayCanvas
import com.healthtrainer.app.ui.components.TokenText
import com.healthtrainer.app.ui.SkeletonGraphics
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseLandmark
import com.healthtrainer.core.scoring.RepScore
import kotlin.math.min

/**
 * Replays a captured pose sequence as a pseudo-3D skeleton inside the dark
 * [com.healthtrainer.app.ui.components.ReplayCanvas] chrome (design-system §5 Replay): eyebrow + h2 +
 * a ← ghost, then the canvas (gradient + tracking pill) wrapping the skeleton + frame slider, then
 * mini-stats / issue chips bound to the replayed rep's REAL data, a tip [CoachNote], and a primary
 * "전체 요약으로".
 *
 * Honesty (§0.3): the mini-stats are the rep's real `durationMs` + per-axis `AxisScore.score`s, and
 * the chips are the rep's real `failures` — all from the passed-in [repScore]. When no [repScore] is
 * supplied (e.g. a graceful-fallback session), only the frame-derived locator/highlight is shown; no
 * numbers are fabricated.
 *
 * The skeleton rendering is **unchanged** and stays self-fitting + orientation-robust:
 * 1. each landmark is pre-projected with a mild oblique depth skew (`x + z*DEPTH_SKEW`);
 * 2. vertical orientation is derived from anatomy (NOSE above the ankles), so the figure is upright;
 * 3. the projected points' bounding box is scaled + centered ONCE over every frame (a stable
 *    [ReplayProjection]) — a per-frame box would cancel the very motion the replay exists to show.
 *
 * NOTE (requires device): Canvas rendering and slider interaction are unverified on an SDK-less
 * machine; the projection/auto-fit math and the data binding are inspectable.
 *
 * @param frames    the captured replay frames.
 * @param repScore  the scored rep being replayed (for real duration/axis/failures); null hides them.
 * @param onBack    back to the set detail (← ghost + system back).
 * @param onSummary jump back to the result summary (the primary "전체 요약으로").
 */
@Composable
fun Skeleton3DViewer(
    frames: List<SkeletonReplayFrame>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repScore: RepScore? = null,
    onSummary: () -> Unit = onBack,
) {
    BackHandler(onBack = onBack)

    val scroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.screenPad)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
            Spacer(Modifier.height(Dimens.gapLarge))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                GhostButton(glyph = "←", onClick = onBack)
                Column(modifier = Modifier.padding(start = Dimens.gap)) {
                    TokenText("자세 다시보기", Type.eyebrow, Hue.muted)
                    TokenText("회차 리플레이", Type.h2, Hue.ink)
                }
            }
            Spacer(Modifier.height(Dimens.gapLarge))

            if (frames.isEmpty()) {
                TokenText("재생할 프레임이 없습니다", Type.body, Hue.muted)
            } else {
                // One projection for the whole sequence -> fixed scale/center so motion stays visible.
                val projection = remember(frames) { computeReplayProjection(frames) }

                var index by remember { mutableIntStateOf(0) }
                val safeIndex = index.coerceIn(0, frames.lastIndex)
                val frame = frames[safeIndex]
                val failures = frame.failureCodes()

                ReplayCanvas(
                    pill = "${frame.setNo}세트 ${frame.repNo}회차",
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                ) {
                    SkeletonCanvas(
                        landmarks = frame.landmarkMap(),
                        isFailed = failures.isNotEmpty(),
                        projection = projection,
                        modifier = Modifier.fillMaxSize().padding(Dimens.gapLarge),
                    )
                }

                Spacer(Modifier.height(Dimens.gap))
                Slider(
                    value = safeIndex.toFloat(),
                    onValueChange = { index = it.toInt() },
                    valueRange = 0f..frames.lastIndex.toFloat().coerceAtLeast(0f),
                    steps = (frames.size - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = Hue.ink,
                        activeTrackColor = Hue.ink,
                        inactiveTrackColor = Hue.soft2,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                TokenText("프레임 ${safeIndex + 1} / ${frames.size}", Type.label, Hue.muted)

                // Real per-rep stats from the scored rep (duration + axis scores), if provided.
                repScore?.let { rep ->
                    Spacer(Modifier.height(Dimens.gapLarge))
                    val tiles = buildList {
                        add(formatDuration(rep.durationMs) to "시간")
                        rep.axes.forEach { axis -> add(axis.score.toString() to FeedbackText.axisLabel(axis.key)) }
                    }
                    MiniStatRow(tiles = tiles)

                    val realFailures = rep.failures.filter { it != FeedbackCode.LOW_CONFIDENCE }
                    if (realFailures.isNotEmpty()) {
                        Spacer(Modifier.height(Dimens.gapLarge))
                        TokenText("이 회차에서 보인 점", Type.section, Hue.muted)
                        Spacer(Modifier.height(Dimens.gap))
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                            realFailures.forEach { code ->
                                IssueChip(
                                    reason = FeedbackText.label(code),
                                    tag = FeedbackText.issueTag(code),
                                    strong = FeedbackText.severity(code) == FeedbackText.Severity.HARD,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Dimens.gapLarge))
                    val tip = FeedbackText.replayTip(rep.failures)
                    CoachNote(title = tip.title, bullets = tip.bullets)
                }
            }

            Spacer(Modifier.height(Dimens.gapLarge))
        }

        PrimaryButton(
            text = "전체 요약으로",
            onClick = onSummary,
            modifier = Modifier.padding(bottom = Dimens.gapLarge),
        )
    }
}

/** "1.4초" style duration for a mini-stat (rep durations are short). */
private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000.0
    return "%.1f초".format(seconds)
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
