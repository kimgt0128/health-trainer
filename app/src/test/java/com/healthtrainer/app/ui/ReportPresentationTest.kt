package com.healthtrainer.app.ui

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.scoring.AxisScore
import com.healthtrainer.core.scoring.RepScore
import com.healthtrainer.core.scoring.SessionSummary
import com.healthtrainer.core.scoring.SetScore
import org.junit.Test

/**
 * [ReportPresentation] turns `:core` [SessionSummary] numbers into display strings/series. These are
 * the honesty-critical derivations (every value comes from a real field) — plain-JVM testable since
 * the helper is Android-free.
 */
class ReportPresentationTest {

    private fun rep(
        setNo: Int,
        repNo: Int,
        overall: Int,
        valid: Boolean,
        axes: List<AxisScore>,
        failures: Set<FeedbackCode> = emptySet(),
        durationMs: Long = 1000L,
    ) = RepScore(setNo, repNo, valid, overall, axes, failures, durationMs)

    private val squatSet = SetScore(
        setNo = 1,
        overall = 78,
        reps = listOf(
            rep(1, 1, 90, valid = true, axes = listOf(AxisScore("depth", 88, false), AxisScore("torso", 92, false))),
            rep(
                1, 2, 55, valid = false,
                axes = listOf(AxisScore("depth", 40, true), AxisScore("torso", 70, false)),
                failures = setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH),
            ),
            rep(1, 3, 88, valid = true, axes = listOf(AxisScore("depth", 86, false), AxisScore("torso", 90, false))),
        ),
        axes = listOf(AxisScore("depth", 71, true), AxisScore("torso", 84, false)),
    )

    private val summary = SessionSummary(
        exerciseType = ExerciseType.SQUAT,
        overall = 78,
        totalReps = 3,
        validReps = 2,
        sets = listOf(squatSet),
        axes = listOf(AxisScore("depth", 71, true), AxisScore("torso", 84, false)),
        topIssues = emptyList(),
    )

    @Test
    fun miniStats_areTotalValidAndCheck() {
        assertThat(ReportPresentation.miniStats(summary)).containsExactly(
            "3" to "총 반복",
            "2" to "안정",
            "1" to "확인", // total - valid
        ).inOrder()
    }

    @Test
    fun heroComment_phrasesValidRatioHonestly() {
        assertThat(ReportPresentation.heroComment(summary)).isEqualTo("3회 중 2회 안정적이었어요")
    }

    @Test
    fun setCardLabel_countsValidOverTotal() {
        assertThat(ReportPresentation.setCardLabel(squatSet)).isEqualTo("안정 2 / 3")
    }

    @Test
    fun repOveralls_areInOrder() {
        assertThat(ReportPresentation.repOveralls(squatSet)).containsExactly(90, 55, 88).inOrder()
    }

    @Test
    fun axisSeries_picksTheSelectedAxisPerRep() {
        assertThat(ReportPresentation.axisSeries(squatSet, "depth")).containsExactly(88, 40, 86).inOrder()
        assertThat(ReportPresentation.axisSeries(squatSet, "torso")).containsExactly(92, 70, 90).inOrder()
    }

    @Test
    fun invalidIndices_markTheBadReps() {
        assertThat(ReportPresentation.invalidIndices(squatSet)).containsExactly(1) // 2nd rep (index 1)
    }

    @Test
    fun axisMean_readsFromSetAxes() {
        assertThat(ReportPresentation.axisMean(squatSet, "depth")).isEqualTo(71)
        assertThat(ReportPresentation.axisMean(squatSet, "nope")).isNull()
    }

    @Test
    fun invalidRepIssues_dropLowConfidenceOnlyReps() {
        val setWithLowConf = squatSet.copy(
            reps = squatSet.reps + rep(
                1, 4, 0, valid = false,
                axes = emptyList(),
                failures = setOf(FeedbackCode.LOW_CONFIDENCE), // not a form fault to coach
            ),
        )
        val issues = ReportPresentation.invalidRepIssues(setWithLowConf)
        // Only rep 2 yields a coachable issue; the low-confidence-only rep is skipped.
        assertThat(issues.map { it.repNo }).containsExactly(2)
        assertThat(issues.single().code).isEqualTo(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
    }

    @Test
    fun repToReplay_prefersFirstInvalidElseLowest() {
        assertThat(ReportPresentation.repToReplay(squatSet)?.repNo).isEqualTo(2) // first invalid
        val allValid = squatSet.copy(
            reps = listOf(
                rep(1, 1, 95, valid = true, axes = emptyList()),
                rep(1, 2, 60, valid = true, axes = emptyList()), // lowest
                rep(1, 3, 80, valid = true, axes = emptyList()),
            ),
        )
        assertThat(ReportPresentation.repToReplay(allValid)?.repNo).isEqualTo(2) // lowest overall
    }

    @Test
    fun setDominantCodes_orderByFrequencyExcludingLowConfidence() {
        val s = squatSet.copy(
            reps = listOf(
                rep(1, 1, 50, false, emptyList(), setOf(FeedbackCode.SQUAT_TORSO_LEAN)),
                rep(1, 2, 50, false, emptyList(), setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)),
                rep(1, 3, 50, false, emptyList(), setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, FeedbackCode.LOW_CONFIDENCE)),
            ),
        )
        // depth (2) before torso (1); low-confidence excluded entirely.
        assertThat(ReportPresentation.setDominantCodes(s))
            .containsExactly(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, FeedbackCode.SQUAT_TORSO_LEAN).inOrder()
    }
}
