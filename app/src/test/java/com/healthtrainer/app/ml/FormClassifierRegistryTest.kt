package com.healthtrainer.app.ml

import com.google.common.truth.Truth.assertThat
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.features.PlankFeatureExtractor
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

    /** Plank is a HOLD -> frame-level extractor (like squat), no rep extractor. */
    @Test
    fun plank_usesFrameLevelExtractor_andHasNoRepExtractor() {
        assertThat(FormClassifierRegistry.extractorFor(ExerciseType.PLANK))
            .isInstanceOf(PlankFeatureExtractor::class.java)
        assertThat(FormClassifierRegistry.repExtractorFor(ExerciseType.PLANK)).isNull()
    }

    @Test
    fun plank_labels_areThreePostureClasses_inOrder() {
        assertThat(FormClassifierRegistry.PLANK_LABELS)
            .containsExactly("hips_low", "correct", "hips_high").inOrder()
    }

    @Test
    fun pushUp_labels_areBinaryCorrectIncorrect_inOrder() {
        assertThat(FormClassifierRegistry.PUSH_UP_LABELS)
            .containsExactly("correct", "incorrect").inOrder()
    }
}
