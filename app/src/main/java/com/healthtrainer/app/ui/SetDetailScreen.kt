package com.healthtrainer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.components.CoachNote
import com.healthtrainer.app.ui.components.GhostButton
import com.healthtrainer.app.ui.components.IssueChip
import com.healthtrainer.app.ui.components.MetricBar
import com.healthtrainer.app.ui.components.PrimaryButton
import com.healthtrainer.app.ui.components.SegmentedTabs
import com.healthtrainer.app.ui.components.TokenText
import com.healthtrainer.app.ui.components.TrendChart
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type
import com.healthtrainer.core.scoring.SessionSummary
import com.healthtrainer.core.scoring.SetScore

/**
 * Set detail (design-system §5 회차별 추세): eyebrow("{N}세트 상세") + h2("회차별 추세") + a ← ghost
 * (back) -> [SegmentedTabs] over THIS set's axis keys -> a [TrendChart] of the selected axis (per-rep
 * axis score + the set mean as the secondary line) -> a [MetricBar] per `setScore.axes` -> the
 * invalid reps as [IssueChip]s -> a templated [CoachNote] -> a primary "문제 회차 리플레이" button.
 *
 * Honesty (§0.3): the tabs are exactly the axis keys the scorer emitted (squat depth/torso, push-up
 * depth/body_line, plank body_line/elbow) — no fabricated axis (e.g. NO squat 무릎/valgus bar; the
 * sagittal rule doesn't measure it). The trend/bar values are the rep/set [AxisScore.score]s; the
 * chips/notes come from real [RepScore.failures]. A single-rep set (a plank hold) trends as one dot.
 *
 * NOTE (requires device): rendering is unverified on an SDK-less machine; the derivation is inspectable.
 *
 * @param summary  the session summary; [setNo] selects the set to detail.
 * @param setNo    1-based set number passed from the result screen.
 * @param onBack   the ← ghost / system back -> back to the result screen.
 * @param onReplay open the replay for this set's problem rep.
 */
@Composable
fun SetDetailScreen(
    summary: SessionSummary,
    setNo: Int,
    onBack: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = summary.sets.firstOrNull { it.setNo == setNo }
    if (set == null) {
        MissingSet(onBack = onBack, modifier = modifier)
        return
    }

    val axisKeys = set.axes.map { it.key }
    var tab by remember(set.setNo) { mutableIntStateOf(0) }
    val selectedKey = axisKeys.getOrNull(tab.coerceIn(0, (axisKeys.size - 1).coerceAtLeast(0)))

    val scroll = rememberScrollState()
    val coach = FeedbackText.coachNote(ReportPresentation.setDominantCodes(set))

    Column(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.screenPad)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
            Spacer(Modifier.height(Dimens.gapLarge))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                GhostButton(glyph = "←", onClick = onBack)
                Column(modifier = Modifier.padding(start = Dimens.gap)) {
                    TokenText("${set.setNo}세트 상세", Type.eyebrow, Hue.muted)
                    TokenText("회차별 추세", Type.h2, Hue.ink)
                }
            }

            Spacer(Modifier.height(Dimens.gapLarge))

            if (axisKeys.isNotEmpty()) {
                SegmentedTabs(
                    labels = axisKeys.map { FeedbackText.axisLabel(it) },
                    selectedIndex = tab.coerceIn(0, axisKeys.lastIndex),
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(Dimens.gap))
            }

            // Trend card for the selected axis.
            CardSurface {
                if (selectedKey != null) {
                    TokenText(
                        text = "${FeedbackText.axisLabel(selectedKey)} 회차별 점수",
                        spec = Type.label,
                        color = Hue.muted,
                    )
                    Spacer(Modifier.height(Dimens.gapSmall))
                    TrendChart(
                        values = ReportPresentation.axisSeries(set, selectedKey),
                        mean = ReportPresentation.axisMean(set, selectedKey),
                        highlight = ReportPresentation.invalidIndices(set),
                    )
                } else {
                    // No scorer axes (graceful-fallback session): show the per-rep overall instead.
                    TokenText("회차별 종합 점수", Type.label, Hue.muted)
                    Spacer(Modifier.height(Dimens.gapSmall))
                    TrendChart(
                        values = ReportPresentation.repOveralls(set),
                        mean = set.overall,
                        highlight = ReportPresentation.invalidIndices(set),
                    )
                }
            }

            if (set.axes.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.gapLarge))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    set.axes.forEach { axis ->
                        MetricBar(
                            label = FeedbackText.axisLabel(axis.key),
                            score = axis.score,
                            emphasized = axis.failed,
                        )
                    }
                }
            }

            val issues = ReportPresentation.invalidRepIssues(set)
            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.gapLarge))
                TokenText("눈에 띄는 회차", Type.section, Hue.muted)
                Spacer(Modifier.height(Dimens.gap))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    issues.forEach { issue ->
                        IssueChip(
                            reason = "${issue.repNo}회차 · ${FeedbackText.label(issue.code)}",
                            tag = FeedbackText.issueTag(issue.code),
                            strong = FeedbackText.severity(issue.code) == FeedbackText.Severity.HARD,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.gapLarge))
            CoachNote(title = coach.title, bullets = coach.bullets)

            Spacer(Modifier.height(Dimens.gapLarge))
        }

        val canReplay = ReportPresentation.repToReplay(set) != null
        PrimaryButton(
            text = "문제 회차 리플레이",
            onClick = onReplay,
            enabled = canReplay,
            modifier = Modifier.padding(bottom = Dimens.gapLarge),
        )
    }
}

/** White rounded card wrapper for the trend chart (token-driven). */
@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Dimens.radCard)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Hue.surface)
            .border(BorderStroke(Dimens.border, Hue.line), shape)
            .padding(Dimens.cardPad),
    ) { content() }
}

@Composable
private fun MissingSet(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(Dimens.screenPad)) {
        GhostButton(glyph = "←", onClick = onBack)
        Spacer(Modifier.height(Dimens.gapLarge))
        TokenText("세트를 찾을 수 없어요", Type.body, Hue.muted)
    }
}
