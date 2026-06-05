package com.healthtrainer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type

/**
 * Stateless result-report primitives (design-system §4): each takes already-derived display data and
 * only draws it — no business logic, state is hoisted. Korean text breaks on word boundaries
 * ([LineBreak.Heading] ~ `word-break: keep-all`); short stat/tag labels stay nowrap.
 */

/** Apply a [Type.Spec] (size+weight) with an explicit color — keeps the call sites token-driven. */
@Composable
internal fun TokenText(
    text: String,
    spec: Type.Spec,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    keepAll: Boolean = false,
) {
    Text(
        text = text,
        color = color,
        fontSize = spec.size,
        fontWeight = spec.weight,
        maxLines = maxLines,
        textAlign = align,
        style = if (keepAll) {
            androidx.compose.ui.text.TextStyle(lineBreak = LineBreak.Heading)
        } else {
            androidx.compose.ui.text.TextStyle.Default
        },
        modifier = modifier,
    )
}

/**
 * §4 HeroScore — the big session score (76) with a one-line muted comment to its right.
 *
 * @param overall 0..100, from `SessionSummary.overall`.
 * @param comment short honest caption derived from real data (e.g. valid-ratio phrasing).
 */
@Composable
fun HeroScore(overall: Int, comment: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        TokenText(overall.toString(), Type.score, Hue.ink)
        TokenText(
            text = comment,
            spec = Type.bodySmall,
            color = Hue.muted,
            keepAll = true,
            modifier = Modifier
                .padding(start = Dimens.gapLarge, bottom = 16.dp)
                .weight(1f),
        )
    }
}

/**
 * §4 MiniStatTile — a strong value (20) over a muted span label (12). Used in the 3-up grid
 * (총 반복 / 안정 / 확인). Caller lays them in a [Row] with equal weights.
 */
@Composable
fun MiniStatTile(strong: String, span: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        TokenText(strong, Type.statValue, Hue.ink, maxLines = 1)
        TokenText(span, Type.label, Hue.muted, maxLines = 1)
    }
}

/** Convenience: a 3-up row of [MiniStatTile]s with the standard gap. */
@Composable
fun MiniStatRow(tiles: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
        tiles.forEach { (strong, span) ->
            MiniStatTile(strong = strong, span = span, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * §3 primary button — height 52, radius 12, `ink` fill, white 15/760 text. The host fixes it to the
 * screen bottom by giving the body above it `weight(1f)`.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (enabled) Hue.ink else Hue.lineStrong
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.buttonHeight)
            .clip(RoundedCornerShape(Dimens.radButton))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Hue.surface, fontSize = Type.bodyStrong.size, fontWeight = FontWeight(760))
    }
}

/** A 1px `line` horizontal divider (the report's section rule). */
@Composable
fun HRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.border)
            .background(Hue.line),
    )
}

/**
 * §5 ghost button — 38×38, radius 10, 1px line, white background, holding a single glyph (← / ↺).
 * Stateless: the caller supplies [glyph] and [onClick].
 */
@Composable
fun GhostButton(glyph: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimens.ghostSize)
            .clip(RoundedCornerShape(10.dp))
            .background(Hue.surface)
            .border(BorderStroke(Dimens.border, Hue.line), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = Hue.ink2, fontSize = Type.bodyStrong.size, fontWeight = FontWeight(600))
    }
}

// ---- Previews (cheap, light surface) ----------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFF7F7F5)
@Composable
private fun PrimitivesPreview() {
    Column(modifier = Modifier.padding(Dimens.screenPad), verticalArrangement = Arrangement.spacedBy(Dimens.gapLarge)) {
        HeroScore(overall = 82, comment = "10회 중 8회 안정적이었어요")
        MiniStatRow(tiles = listOf("10" to "총 반복", "8" to "안정", "2" to "확인"))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            GhostButton(glyph = "←", onClick = {})
            GhostButton(glyph = "↺", onClick = {})
        }
        PrimaryButton(text = "1세트 상세 보기", onClick = {})
    }
}
