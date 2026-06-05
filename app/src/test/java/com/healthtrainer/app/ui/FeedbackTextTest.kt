package com.healthtrainer.app.ui

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.FeedbackCode
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
    fun modelFormLabel_unknownLabel_fallsBackToRawPlusModel() {
        // Forward-compatible: a re-exported model with a new class never crashes the UI.
        assertThat(FeedbackText.modelFormLabel("brand_new_class")).isEqualTo("brand_new_class (모델)")
    }
}
