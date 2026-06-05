package com.healthtrainer.core.scoring

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.PlankRule
import com.healthtrainer.core.exercise.PushUpRule
import com.healthtrainer.core.exercise.SquatRule
import org.junit.Test

/**
 * TDD spec for the per-exercise [ExerciseScorer]s and [ScorerRegistry].
 *
 * Scores are 0..100 ints. Axis score formulas reference the RULE's threshold as the SINGLE SOURCE
 * (e.g. [SquatRule.DEPTH_MAX_KNEE_ANGLE]) — there is no second copy of a threshold in the scorer.
 * `failed` on an axis mirrors whether the corresponding rep-level [FeedbackCode] is in the rep's
 * failures. `overall` is the rounded mean of the axis scores. These tests pin the boundary behavior
 * (at-threshold = 100, beyond standing reference = 0, clamped) and the failed flags.
 */
class ScorersTest {

    // ---------- Squat ----------

    private val squatScorer = SquatScorer()

    @Test
    fun squat_axisKeys() {
        assertThat(squatScorer.axisKeys()).containsExactly("depth", "torso").inOrder()
        assertThat(squatScorer.exerciseType).isEqualTo(ExerciseType.SQUAT)
    }

    @Test
    fun squat_deepRep_depthFullAndNotFailed() {
        // min_knee well below the depth threshold -> depth = 100, no depth failure.
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 90f, "mean_torso_angle" to 175f, "min_torso_angle" to 170f),
            failures = emptySet(),
            durationMs = 1500L,
        )
        val depth = score.axes.single { it.key == "depth" }
        assertThat(depth.score).isEqualTo(100)
        assertThat(depth.failed).isFalse()
        assertThat(score.valid).isTrue()
    }

    @Test
    fun squat_atDepthThreshold_isStillFull() {
        // Exactly at DEPTH_MAX_KNEE_ANGLE -> 100 (<= threshold maps to 100).
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to SquatRule.DEPTH_MAX_KNEE_ANGLE, "mean_torso_angle" to 175f),
            failures = emptySet(),
            durationMs = 1000L,
        )
        assertThat(score.axes.single { it.key == "depth" }.score).isEqualTo(100)
    }

    @Test
    fun squat_shallowRep_depthLowAndFailed() {
        // min_knee near the standing reference (~170) + the rule flagged depth -> low score + failed.
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 165f, "mean_torso_angle" to 175f),
            failures = setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH),
            durationMs = 1000L,
        )
        val depth = score.axes.single { it.key == "depth" }
        assertThat(depth.score).isLessThan(20)
        assertThat(depth.failed).isTrue()
        assertThat(score.valid).isFalse()
    }

    @Test
    fun squat_standingReference_depthZero() {
        // min_knee at/above the standing reference -> depth clamps to 0.
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 175f, "mean_torso_angle" to 175f),
            failures = emptySet(),
            durationMs = 1000L,
        )
        assertThat(score.axes.single { it.key == "depth" }.score).isEqualTo(0)
    }

    @Test
    fun squat_uprightTorso_torsoFull() {
        // torso at/above the lean threshold -> torso = 100, not failed.
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 90f, "mean_torso_angle" to SquatRule.TORSO_LEAN_MIN_ANGLE),
            failures = emptySet(),
            durationMs = 1000L,
        )
        val torso = score.axes.single { it.key == "torso" }
        assertThat(torso.score).isEqualTo(100)
        assertThat(torso.failed).isFalse()
    }

    @Test
    fun squat_leaningTorso_torsoLowAndFailed() {
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 90f, "mean_torso_angle" to 110f),
            failures = setOf(FeedbackCode.SQUAT_TORSO_LEAN),
            durationMs = 1000L,
        )
        val torso = score.axes.single { it.key == "torso" }
        assertThat(torso.score).isLessThan(100)
        assertThat(torso.failed).isTrue()
    }

    @Test
    fun squat_overall_isRoundedMeanOfAxes() {
        // depth = 100, torso ~ some value -> overall = round((100 + torso)/2).
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 90f, "mean_torso_angle" to 130f),
            failures = emptySet(),
            durationMs = 1000L,
        )
        val depth = score.axes.single { it.key == "depth" }.score
        val torso = score.axes.single { it.key == "torso" }.score
        assertThat(score.overall).isEqualTo(Math.round((depth + torso) / 2f))
    }

    @Test
    fun squat_scoresClampedToZeroHundred() {
        val score = squatScorer.scoreRep(
            metrics = mapOf("min_knee_angle" to 200f, "mean_torso_angle" to 0f),
            failures = emptySet(),
            durationMs = 1000L,
        )
        score.axes.forEach {
            assertThat(it.score).isAtLeast(0)
            assertThat(it.score).isAtMost(100)
        }
        assertThat(score.overall).isAtLeast(0)
        assertThat(score.overall).isAtMost(100)
    }

    @Test
    fun squat_missingMetric_axisScoresZeroButStillPresent() {
        // No metrics at all (e.g. an all-low-confidence rep). Axes still emitted, scored 0.
        val score = squatScorer.scoreRep(metrics = emptyMap(), failures = emptySet(), durationMs = 0L)
        assertThat(score.axes.map { it.key }).containsExactly("depth", "torso")
        assertThat(score.axes.all { it.score == 0 }).isTrue()
    }

    @Test
    fun squat_carriesFailuresAndDuration() {
        val failures = setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)
        val score = squatScorer.scoreRep(mapOf("min_knee_angle" to 165f), failures, durationMs = 1234L)
        assertThat(score.failures).isEqualTo(failures)
        assertThat(score.durationMs).isEqualTo(1234L)
    }

    // ---------- PushUp ----------

    private val pushUpScorer = PushUpScorer()

    @Test
    fun pushUp_axisKeys() {
        assertThat(pushUpScorer.axisKeys()).containsExactly("depth", "body_line").inOrder()
        assertThat(pushUpScorer.exerciseType).isEqualTo(ExerciseType.PUSH_UP)
    }

    @Test
    fun pushUp_deepRep_depthFull() {
        val score = pushUpScorer.scoreRep(
            metrics = mapOf("min_elbow_angle" to 85f, "min_body_line_angle" to 178f, "body_line_broken_ratio" to 0f),
            failures = emptySet(),
            durationMs = 1000L,
        )
        val depth = score.axes.single { it.key == "depth" }
        assertThat(depth.score).isEqualTo(100)
        assertThat(depth.failed).isFalse()
    }

    @Test
    fun pushUp_shallowRep_depthLowAndFailed() {
        val score = pushUpScorer.scoreRep(
            metrics = mapOf("min_elbow_angle" to 160f, "min_body_line_angle" to 178f, "body_line_broken_ratio" to 0f),
            failures = setOf(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH),
            durationMs = 1000L,
        )
        val depth = score.axes.single { it.key == "depth" }
        assertThat(depth.score).isLessThan(20)
        assertThat(depth.failed).isTrue()
    }

    @Test
    fun pushUp_brokenBodyLine_bodyLineFailedAndLow() {
        val score = pushUpScorer.scoreRep(
            metrics = mapOf("min_elbow_angle" to 85f, "min_body_line_angle" to 140f, "body_line_broken_ratio" to 0.6f),
            failures = setOf(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN),
            durationMs = 1000L,
        )
        val bodyLine = score.axes.single { it.key == "body_line" }
        assertThat(bodyLine.failed).isTrue()
        assertThat(bodyLine.score).isLessThan(100)
    }

    @Test
    fun pushUp_straightBody_bodyLineFull() {
        val score = pushUpScorer.scoreRep(
            metrics = mapOf("min_elbow_angle" to 85f, "min_body_line_angle" to PushUpRule.BODY_LINE_MIN_ANGLE, "body_line_broken_ratio" to 0f),
            failures = emptySet(),
            durationMs = 1000L,
        )
        assertThat(score.axes.single { it.key == "body_line" }.score).isEqualTo(100)
    }

    // ---------- Plank ----------

    private val plankScorer = PlankScorer()

    @Test
    fun plank_axisKeys() {
        assertThat(plankScorer.axisKeys()).containsExactly("body_line", "elbow").inOrder()
        assertThat(plankScorer.exerciseType).isEqualTo(ExerciseType.PLANK)
    }

    @Test
    fun plank_perfectHold_bothAxesFull() {
        // A perfect hold: body line above threshold AND elbow exactly under the shoulder (offset 0).
        val score = plankScorer.scoreRep(
            metrics = mapOf("min_body_line_angle" to 178f, "max_elbow_offset" to 0f),
            failures = emptySet(),
            durationMs = 30_000L,
        )
        assertThat(score.axes.single { it.key == "body_line" }.score).isEqualTo(100)
        assertThat(score.axes.single { it.key == "elbow" }.score).isEqualTo(100)
        assertThat(score.valid).isTrue()
    }

    @Test
    fun plank_smallElbowOffset_scoresHighButBelowFull() {
        // The elbow ramps linearly from 100 at offset 0 to 0 at ELBOW_OFFSET_MAX, so a small (in-band)
        // offset is high but not a perfect 100. 0.05 of a 0.25 max -> (0.25-0.05)/0.25 = 0.8 -> 80.
        val score = plankScorer.scoreRep(
            metrics = mapOf("min_body_line_angle" to 178f, "max_elbow_offset" to 0.05f),
            failures = emptySet(),
            durationMs = 30_000L,
        )
        val elbow = score.axes.single { it.key == "elbow" }.score
        assertThat(elbow).isEqualTo(80)
        assertThat(elbow).isLessThan(100)
    }

    @Test
    fun plank_saggingHold_bodyLineFailedAndLow() {
        val score = plankScorer.scoreRep(
            metrics = mapOf("min_body_line_angle" to 150f, "max_elbow_offset" to 0.05f),
            failures = setOf(FeedbackCode.PLANK_HIPS_LOW),
            durationMs = 10_000L,
        )
        val bodyLine = score.axes.single { it.key == "body_line" }
        assertThat(bodyLine.failed).isTrue()
        assertThat(bodyLine.score).isLessThan(100)
    }

    @Test
    fun plank_elbowOut_elbowAxisLow() {
        // elbow offset at the max -> elbow axis = 0; beyond -> clamped 0.
        val atMax = plankScorer.scoreRep(
            metrics = mapOf("min_body_line_angle" to 178f, "max_elbow_offset" to PlankRule.ELBOW_OFFSET_MAX),
            failures = emptySet(),
            durationMs = 10_000L,
        )
        assertThat(atMax.axes.single { it.key == "elbow" }.score).isEqualTo(0)
        val beyond = plankScorer.scoreRep(
            metrics = mapOf("min_body_line_angle" to 178f, "max_elbow_offset" to 0.5f),
            failures = emptySet(),
            durationMs = 10_000L,
        )
        assertThat(beyond.axes.single { it.key == "elbow" }.score).isEqualTo(0)
    }

    // ---------- Registry ----------

    @Test
    fun registry_returnsAScorerForEveryExerciseType() {
        for (type in ExerciseType.values()) {
            val scorer = ScorerRegistry.forType(type)
            assertThat(scorer).isNotNull()
            assertThat(scorer!!.exerciseType).isEqualTo(type)
        }
    }
}
