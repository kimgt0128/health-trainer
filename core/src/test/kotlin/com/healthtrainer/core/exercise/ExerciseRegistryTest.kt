package com.healthtrainer.core.exercise

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ExerciseRegistry] — the single registration point that makes adding an exercise an
 * open/closed change.
 *
 * The headline safety net is [registry_isExhaustiveOverExerciseType]: it FAILS the moment an
 * [ExerciseType] value is added without a matching [ExerciseDefinition], so a half-wired new
 * exercise can never ship green.
 */
class ExerciseRegistryTest {

    @Test
    fun ruleFor_squat_returnsSquatRule() {
        assertThat(ExerciseRegistry.ruleFor(ExerciseType.SQUAT)).isInstanceOf(SquatRule::class.java)
    }

    @Test
    fun ruleFor_pushUp_returnsPushUpRule() {
        assertThat(ExerciseRegistry.ruleFor(ExerciseType.PUSH_UP)).isInstanceOf(PushUpRule::class.java)
    }

    @Test
    fun ruleFor_plank_returnsPlankRule() {
        assertThat(ExerciseRegistry.ruleFor(ExerciseType.PLANK)).isInstanceOf(PlankRule::class.java)
    }

    @Test
    fun ruleFor_returnsFreshInstanceEachCall() {
        // A rule may carry per-session/per-set state in the future; callers must get their own copy,
        // not a shared singleton.
        val first = ExerciseRegistry.ruleFor(ExerciseType.SQUAT)
        val second = ExerciseRegistry.ruleFor(ExerciseType.SQUAT)
        assertThat(first).isNotSameInstanceAs(second)
    }

    @Test
    fun definition_returnsTheMatchingType() {
        val def = ExerciseRegistry.definition(ExerciseType.PLANK)
        assertThat(def.type).isEqualTo(ExerciseType.PLANK)
        assertThat(def.createRule()).isInstanceOf(PlankRule::class.java)
    }

    @Test
    fun definition_createRule_returnsFreshInstanceEachCall() {
        val def = ExerciseRegistry.definition(ExerciseType.SQUAT)
        assertThat(def.createRule()).isNotSameInstanceAs(def.createRule())
    }

    @Test
    fun all_containsOneDefinitionPerRegisteredType() {
        // No duplicate registrations: one definition per distinct type.
        val types = ExerciseRegistry.all.map { it.type }
        assertThat(types).containsNoDuplicates()
    }

    @Test
    fun registry_isExhaustiveOverExerciseType() {
        // THE extensibility safety net: every ExerciseType must be registered. Adding an enum value
        // without an ExerciseDefinition breaks this test (and CI), not production at runtime.
        assertThat(ExerciseRegistry.all.map { it.type }.toSet())
            .isEqualTo(ExerciseType.entries.toSet())
    }
}
