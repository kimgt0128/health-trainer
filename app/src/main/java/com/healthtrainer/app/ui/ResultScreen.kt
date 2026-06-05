package com.healthtrainer.app.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthtrainer.app.ui.components.GhostButton
import com.healthtrainer.app.ui.components.HeroScore
import com.healthtrainer.app.ui.components.HRule
import com.healthtrainer.app.ui.components.MiniStatRow
import com.healthtrainer.app.ui.components.PrimaryButton
import com.healthtrainer.app.ui.components.SetSummaryCard
import com.healthtrainer.app.ui.components.TokenText
import com.healthtrainer.app.ui.theme.Dimens
import com.healthtrainer.app.ui.theme.Hue
import com.healthtrainer.app.ui.theme.Type
import com.healthtrainer.core.scoring.SessionSummary

/**
 * Result summary (design-system §5 결과 요약): eyebrow + h1("{운동} 요약") + a ↺ ghost (restart) ->
 * [HeroScore] -> divider -> three mini-stats (총 반복 / 안정 / 확인) -> "세트별 요약" + one
 * [SetSummaryCard] per set -> a primary button into the set detail.
 *
 * Every number is a real [SessionSummary] field (honesty, §0.3): the hero is `overall`, the stats are
 * `totalReps` / `validReps` / `totalReps - validReps`, each card's score + sparkline come from its
 * [com.healthtrainer.core.scoring.SetScore]. Captions are phrased only by [ReportPresentation] /
 * [ExerciseUiText] — nothing is fabricated.
 *
 * NOTE (requires device): rendering is unverified on an SDK-less machine; the data derivation is
 * inspectable.
 *
 * @param summary  the `:core` session summary (from `MainViewModel.summary`).
 * @param onOpenSet open the detail for a given 1-based `setNo` (a card tap, or the primary button).
 * @param onRestart return to the exercise screen for a new session (the ↺ ghost).
 */
@Composable
fun ResultScreen(
    summary: SessionSummary,
    onOpenSet: (Int) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.screenPad)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
            Spacer(Modifier.height(Dimens.gapLarge))

            // eyebrow + h1 with the ↺ ghost on the right
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    TokenText("오늘의 운동 완료", Type.eyebrow, Hue.muted)
                    TokenText("${ExerciseUiText.label(summary.exerciseType)} 요약", Type.h1, Hue.ink)
                }
                GhostButton(glyph = "↺", onClick = onRestart)
            }

            Spacer(Modifier.height(Dimens.gapLarge))
            HeroScore(overall = summary.overall, comment = ReportPresentation.heroComment(summary))

            Spacer(Modifier.height(Dimens.gapLarge))
            HRule()
            Spacer(Modifier.height(Dimens.gapLarge))

            MiniStatRow(tiles = ReportPresentation.miniStats(summary))

            Spacer(Modifier.height(Dimens.gapLarge))
            TokenText("세트별 요약", Type.section, Hue.muted)
            Spacer(Modifier.height(Dimens.gap))

            if (summary.sets.isEmpty()) {
                TokenText("기록된 세트가 없어요", Type.body, Hue.muted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    summary.sets.forEach { set ->
                        SetSummaryCard(
                            title = "${set.setNo}세트",
                            overall = set.overall,
                            label = ReportPresentation.setCardLabel(set),
                            repOveralls = ReportPresentation.repOveralls(set),
                            onClick = { onOpenSet(set.setNo) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.gapLarge))
        }

        // Primary action: open the first set's detail (cards open a specific one).
        val firstSetNo = summary.sets.firstOrNull()?.setNo
        PrimaryButton(
            text = firstSetNo?.let { "${it}세트 상세 보기" } ?: "세트 상세",
            onClick = { firstSetNo?.let(onOpenSet) },
            enabled = firstSetNo != null,
            modifier = Modifier.padding(bottom = Dimens.gapLarge),
        )
    }
}
