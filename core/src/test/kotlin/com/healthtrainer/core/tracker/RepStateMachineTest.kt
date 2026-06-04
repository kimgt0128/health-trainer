package com.healthtrainer.core.tracker

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.SquatRule
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import org.junit.Test

/**
 * Tests for [RepStateMachine] — squat/push-up rep segmentation (TOP -> descent -> TOP).
 *
 * Synthetic side-view squat frames (z = 0, already normalized). Each frame carries a distinct
 * timestamp so rep start/end timestamps can be asserted. Rep #2 is deliberately shallow
 * (min knee ~130): too shallow for good form but still a real descent (< 140), so it must be
 * counted and then recorded as a SQUAT_DEPTH_NOT_ENOUGH failure — the loose-descent contract from
 * the pose-rule-authoring skill.
 */
class RepStateMachineTest {

    private val rule = SquatRule()

    private fun lm(name: LandmarkName, x: Float, y: Float, vis: Float = 0.9f) =
        PoseLandmark(name, x, y, 0f, vis)

    private fun squatFrame(
        tsMs: Long,
        shoulder: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        knee: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
    ): PoseFrame {
        val pairs = listOf(
            LandmarkName.LEFT_SHOULDER to shoulder, LandmarkName.RIGHT_SHOULDER to shoulder,
            LandmarkName.LEFT_HIP to hip, LandmarkName.RIGHT_HIP to hip,
            LandmarkName.LEFT_KNEE to knee, LandmarkName.RIGHT_KNEE to knee,
            LandmarkName.LEFT_ANKLE to ankle, LandmarkName.RIGHT_ANKLE to ankle,
        )
        return PoseFrame(tsMs, pairs.map { (n, p) -> lm(n, p.first, p.second) }.associateBy { it.name })
    }

    /** Standing (knee ~170, TOP). */
    private fun standing(ts: Long) = squatFrame(
        ts, shoulder = 0f to 4f, hip = 0f to 3f, knee = 0.0875f to 2f, ankle = 0f to 1f,
    )

    /** Deep squat (knee ~90, valid depth). */
    private fun deep(ts: Long) = squatFrame(
        ts, shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.3f to 1f,
    )

    /** Shallow squat (knee ~130: counts as a descent but fails depth). */
    private fun shallow(ts: Long) = squatFrame(
        ts, shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.7f to 0.42f,
    )

    /** Barely bent (knee ~155: NOT a descent — a tiny bob). */
    private fun bob(ts: Long) = squatFrame(
        ts, shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.42f to 0.1f,
    )

    @Test
    fun threeReps_secondShallow_countsThreeWithSecondInvalid() {
        // rep1 deep(valid) -> rep2 shallow(invalid depth) -> rep3 deep(valid)
        val frames = listOf(
            standing(0),
            deep(100), standing(200),       // rep 1: TOP -> deep -> TOP
            shallow(300), standing(400),     // rep 2: TOP -> shallow -> TOP
            deep(500), standing(600),        // rep 3: TOP -> deep -> TOP
        )
        val reps = RepStateMachine(rule).process(frames)

        assertThat(reps).hasSize(3)

        assertThat(reps[0].valid).isTrue()
        assertThat(reps[0].failures).isEmpty()

        assertThat(reps[1].valid).isFalse()
        assertThat(reps[1].failures).contains(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)

        assertThat(reps[2].valid).isTrue()
        assertThat(reps[2].failures).isEmpty()
    }

    @Test
    fun repTimestamps_spanDescentStartToTopClose() {
        val frames = listOf(standing(0), deep(100), standing(200))
        val reps = RepStateMachine(rule).process(frames)
        assertThat(reps).hasSize(1)
        // Rep starts at the first descent frame and ends at the closing TOP frame.
        assertThat(reps[0].startTimestampMs).isEqualTo(100)
        assertThat(reps[0].endTimestampMs).isEqualTo(200)
    }

    @Test
    fun tinyBob_doesNotCountAsRep() {
        // knee 170 -> 155 -> 170: 155 is above the descent threshold (140) -> no rep.
        val frames = listOf(standing(0), bob(100), standing(200))
        val reps = RepStateMachine(rule).process(frames)
        assertThat(reps).isEmpty()
    }

    @Test
    fun multiFrameDescent_countsSingleRep() {
        // A descent spanning several frames (TOP -> shallow -> deep -> shallow -> TOP) is one rep,
        // and reaching ~90 makes it valid.
        val frames = listOf(
            standing(0), shallow(100), deep(200), shallow(300), standing(400),
        )
        val reps = RepStateMachine(rule).process(frames)
        assertThat(reps).hasSize(1)
        assertThat(reps[0].valid).isTrue()
        assertThat(reps[0].startTimestampMs).isEqualTo(100)
        assertThat(reps[0].endTimestampMs).isEqualTo(400)
    }

    @Test
    fun incompleteRep_atEnd_isNotEmitted() {
        // Descends but never returns to TOP -> no completed rep.
        val frames = listOf(standing(0), deep(100), deep(200))
        val reps = RepStateMachine(rule).process(frames)
        assertThat(reps).isEmpty()
    }

    @Test
    fun lowConfidenceMidRep_isBufferedNotBroken() {
        // A low-confidence frame mid-descent must not split or invalidate the rep; aggregateRep
        // ignores it (no metrics). The deep frame still makes the rep valid.
        val occluded = squatFrame(
            150, shoulder = 0f to 3f, hip = 0f to 2f, knee = 0f to 1f, ankle = 0.3f to 1f,
        ).let { f -> PoseFrame(f.timestampMs, f.landmarks.mapValues { it.value.copy(visibility = 0.3f) }) }
        val frames = listOf(standing(0), deep(100), occluded, standing(200))
        val reps = RepStateMachine(rule).process(frames)
        assertThat(reps).hasSize(1)
        assertThat(reps[0].valid).isTrue()
    }
}
