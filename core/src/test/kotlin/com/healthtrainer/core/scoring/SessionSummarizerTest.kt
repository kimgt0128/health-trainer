package com.healthtrainer.core.scoring

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.tracker.ExerciseSession
import com.healthtrainer.core.tracker.RepRecord
import com.healthtrainer.core.tracker.SetRecord
import org.junit.Test

/**
 * TDD spec for [SessionSummarizer.summarize] — the pure aggregation from a tracked
 * [ExerciseSession] (whose [RepRecord]s now carry `metrics` + `failures`) to a [SessionSummary].
 *
 * Pins: per-rep scoring via [ScorerRegistry] with setNo/repNo attached, set/session overalls as
 * rounded means of rep overalls, per-axis means, valid/total counts, the descending top-issue tally
 * (excluding LOW_CONFIDENCE), the empty-session zero case, and the unregistered-type graceful
 * fallback (valid -> 100 / invalid -> 0, empty axes).
 */
class SessionSummarizerTest {

    private fun squatRep(
        setNo: Int,
        repNo: Int,
        minKnee: Float,
        meanTorso: Float,
        failures: Set<FeedbackCode> = emptySet(),
        startMs: Long = 0L,
        endMs: Long = 1000L,
    ) = RepRecord(
        setNo = setNo,
        repNo = repNo,
        valid = failures.isEmpty(),
        failures = failures,
        startTimestampMs = startMs,
        endTimestampMs = endMs,
        metrics = mapOf("min_knee_angle" to minKnee, "mean_torso_angle" to meanTorso),
    )

    @Test
    fun emptySession_zeroOverallAndEmptyLists() {
        val session = ExerciseSession(ExerciseType.SQUAT, startedAtMs = 0L, sets = emptyList())
        val summary = SessionSummarizer.summarize(session)

        assertThat(summary.exerciseType).isEqualTo(ExerciseType.SQUAT)
        assertThat(summary.overall).isEqualTo(0)
        assertThat(summary.totalReps).isEqualTo(0)
        assertThat(summary.validReps).isEqualTo(0)
        assertThat(summary.sets).isEmpty()
        assertThat(summary.axes).isEmpty()
        assertThat(summary.topIssues).isEmpty()
    }

    @Test
    fun allValidSession_highOverallAndAllValidCounts() {
        // Two deep, upright reps -> both axes ~100 -> overall near 100.
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(
                    squatRep(1, 1, minKnee = 90f, meanTorso = 175f),
                    squatRep(1, 2, minKnee = 95f, meanTorso = 170f),
                )),
            ),
        )
        val summary = SessionSummarizer.summarize(session)

        assertThat(summary.totalReps).isEqualTo(2)
        assertThat(summary.validReps).isEqualTo(2)
        assertThat(summary.overall).isAtLeast(95)
        assertThat(summary.topIssues).isEmpty()
        // Axes present for the squat scorer.
        assertThat(summary.axes.map { it.key }).containsExactly("depth", "torso")
    }

    @Test
    fun summarizer_attachesSetAndRepNumbersToScoredReps() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(squatRep(1, 1, 90f, 175f), squatRep(1, 2, 92f, 172f))),
                SetRecord(2, listOf(squatRep(2, 1, 95f, 170f))),
            ),
        )
        val summary = SessionSummarizer.summarize(session)

        assertThat(summary.sets.map { it.setNo }).containsExactly(1, 2).inOrder()
        val set1 = summary.sets.first { it.setNo == 1 }
        assertThat(set1.reps.map { it.repNo }).containsExactly(1, 2).inOrder()
        assertThat(set1.reps.all { it.setNo == 1 }).isTrue()
        val set2 = summary.sets.first { it.setNo == 2 }
        assertThat(set2.reps.single().setNo).isEqualTo(2)
        assertThat(set2.reps.single().repNo).isEqualTo(1)
    }

    @Test
    fun setOverall_isRoundedMeanOfRepOveralls() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(
                    squatRep(1, 1, minKnee = 90f, meanTorso = 175f),  // ~100
                    squatRep(1, 2, minKnee = 165f, meanTorso = 110f, failures = setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)), // low
                )),
            ),
        )
        val summary = SessionSummarizer.summarize(session)
        val set = summary.sets.single()
        val expected = Math.round(set.reps.map { it.overall }.average().toFloat())
        assertThat(set.overall).isEqualTo(expected)
    }

    @Test
    fun setAxes_arePerAxisMeanAcrossReps() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(
                    squatRep(1, 1, minKnee = 90f, meanTorso = 175f),
                    squatRep(1, 2, minKnee = 165f, meanTorso = 110f, failures = setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)),
                )),
            ),
        )
        val summary = SessionSummarizer.summarize(session)
        val set = summary.sets.single()

        val depthMean = Math.round(set.reps.map { r -> r.axes.single { it.key == "depth" }.score }.average().toFloat())
        val setDepth = set.axes.single { it.key == "depth" }.score
        assertThat(setDepth).isEqualTo(depthMean)
    }

    @Test
    fun sessionOverall_isRoundedMeanOfAllRepOveralls() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(squatRep(1, 1, 90f, 175f))),
                SetRecord(2, listOf(squatRep(2, 1, 165f, 110f, setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)))),
            ),
        )
        val summary = SessionSummarizer.summarize(session)
        val allReps = summary.sets.flatMap { it.reps }
        val expected = Math.round(allReps.map { it.overall }.average().toFloat())
        assertThat(summary.overall).isEqualTo(expected)
    }

    @Test
    fun mixedSession_countsValidRepsAndTalliesIssuesDescending() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(
                    squatRep(1, 1, 90f, 175f), // valid
                    squatRep(1, 2, 165f, 175f, setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)), // depth fail
                    squatRep(1, 3, 165f, 110f, setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, FeedbackCode.SQUAT_TORSO_LEAN)), // both
                )),
            ),
        )
        val summary = SessionSummarizer.summarize(session)

        assertThat(summary.totalReps).isEqualTo(3)
        assertThat(summary.validReps).isEqualTo(1)
        // DEPTH appears in 2 reps, TORSO in 1 -> descending order DEPTH then TORSO.
        assertThat(summary.topIssues.map { it.code })
            .containsExactly(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, FeedbackCode.SQUAT_TORSO_LEAN).inOrder()
        assertThat(summary.topIssues.first().count).isEqualTo(2)
        assertThat(summary.topIssues.last().count).isEqualTo(1)
    }

    @Test
    fun topIssues_excludeLowConfidence() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(
                    squatRep(1, 1, 165f, 175f, setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, FeedbackCode.LOW_CONFIDENCE)),
                )),
            ),
        )
        val summary = SessionSummarizer.summarize(session)
        assertThat(summary.topIssues.map { it.code }).doesNotContain(FeedbackCode.LOW_CONFIDENCE)
        assertThat(summary.topIssues.map { it.code }).containsExactly(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
    }

    @Test
    fun sessionAxes_arePerAxisMeanAcrossAllReps() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(
                SetRecord(1, listOf(squatRep(1, 1, 90f, 175f))),
                SetRecord(2, listOf(squatRep(2, 1, 165f, 110f, setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)))),
            ),
        )
        val summary = SessionSummarizer.summarize(session)
        val allReps = summary.sets.flatMap { it.reps }
        val depthMean = Math.round(allReps.map { r -> r.axes.single { it.key == "depth" }.score }.average().toFloat())
        assertThat(summary.axes.single { it.key == "depth" }.score).isEqualTo(depthMean)
    }

    @Test
    fun repDuration_isEndMinusStart() {
        val session = ExerciseSession(
            ExerciseType.SQUAT, startedAtMs = 0L,
            sets = listOf(SetRecord(1, listOf(squatRep(1, 1, 90f, 175f, startMs = 200L, endMs = 1700L)))),
        )
        val summary = SessionSummarizer.summarize(session)
        assertThat(summary.sets.single().reps.single().durationMs).isEqualTo(1500L)
    }

    // --- Unregistered-type graceful fallback ---------------------------------------------------

    @Test
    fun unregisteredType_fallsBackToValidRatioWithEmptyAxes() {
        // Drive the documented fallback directly: a session scored with NO scorer.
        val reps = listOf(
            squatRep(1, 1, 90f, 175f),                                   // valid -> 100
            squatRep(1, 2, 165f, 110f, setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)), // invalid -> 0
        )
        val session = ExerciseSession(ExerciseType.SQUAT, startedAtMs = 0L, sets = listOf(SetRecord(1, reps)))

        val summary = SessionSummarizer.summarizeWith(session, scorer = null)

        assertThat(summary.totalReps).isEqualTo(2)
        assertThat(summary.validReps).isEqualTo(1)
        // valid -> 100, invalid -> 0, mean = 50.
        assertThat(summary.overall).isEqualTo(50)
        assertThat(summary.axes).isEmpty()
        summary.sets.single().reps.let { scored ->
            assertThat(scored.first { it.repNo == 1 }.overall).isEqualTo(100)
            assertThat(scored.first { it.repNo == 2 }.overall).isEqualTo(0)
            assertThat(scored.all { it.axes.isEmpty() }).isTrue()
        }
        // Issues are still tallied from the rep failures even without a scorer.
        assertThat(summary.topIssues.map { it.code }).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
    }
}
