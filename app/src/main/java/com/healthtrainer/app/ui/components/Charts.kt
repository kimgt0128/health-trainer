package com.healthtrainer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type

/**
 * Canvas-drawn report charts (design-system §4) — no chart library, just `drawLine`/`drawCircle`.
 * All inputs are already-derived `:core` numbers (per-rep `overall`/axis scores in 0..100). Honesty:
 * a single-point series (e.g. a plank set's one hold) draws a single dot, never a faked trend.
 */

/** Fixed 0..100 score domain so every chart shares one vertical scale (scores are honest 0..100). */
private const val SCORE_MIN = 0f
private const val SCORE_MAX = 100f

private fun y(score: Int, h: Float, top: Float, bottom: Float): Float {
    val t = ((score - SCORE_MIN) / (SCORE_MAX - SCORE_MIN)).coerceIn(0f, 1f)
    // higher score -> nearer the top
    return bottom - t * (bottom - top)
}

/**
 * §4 Sparkline — per-rep `overall` polyline for one set's card. A faint baseline (lineStrong, 1.2px)
 * plus the ink stroke (2.4px). One point -> a single dot; empty -> nothing.
 *
 * @param values each rep's `overall` in order (`SetScore.reps.map { it.overall }`).
 */
@Composable
fun Sparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    height: Dp = 28.dp,
) {
    Canvas(modifier = modifier.size(width, height)) {
        val top = 2f
        val bottom = size.height - 2f
        val h = size.height
        // baseline at the midpoint of the score band
        drawLine(
            color = Hue.lineStrong,
            start = Offset(0f, (top + bottom) / 2f),
            end = Offset(size.width, (top + bottom) / 2f),
            strokeWidth = 1.2f,
        )
        if (values.isEmpty()) return@Canvas
        if (values.size == 1) {
            drawCircle(color = Hue.ink, radius = 2.4f, center = Offset(size.width / 2f, y(values[0], h, top, bottom)))
            return@Canvas
        }
        val step = size.width / (values.size - 1)
        var prev = Offset(0f, y(values[0], h, top, bottom))
        for (i in 1 until values.size) {
            val cur = Offset(i * step, y(values[i], h, top, bottom))
            drawLine(color = Hue.ink, start = prev, end = cur, strokeWidth = 2.4f, cap = StrokeCap.Round)
            prev = cur
        }
    }
}

/**
 * §4 MetricBar — a left axis label (72dp muted) + a rounded track (soft2, 6dp) with an `ink` fill to
 * the score%, and the numeric value on the right (strong). For one [com.healthtrainer.core.scoring.AxisScore].
 *
 * @param label axis label (via FeedbackText.axisLabel).
 * @param score 0..100.
 * @param emphasized when the axis `failed` — drawn slightly heavier (NO hue change, §0.1).
 */
@Composable
fun MetricBar(label: String, score: Int, emphasized: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TokenText(
            text = label,
            spec = if (emphasized) Type.labelStrong else Type.label,
            color = if (emphasized) Hue.ink2 else Hue.muted,
            maxLines = 1,
            modifier = Modifier.width(Dimens.metricLabelWidth),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.barTrack)
                .padding(end = Dimens.gapSmall),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(Dimens.barTrack)) {
                val r = size.height / 2f
                drawRoundRect(
                    color = Hue.soft2,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
                val frac = (score.coerceIn(0, 100) / 100f)
                if (frac > 0f) {
                    drawRoundRect(
                        color = Hue.ink,
                        size = androidx.compose.ui.geometry.Size(size.width * frac, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                }
            }
        }
        TokenText(score.toString(), Type.labelStrong, Hue.ink, maxLines = 1)
    }
}

/**
 * §4 TrendChart — a per-rep axis-score line (ink 3.5px) with an optional mean line (muted 2.4px,
 * opacity .48), a light grid (line 1px) and 11sp muted end labels. Notable reps (the indices in
 * [highlight]) get a white-filled dot with an ink stroke. One point -> a single dot.
 *
 * @param values per-rep axis score in order.
 * @param mean   optional secondary reference (session/set mean for this axis); null hides the line.
 * @param highlight indices into [values] to mark (e.g. invalid reps).
 */
@Composable
fun TrendChart(
    values: List<Int>,
    mean: Int?,
    modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(),
    height: Dp = 132.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val top = 6f
            val bottom = size.height - 6f
            val h = size.height
            // grid: three horizontal rules (top / mid / bottom of the band)
            listOf(top, (top + bottom) / 2f, bottom).forEach { gy ->
                drawLine(Hue.line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            // mean reference line
            mean?.let { m ->
                val my = y(m, h, top, bottom)
                drawLine(
                    color = Hue.muted.copy(alpha = 0.48f),
                    start = Offset(0f, my),
                    end = Offset(size.width, my),
                    strokeWidth = 2.4f,
                )
            }
            if (values.isEmpty()) return@Canvas
            if (values.size == 1) {
                val c = Offset(size.width / 2f, y(values[0], h, top, bottom))
                drawCircle(Hue.surface, radius = 5f, center = c)
                drawCircle(Hue.ink, radius = 5f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f))
                return@Canvas
            }
            val step = size.width / (values.size - 1)
            var prev = Offset(0f, y(values[0], h, top, bottom))
            for (i in 1 until values.size) {
                val cur = Offset(i * step, y(values[i], h, top, bottom))
                drawLine(Hue.ink, prev, cur, strokeWidth = 3.5f, cap = StrokeCap.Round)
                prev = cur
            }
            // highlight notable reps with white-fill / ink-stroke dots
            highlight.filter { it in values.indices }.forEach { i ->
                val c = Offset(i * step, y(values[i], h, top, bottom))
                drawCircle(Hue.surface, radius = 5f, center = c)
                drawCircle(Hue.ink, radius = 5f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TokenText("1회", Type.axisTick, Hue.muted, maxLines = 1)
            if (values.size > 1) {
                TokenText("${values.size}회", Type.axisTick, Hue.muted, maxLines = 1)
            }
        }
    }
}

// ---- Previews ----------------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ChartsPreview() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimens.gapLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.gapLarge),
    ) {
        Sparkline(values = listOf(70, 82, 64, 90, 88))
        MetricBar(label = "깊이", score = 76, emphasized = true)
        MetricBar(label = "상체", score = 91, emphasized = false)
        TrendChart(values = listOf(72, 80, 58, 88, 84, 91), mean = 79, highlight = setOf(2))
    }
}
