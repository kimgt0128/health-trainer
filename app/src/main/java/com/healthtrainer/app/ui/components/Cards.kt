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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type

/**
 * §4 SetSummaryCard — `N세트` (left) + `점수 · 라벨` and a [Sparkline] (right). Tapping opens the set
 * detail. Selected/strong variant uses the `soft` fill + `lineStrong` border (no hue change).
 *
 * @param title  e.g. "1세트".
 * @param overall the set's `overall`.
 * @param label  short honest caption (e.g. "안정 4 / 5").
 * @param repOveralls per-rep overalls for the sparkline (`SetScore.reps.map { it.overall }`).
 */
@Composable
fun SetSummaryCard(
    title: String,
    overall: Int,
    label: String,
    repOveralls: List<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(Dimens.radCard)
    val bg = if (selected) Hue.soft else Hue.surface
    val borderColor = if (selected) Hue.lineStrong else Hue.line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(BorderStroke(Dimens.border, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(Dimens.cardPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenText(title, Type.bodyStrong, Hue.ink, maxLines = 1)
        TokenText(
            text = "$overall · $label",
            spec = Type.label,
            color = Hue.muted,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = Dimens.gap),
        )
        Sparkline(values = repOveralls)
    }
}

/**
 * §4 SegmentedTabs — axis switch with a `soft` track; the active segment is a white pill with a soft
 * shadow + ink label. Stateless: caller owns the selected index.
 *
 * @param labels display labels (already mapped from axis keys).
 * @param selectedIndex active segment.
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = RoundedCornerShape(Dimens.radTab)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outer)
            .background(Hue.soft)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, text ->
            val active = i == selectedIndex
            val seg = RoundedCornerShape(Dimens.radTab - 2.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(seg)
                    .background(if (active) Hue.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TokenText(
                    text = text,
                    spec = if (active) Type.labelStrong else Type.label,
                    color = if (active) Hue.ink else Hue.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * §4 IssueChip — a strong reason (left) + a muted tag (우선/확인/유지, right). The strong variant uses
 * the `soft` fill + `lineStrong` border. Stateless: caller passes the already-mapped label + tag.
 *
 * @param reason the Korean reason (via FeedbackText.label, optionally prefixed with the rep locator).
 * @param tag    the severity tag (via FeedbackText.issueTag).
 */
@Composable
fun IssueChip(
    reason: String,
    tag: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
) {
    val shape = RoundedCornerShape(Dimens.radChip)
    val bg = if (strong) Hue.soft else Hue.surface
    val borderColor = if (strong) Hue.lineStrong else Hue.line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(BorderStroke(Dimens.border, borderColor), shape)
            .padding(horizontal = Dimens.cardPad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenText(
            text = reason,
            spec = Type.bodyStrong,
            color = Hue.ink2,
            keepAll = true,
            modifier = Modifier.weight(1f),
        )
        TokenText(tag, Type.label, Hue.muted, maxLines = 1, modifier = Modifier.padding(start = Dimens.gap))
    }
}

/**
 * §4 CoachNote — a `#FAFAFA` card with a 14sp title and muted 13sp bullet rows (each led by a 4dp
 * dot). The text is the deterministic FeedbackText template (NOT generated). Stateless.
 *
 * @param title   note title.
 * @param bullets ordered cue lines.
 */
@Composable
fun CoachNote(title: String, bullets: List<String>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.radCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFFAFAFA))
            .border(BorderStroke(Dimens.border, Hue.line), shape)
            .padding(Dimens.cardPad),
        verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
    ) {
        TokenText(title, Type.noteTitle, Hue.ink, keepAll = true)
        bullets.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp, end = Dimens.gapSmall)
                        .clip(RoundedCornerShape(50))
                        .background(Hue.muted)
                        .size(4.dp),
                )
                TokenText(line, Type.noteBullet, Hue.muted, keepAll = true)
            }
        }
    }
}

// ---- Previews ----------------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFF7F7F5)
@Composable
private fun CardsPreview() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimens.screenPad),
        verticalArrangement = Arrangement.spacedBy(Dimens.gapLarge),
    ) {
        SetSummaryCard(title = "1세트", overall = 82, label = "안정 4 / 5", repOveralls = listOf(70, 84, 64, 90, 88), onClick = {})
        SegmentedTabs(labels = listOf("깊이", "상체"), selectedIndex = 0, onSelect = {})
        IssueChip(reason = "2회차 · 스쿼트 깊이 부족", tag = "우선", strong = true)
        IssueChip(reason = "4회차 · 상체 과도하게 숙임", tag = "확인")
        CoachNote(
            title = "다음에 신경 쓸 점",
            bullets = listOf("허벅지가 무릎 높이 아래로 내려가도록 더 깊게 앉아 보세요.", "가슴을 세워 상체가 숙여지지 않게 하세요."),
        )
    }
}
