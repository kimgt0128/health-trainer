package com.healthtrainer.app.ml

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.features.PushUpFeatureExtractor
import com.healthtrainer.core.features.SquatFeatureExtractor
import org.junit.Test

/**
 * Pure (Context-free) wiring checks for the assist-model registry. `forExercise` needs a [android.content.Context]
 * (device path, untested here); these cover the extractor mapping that the pipeline reads at build time.
 */
class FormClassifierRegistryTest {

    @Test
    fun pushUp_usesRepLevelExtractor_andHasNoFrameExtractor() {
        assertThat(FormClassifierRegistry.repExtractorFor(ExerciseType.PUSH_UP))
            .isInstanceOf(PushUpFeatureExtractor::class.java)
        assertThat(FormClassifierRegistry.extractorFor(ExerciseType.PUSH_UP)).isNull()
    }

    @Test
    fun squat_usesFrameLevelExtractor_andHasNoRepExtractor() {
        assertThat(FormClassifierRegistry.extractorFor(ExerciseType.SQUAT))
            .isInstanceOf(SquatFeatureExtractor::class.java)
        assertThat(FormClassifierRegistry.repExtractorFor(ExerciseType.SQUAT)).isNull()
    }

    /** No model registered -> both extractors null -> the pipeline runs rules-only for this exercise. */
    @Test
    fun plank_hasNoModel_soBothExtractorsAreNull() {
        assertThat(FormClassifierRegistry.extractorFor(ExerciseType.PLANK)).isNull()
        assertThat(FormClassifierRegistry.repExtractorFor(ExerciseType.PLANK)).isNull()
    }

    @Test
    fun pushUp_labels_areBinaryCorrectIncorrect_inOrder() {
        assertThat(FormClassifierRegistry.PUSH_UP_LABELS)
            .containsExactly("correct", "incorrect").inOrder()
    }
}
