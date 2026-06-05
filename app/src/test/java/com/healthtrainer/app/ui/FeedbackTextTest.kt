package com.healthtrainer.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [FeedbackText] is deliberately Android-free, so its presentation mapping is plain-JVM testable. */
class FeedbackTextTest {

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
