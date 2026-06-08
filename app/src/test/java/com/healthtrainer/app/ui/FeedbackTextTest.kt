package com.healthtrainer.app.ui

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.tracker.RepRecord
import org.junit.Test

/** [FeedbackText] is deliberately Android-free, so its presentation mapping is plain-JVM testable. */
class FeedbackTextTest {

    @Test
    fun axisLabel_mapsTheScorerKeys() {
        // The exact keys SquatScorer/PushUpScorer/PlankScorer emit (design-system §6 single catalog).
        assertThat(FeedbackText.axisLabel("depth")).isEqualTo("깊이")
        assertThat(FeedbackText.axisLabel("torso")).isEqualTo("상체")
        assertThat(FeedbackText.axisLabel("body_line")).isEqualTo("몸통 일직선")
        assertThat(FeedbackText.axisLabel("elbow")).isEqualTo("팔꿈치 정렬")
    }

    @Test
    fun axisLabel_unknownKey_fallsBackToRaw() {
        assertThat(FeedbackText.axisLabel("future_axis")).isEqualTo("future_axis")
    }

    @Test
    fun issueTag_followsSeverity() {
        // HARD -> 우선, SOFT -> 확인, INFO -> 유지 (pure function of severity).
        assertThat(FeedbackText.issueTag(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)).isEqualTo("우선")
        assertThat(FeedbackText.issueTag(FeedbackCode.SQUAT_TORSO_LEAN)).isEqualTo("확인")
        assertThat(FeedbackText.issueTag(FeedbackCode.LOW_CONFIDENCE)).isEqualTo("유지")
    }

    @Test
    fun coachNote_isDeterministicFromDominantCodes() {
        val note = FeedbackText.coachNote(
            listOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, FeedbackCode.SQUAT_TORSO_LEAN),
        )
        assertThat(note.title).isEqualTo("다음에 신경 쓸 점")
        assertThat(note.bullets).containsExactly(
            FeedbackText.coachCue(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH),
            FeedbackText.coachCue(FeedbackCode.SQUAT_TORSO_LEAN),
        ).inOrder()
    }

    @Test
    fun coachNote_noIssues_isEncouragingNotEmpty() {
        val note = FeedbackText.coachNote(emptyList())
        assertThat(note.title).isEqualTo("잘하고 있어요")
        assertThat(note.bullets).hasSize(1)
    }

    @Test
    fun coachNote_capsAndDeduplicates() {
        val note = FeedbackText.coachNote(
            listOf(
                FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH,
                FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH, // dup
                FeedbackCode.SQUAT_TORSO_LEAN,
                FeedbackCode.PLANK_HIPS_LOW,
                FeedbackCode.PLANK_HIPS_HIGH, // beyond maxCues=3
            ),
        )
        assertThat(note.bullets).hasSize(3)
    }

    @Test
    fun modelFormLabel_pushUpIncorrect_isConservativeKorean() {
        // Push-up's binary "incorrect" surfaces as a conservative hint (not a hard verdict).
        assertThat(FeedbackText.modelFormLabel("incorrect")).isEqualTo("푸쉬업 자세 확인 필요 (모델)")
    }

    @Test
    fun modelFormLabel_squatClass_isMapped() {
        assertThat(FeedbackText.modelFormLabel("knees_caving_in")).isEqualTo("무릎이 안쪽으로 모임 (모델)")
    }

    @Test
    fun modelFormLabel_plankClasses_areConservativeKorean() {
        // Plank assist surfaces sag/pike as a soft "확인 필요" hint, never a hard verdict.
        assertThat(FeedbackText.modelFormLabel("hips_low")).isEqualTo("엉덩이 처짐 확인 필요 (모델)")
        assertThat(FeedbackText.modelFormLabel("hips_high")).isEqualTo("엉덩이 솟음 확인 필요 (모델)")
    }

    @Test
    fun modelFormLabel_unknownLabel_fallsBackToRawPlusModel() {
        // Forward-compatible: a re-exported model with a new class never crashes the UI.
        assertThat(FeedbackText.modelFormLabel("brand_new_class")).isEqualTo("brand_new_class (모델)")
    }

    // ---- label() : every FeedbackCode has a non-blank Korean label (no missing-branch crash) -----

    @Test
    fun label_isTotalOverEveryFeedbackCode() {
        // `when` over the enum is exhaustive; this pins that every code maps to a non-blank string
        // (a new code added without a label would fail to compile, keeping this honest).
        FeedbackCode.values().forEach { code ->
            assertThat(FeedbackText.label(code)).isNotEmpty()
        }
    }

    @Test
    fun label_mapsPlankAndExerciseCodesToKorean() {
        // Parity with the squat/push-up labels: the plank codes have their own Korean strings.
        assertThat(FeedbackText.label(FeedbackCode.PLANK_HIPS_LOW)).isEqualTo("엉덩이 처짐")
        assertThat(FeedbackText.label(FeedbackCode.PLANK_HIPS_HIGH)).isEqualTo("엉덩이 솟음")
        assertThat(FeedbackText.label(FeedbackCode.PLANK_ELBOW_MISALIGNED)).isEqualTo("팔꿈치 정렬 어긋남")
        assertThat(FeedbackText.label(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)).isEqualTo("스쿼트 깊이 부족")
        assertThat(FeedbackText.label(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)).isEqualTo("몸통 일직선 무너짐")
    }

    // ---- severity() : the HARD/SOFT/INFO buckets, incl. the plank split -------------------------

    @Test
    fun severity_bucketsCodesAndCoversEveryCode() {
        // Pins the exercise-specific split that drives overlay color & issue grouping. Hip faults are
        // HARD (they invalidate a hold); elbow-misaligned is a SOFT advisory; low-confidence is INFO.
        assertThat(FeedbackText.severity(FeedbackCode.PLANK_HIPS_LOW)).isEqualTo(FeedbackText.Severity.HARD)
        assertThat(FeedbackText.severity(FeedbackCode.PLANK_HIPS_HIGH)).isEqualTo(FeedbackText.Severity.HARD)
        assertThat(FeedbackText.severity(FeedbackCode.PUSH_UP_DEPTH_NOT_ENOUGH)).isEqualTo(FeedbackText.Severity.HARD)
        assertThat(FeedbackText.severity(FeedbackCode.PUSH_UP_BODY_LINE_BROKEN)).isEqualTo(FeedbackText.Severity.HARD)
        assertThat(FeedbackText.severity(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH)).isEqualTo(FeedbackText.Severity.HARD)

        assertThat(FeedbackText.severity(FeedbackCode.PLANK_ELBOW_MISALIGNED)).isEqualTo(FeedbackText.Severity.SOFT)
        assertThat(FeedbackText.severity(FeedbackCode.SQUAT_TORSO_LEAN)).isEqualTo(FeedbackText.Severity.SOFT)

        assertThat(FeedbackText.severity(FeedbackCode.LOW_CONFIDENCE)).isEqualTo(FeedbackText.Severity.INFO)

        // Total: every code resolves to a bucket (a new code must pick one or it won't compile).
        FeedbackCode.values().forEach { assertThat(FeedbackText.severity(it)).isNotNull() }
    }

    @Test
    fun issueTag_forPlankHardAndSoftCodes() {
        // The tag is a pure function of severity -> a plank hip fault tags 우선, the elbow advisory 확인.
        assertThat(FeedbackText.issueTag(FeedbackCode.PLANK_HIPS_LOW)).isEqualTo("우선")
        assertThat(FeedbackText.issueTag(FeedbackCode.PLANK_ELBOW_MISALIGNED)).isEqualTo("확인")
    }

    // ---- coachCue() : every code has a deterministic cue, incl. the plank ones -------------------

    @Test
    fun coachCue_isTotalAndNonBlankForEveryCode() {
        FeedbackCode.values().forEach { code ->
            assertThat(FeedbackText.coachCue(code)).isNotEmpty()
        }
    }

    @Test
    fun coachCue_plankCodesAreSpecificAndDistinct() {
        // Parity with squat/push-up cues: sag and pike get DIFFERENT, actionable Korean advice.
        val low = FeedbackText.coachCue(FeedbackCode.PLANK_HIPS_LOW)
        val high = FeedbackText.coachCue(FeedbackCode.PLANK_HIPS_HIGH)
        assertThat(low).isNotEqualTo(high)
        assertThat(low).contains("엉덩이")
        assertThat(high).contains("엉덩이")
    }

    // ---- invalidRepLine() : the "N세트 M회차: <사유>" failure line --------------------------------

    @Test
    fun invalidRepLine_rendersSetRepAndJoinedReasons() {
        // A shallow 2nd-rep squat renders "1세트 2회차: 스쿼트 깊이 부족".
        val rep = RepRecord(
            setNo = 1, repNo = 2, valid = false,
            failures = setOf(FeedbackCode.SQUAT_DEPTH_NOT_ENOUGH),
            startTimestampMs = 0L, endTimestampMs = 1000L,
        )
        assertThat(FeedbackText.invalidRepLine(rep)).isEqualTo("1세트 2회차: 스쿼트 깊이 부족")
    }

    @Test
    fun invalidRepLine_plankHoldFailure_usesPlankLabel() {
        // The plank hold record (repNo 1) with a sag renders with the plank label.
        val hold = RepRecord(
            setNo = 2, repNo = 1, valid = false,
            failures = setOf(FeedbackCode.PLANK_HIPS_LOW),
            startTimestampMs = 0L, endTimestampMs = 30_000L,
        )
        assertThat(FeedbackText.invalidRepLine(hold)).isEqualTo("2세트 1회차: 엉덩이 처짐")
    }

    @Test
    fun invalidRepLine_validRep_isJustTheLocatorPrefix() {
        val rep = RepRecord(
            setNo = 3, repNo = 4, valid = true, failures = emptySet(),
            startTimestampMs = 0L, endTimestampMs = 1L,
        )
        // Empty failures -> just "N세트 M회차" (callers only render this for invalid reps).
        assertThat(FeedbackText.invalidRepLine(rep)).isEqualTo("3세트 4회차")
    }
}
