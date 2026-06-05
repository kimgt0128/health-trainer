package com.healthtrainer.core.features

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.MovementPhase
import org.junit.Test

/**
 * TDD spec for [PushUpFeatureExtractor] — the :core producer of the push-up-form model's rep-level
 * 10-feature input vector. Unlike [SquatFeatureExtractor] (frame-level [ExerciseFeatureExtractor]),
 * this consumes a *completed rep*: the list of per-frame [ExerciseFeedback]s plus the rep duration.
 *
 * The feature NAMES + ORDER are the train<->inference contract (mirrors the ml track's push-up rep
 * dataset FEATURE_COLUMNS / generated feature_config). Inputs are synthetic [ExerciseFeedback]s built
 * directly (the extractor never sees a PoseFrame), so each expected feature is hand-derivable.
 */
class PushUpFeatureExtractorTest {

    private val extractor = PushUpFeatureExtractor()

    /** A feedback frame carrying push-up metrics (matches what PushUpRule.evaluate emits). */
    private fun fb(
        phase: MovementPhase,
        elbow: Float? = null,
        bodyLine: Float? = null,
        broken: Boolean = false,
    ): ExerciseFeedback {
        val metrics = buildMap {
            if (elbow != null) put("elbowAngle", elbow)
            if (bodyLine != null) put("bodyLineAngle", bodyLine)
        }
        val hard = if (broken) setOf(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN) else emptySet()
        return ExerciseFeedback(
            phase = phase,
            hardFailures = hard,
            softWarnings = emptySet(),
            metrics = metrics,
        )
    }

    private fun idx(name: String) = extractor.featureNames().indexOf(name)

    // ---- contract: names + order + size ---------------------------------------------------------

    @Test
    fun featureNames_areThe10ContractFeaturesInOrder() {
        assertThat(extractor.featureNames()).containsExactly(
            "min_elbow_angle", "max_elbow_angle", "mean_elbow_angle", "elbow_angle_range",
            "min_body_line_angle", "mean_body_line_angle", "body_line_broken_ratio",
            "visible_frame_ratio", "rep_duration_ms", "down_phase_ratio",
        ).inOrder()
    }

    @Test
    fun exerciseType_isPushUp() {
        assertThat(extractor.exerciseType).isEqualTo(ExerciseType.PUSH_UP)
    }

    @Test
    fun featureNamesSize_equalsExtractedVectorSize_andIs10() {
        val rep = listOf(fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f))
        val out = extractor.extract(rep, repDurationMs = 500L)!!
        assertThat(extractor.featureNames().size).isEqualTo(10)
        assertThat(out.size).isEqualTo(10)
        assertThat(out.size).isEqualTo(extractor.featureNames().size)
    }

    // ---- elbow features --------------------------------------------------------------------------

    @Test
    fun elbowFeatures_computeMinMaxMeanRangeFromElbowAngles() {
        // Elbow samples 170, 90, 170 -> min 90, max 170, mean 143.333..., range 80.
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
            fb(MovementPhase.BOTTOM, elbow = 90f, bodyLine = 178f),
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
        )
        val out = extractor.extract(rep, repDurationMs = 600L)!!
        assertThat(out[idx("min_elbow_angle")]).isWithin(1e-3f).of(90f)
        assertThat(out[idx("max_elbow_angle")]).isWithin(1e-3f).of(170f)
        assertThat(out[idx("mean_elbow_angle")]).isWithin(1e-3f).of((170f + 90f + 170f) / 3f)
        assertThat(out[idx("elbow_angle_range")]).isWithin(1e-3f).of(80f)
    }

    // ---- body-line features ----------------------------------------------------------------------

    @Test
    fun bodyLineFeatures_computeMinMeanFromBodyLineAngles() {
        // Body-line samples 178, 150 -> min 150, mean 164.
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
            fb(MovementPhase.BOTTOM, elbow = 90f, bodyLine = 150f, broken = true),
        )
        val out = extractor.extract(rep, repDurationMs = 400L)!!
        assertThat(out[idx("min_body_line_angle")]).isWithin(1e-3f).of(150f)
        assertThat(out[idx("mean_body_line_angle")]).isWithin(1e-3f).of((178f + 150f) / 2f)
    }

    @Test
    fun bodyLineFeatures_areZeroWhenNoBodyLineSamples() {
        // Elbows present (so not gated to null) but no bodyLineAngle anywhere -> min/mean = 0.
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f),
            fb(MovementPhase.BOTTOM, elbow = 90f),
        )
        val out = extractor.extract(rep, repDurationMs = 300L)!!
        assertThat(out[idx("min_body_line_angle")]).isEqualTo(0f)
        assertThat(out[idx("mean_body_line_angle")]).isEqualTo(0f)
    }

    // ---- body_line_broken_ratio: broken frames / bodyLine-bearing frames ------------------------

    @Test
    fun bodyLineBrokenRatio_isBrokenFramesOverBodyLineBearingFrames() {
        // 4 frames carry bodyLineAngle; 1 of them is broken -> ratio 0.25. The 5th frame has no
        // bodyLineAngle (low-confidence-ish) and must not count in the denominator.
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
            fb(MovementPhase.BOTTOM, elbow = 95f, bodyLine = 175f),
            fb(MovementPhase.BOTTOM, elbow = 90f, bodyLine = 150f, broken = true),
            fb(MovementPhase.TOP, elbow = 168f, bodyLine = 176f),
            fb(MovementPhase.UNKNOWN, elbow = 120f), // no bodyLineAngle
        )
        val out = extractor.extract(rep, repDurationMs = 800L)!!
        assertThat(out[idx("body_line_broken_ratio")]).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun bodyLineBrokenRatio_isZeroWhenNoBodyLineSamples() {
        val rep = listOf(fb(MovementPhase.TOP, elbow = 170f), fb(MovementPhase.BOTTOM, elbow = 90f))
        val out = extractor.extract(rep, repDurationMs = 200L)!!
        assertThat(out[idx("body_line_broken_ratio")]).isEqualTo(0f)
    }

    // ---- visible_frame_ratio: elbow-bearing frames / total frames -------------------------------

    @Test
    fun visibleFrameRatio_isElbowBearingFramesOverTotalFrames() {
        // 4 total frames, 3 carry an elbowAngle -> 0.75.
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
            fb(MovementPhase.BOTTOM, elbow = 90f, bodyLine = 178f),
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
            fb(MovementPhase.UNKNOWN), // low-confidence: no metrics
        )
        val out = extractor.extract(rep, repDurationMs = 1000L)!!
        assertThat(out[idx("visible_frame_ratio")]).isWithin(1e-4f).of(0.75f)
    }

    // ---- rep_duration_ms -------------------------------------------------------------------------

    @Test
    fun repDurationMs_isThePassedDurationAsFloat() {
        val rep = listOf(fb(MovementPhase.TOP, elbow = 170f))
        val out = extractor.extract(rep, repDurationMs = 1234L)!!
        assertThat(out[idx("rep_duration_ms")]).isWithin(1e-3f).of(1234f)
    }

    // ---- down_phase_ratio: BOTTOM frames / total frames -----------------------------------------

    @Test
    fun downPhaseRatio_isBottomFramesOverTotalFrames() {
        // 4 total frames, 2 are BOTTOM -> 0.5. (UNKNOWN/TOP don't count.)
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f),
            fb(MovementPhase.BOTTOM, elbow = 90f),
            fb(MovementPhase.BOTTOM, elbow = 95f),
            fb(MovementPhase.UNKNOWN),
        )
        val out = extractor.extract(rep, repDurationMs = 700L)!!
        assertThat(out[idx("down_phase_ratio")]).isWithin(1e-4f).of(0.5f)
    }

    // ---- gate: no elbow samples -> null (model defers) ------------------------------------------

    @Test
    fun noElbowSamples_returnsNull() {
        // All frames low-confidence (no elbowAngle) -> required joints occluded -> model can't run.
        val rep = listOf(
            fb(MovementPhase.UNKNOWN),
            fb(MovementPhase.UNKNOWN, bodyLine = 178f), // bodyLine present but elbow absent
        )
        assertThat(extractor.extract(rep, repDurationMs = 500L)).isNull()
    }

    @Test
    fun emptyRep_returnsNull() {
        assertThat(extractor.extract(emptyList(), repDurationMs = 0L)).isNull()
    }

    // ---- full rep: every slot lands in the right place ------------------------------------------

    @Test
    fun fullRep_allTenFeaturesLandInContractOrder() {
        // 3 frames: up(170, line 178, TOP), down(90, line 150 broken, BOTTOM), up(170, line 178, TOP).
        val rep = listOf(
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
            fb(MovementPhase.BOTTOM, elbow = 90f, bodyLine = 150f, broken = true),
            fb(MovementPhase.TOP, elbow = 170f, bodyLine = 178f),
        )
        val out = extractor.extract(rep, repDurationMs = 600L)!!

        // Positional contract: featureNames()[i] <-> out[i].
        assertThat(out[0]).isWithin(1e-3f).of(90f)                          // min_elbow_angle
        assertThat(out[1]).isWithin(1e-3f).of(170f)                         // max_elbow_angle
        assertThat(out[2]).isWithin(1e-3f).of((170f + 90f + 170f) / 3f)     // mean_elbow_angle
        assertThat(out[3]).isWithin(1e-3f).of(80f)                          // elbow_angle_range
        assertThat(out[4]).isWithin(1e-3f).of(150f)                         // min_body_line_angle
        assertThat(out[5]).isWithin(1e-3f).of((178f + 150f + 178f) / 3f)    // mean_body_line_angle
        assertThat(out[6]).isWithin(1e-4f).of(1f / 3f)                      // body_line_broken_ratio
        assertThat(out[7]).isWithin(1e-4f).of(1f)                           // visible_frame_ratio
        assertThat(out[8]).isWithin(1e-3f).of(600f)                         // rep_duration_ms
        assertThat(out[9]).isWithin(1e-4f).of(1f / 3f)                      // down_phase_ratio
    }
}
