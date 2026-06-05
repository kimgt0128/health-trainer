package com.healthtrainer.app.ui

import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.scoring.RepScore
import com.healthtrainer.core.scoring.SessionSummary
import com.healthtrainer.core.scoring.SetScore

/**
 * Pure presentation derivations for the result report — turns honest `:core` [SessionSummary] numbers
 * into the small display strings/series the screens render. Android-free so it stays unit-testable
 * (design-system §0.3 honesty: everything here is computed from real fields, nothing fabricated).
 *
 * This is the ONLY place that phrases captions; screens just place the components.
 */
object ReportPresentation {

    /** Hero caption: honest valid-ratio phrasing. Empty session -> a neutral prompt. */
    fun heroComment(summary: SessionSummary): String = when {
        summary.totalReps == 0 -> "기록된 회차가 없어요"
        summary.validReps == summary.totalReps -> "모든 회차가 안정적이었어요"
        else -> "${summary.totalReps}회 중 ${summary.validReps}회 안정적이었어요"
    }

    /** The three mini-stat tiles (value, label): 총 반복 / 안정 / 확인 (= total - valid). */
    fun miniStats(summary: SessionSummary): List<Pair<String, String>> = listOf(
        summary.totalReps.toString() to "총 반복",
        summary.validReps.toString() to "안정",
        (summary.totalReps - summary.validReps).toString() to "확인",
    )

    /** Short caption on a set card: "안정 V / N" from the set's own reps. */
    fun setCardLabel(set: SetScore): String {
        val total = set.reps.size
        val valid = set.reps.count { it.valid }
        return "안정 $valid / $total"
    }

    /** Per-rep `overall` series for a set's sparkline / a trend. */
    fun repOveralls(set: SetScore): List<Int> = set.reps.map { it.overall }

    /**
     * Per-rep score series for one axis [key] within a set (the [com.healthtrainer.app.ui.components.TrendChart]
     * line). Reps missing the axis are skipped (scorers always emit the same keys, so normally none).
     */
    fun axisSeries(set: SetScore, key: String): List<Int> =
        set.reps.mapNotNull { rep -> rep.axes.firstOrNull { it.key == key }?.score }

    /** The indices (into the axis series) of this set's invalid reps — the notable points to mark. */
    fun invalidIndices(set: SetScore): Set<Int> =
        set.reps.mapIndexedNotNull { i, rep -> if (!rep.valid) i else null }.toSet()

    /** The set's mean for one axis (from `SetScore.axes`), or null if the axis isn't present. */
    fun axisMean(set: SetScore, key: String): Int? =
        set.axes.firstOrNull { it.key == key }?.score

    /**
     * The invalid reps of a set, each as (locator, dominant code) for an IssueChip. Locator is
     * "M회차"; the dominant code is the rep's first non-low-confidence failure (the one we coach on).
     * Reps with only [FeedbackCode.LOW_CONFIDENCE] (couldn't evaluate) are skipped — that's not a form
     * fault to fix.
     */
    fun invalidRepIssues(set: SetScore): List<RepIssue> =
        set.reps.filter { !it.valid }.mapNotNull { rep ->
            val code = rep.failures.firstOrNull { it != FeedbackCode.LOW_CONFIDENCE } ?: return@mapNotNull null
            RepIssue(repNo = rep.repNo, code = code)
        }

    /**
     * The dominant failure codes across a set, ordered by frequency (descending), excluding
     * low-confidence. Feeds the set's CoachNote. Pure tally over the set's reps' failures.
     */
    fun setDominantCodes(set: SetScore): List<FeedbackCode> =
        set.reps.flatMap { it.failures }
            .filter { it != FeedbackCode.LOW_CONFIDENCE }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

    /** Pick the rep to replay from a set: the first invalid rep, else the lowest-scoring rep, else null. */
    fun repToReplay(set: SetScore): RepScore? =
        set.reps.firstOrNull { !it.valid } ?: set.reps.minByOrNull { it.overall }

    /** A failed/invalid rep reduced to what a chip needs. */
    data class RepIssue(val repNo: Int, val code: FeedbackCode)
}
