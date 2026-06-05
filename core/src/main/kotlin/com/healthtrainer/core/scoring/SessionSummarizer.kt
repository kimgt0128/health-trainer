package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.tracker.ExerciseSession
import com.healthtrainer.core.tracker.RepRecord
import com.healthtrainer.core.tracker.SetRecord

/**
 * Pure aggregation from a tracked [ExerciseSession] (whose [RepRecord]s carry the rule's per-rep
 * `metrics` + rep-level `failures`) to a [SessionSummary] for the result report.
 *
 * Honest by construction: every number comes from a rep the rule engine actually recorded. Per rep
 * we ask [ScorerRegistry] for the exercise's [ExerciseScorer]; set/session overalls are rounded means
 * of rep overalls; axes are per-axis means; `topIssues` is the [FeedbackCode] frequency across all
 * reps (descending, excluding [FeedbackCode.LOW_CONFIDENCE], which is an "couldn't evaluate" signal,
 * not a form fault).
 *
 * If no scorer is registered for the session's type, it degrades gracefully (it does not crash):
 * each rep's overall falls back to its valid flag (valid -> 100, invalid -> 0) with empty axes, and
 * the summary still computes counts and the issue tally.
 */
object SessionSummarizer {

    /** Summarize [session] using the registered scorer for its type (null -> valid-ratio fallback). */
    fun summarize(session: ExerciseSession): SessionSummary =
        summarizeWith(session, ScorerRegistry.forType(session.exerciseType))

    /**
     * Summarize [session] with an explicit [scorer] (or `null` for the valid-ratio fallback). The
     * seam that makes the no-scorer path testable without an unregistered [ExerciseType].
     */
    internal fun summarizeWith(session: ExerciseSession, scorer: ExerciseScorer?): SessionSummary {
        val sets = session.sets.map { set -> scoreSet(set, scorer) }
        val allReps = sets.flatMap { it.reps }

        val overall = meanOverall(allReps)
        val axes = meanAxes(allReps)
        val totalReps = allReps.size
        val validReps = allReps.count { it.valid }
        val topIssues = tallyIssues(allReps)

        return SessionSummary(
            exerciseType = session.exerciseType,
            overall = overall,
            totalReps = totalReps,
            validReps = validReps,
            sets = sets,
            axes = axes,
            topIssues = topIssues,
        )
    }

    private fun scoreSet(set: SetRecord, scorer: ExerciseScorer?): SetScore {
        val reps = set.reps.map { rep -> scoreRep(rep, scorer) }
        return SetScore(
            setNo = set.setNo,
            overall = meanOverall(reps),
            reps = reps,
            axes = meanAxes(reps),
        )
    }

    private fun scoreRep(rep: RepRecord, scorer: ExerciseScorer?): RepScore {
        val durationMs = rep.endTimestampMs - rep.startTimestampMs
        return if (scorer != null) {
            // The scorer owns overall+axes; we attach the set/rep numbering it leaves at 0.
            scorer.scoreRep(rep.metrics, rep.failures, durationMs)
                .copy(setNo = rep.setNo, repNo = rep.repNo)
        } else {
            // Graceful fallback: no axes, overall from the rep's own valid flag.
            RepScore(
                setNo = rep.setNo,
                repNo = rep.repNo,
                valid = rep.valid,
                overall = if (rep.valid) 100 else 0,
                axes = emptyList(),
                failures = rep.failures,
                durationMs = durationMs,
            )
        }
    }

    /** Rounded mean of the reps' overalls (0 when there are no reps). */
    private fun meanOverall(reps: List<RepScore>): Int =
        if (reps.isEmpty()) 0 else Math.round(reps.map { it.overall }.average().toFloat()).coerceIn(0, 100)

    /**
     * Per-axis mean across [reps], preserving each axis's order and OR-ing its `failed` flag (an axis
     * is "failed" for the group if it failed in any rep). Empty when there are no reps / no axes.
     */
    private fun meanAxes(reps: List<RepScore>): List<AxisScore> {
        if (reps.isEmpty()) return emptyList()
        // Axis keys in first-seen order (every rep of a given exercise emits the same keys).
        val keys = reps.flatMap { it.axes.map { a -> a.key } }.distinct()
        return keys.map { key ->
            val perRep = reps.mapNotNull { rep -> rep.axes.firstOrNull { it.key == key } }
            val meanScore = Math.round(perRep.map { it.score }.average().toFloat()).coerceIn(0, 100)
            AxisScore(key = key, score = meanScore, failed = perRep.any { it.failed })
        }
    }

    /** [FeedbackCode] frequency across [reps], descending by count, excluding LOW_CONFIDENCE. */
    private fun tallyIssues(reps: List<RepScore>): List<IssueTally> =
        reps.flatMap { it.failures }
            .filter { it != FeedbackCode.LOW_CONFIDENCE }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { IssueTally(it.key, it.value) }
}
