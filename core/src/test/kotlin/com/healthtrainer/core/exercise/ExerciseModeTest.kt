package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Each [ExerciseRule] declares its own tracking [ExerciseMode] (the object knows its behavior).
 * The [SetTracker] dispatches on this instead of hardcoding any exercise type, so a new hold-type
 * exercise just declares [ExerciseMode.HOLD].
 */
class ExerciseModeTest {

    @Test
    fun squatRule_isRepCounted() {
        assertThat(SquatRule().mode).isEqualTo(ExerciseMode.REP_COUNTED)
    }

    @Test
    fun pushUpRule_isRepCounted() {
        assertThat(PushUpRule().mode).isEqualTo(ExerciseMode.REP_COUNTED)
    }

    @Test
    fun plankRule_isHold() {
        assertThat(PlankRule().mode).isEqualTo(ExerciseMode.HOLD)
    }
}
