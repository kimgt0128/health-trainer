package com.healthtrainer.core.exercise

/**
 * One registered exercise: its [type] and a factory that produces a *fresh* [ExerciseRule].
 *
 * The factory (not a shared instance) keeps rule creation lazy and gives every caller its own rule,
 * so a rule is free to hold per-session/per-set state in the future without leaking it across
 * sessions.
 */
data class ExerciseDefinition(
    val type: ExerciseType,
    val createRule: () -> ExerciseRule,
)

/**
 * The single registration point for exercises — the one place a new exercise is wired in.
 *
 * Adding an exercise is an open/closed change: implement its [ExerciseRule] (declaring its
 * [ExerciseMode]) and add ONE [ExerciseDefinition] line below. Nothing else in [com.healthtrainer.core]
 * branches on a specific [ExerciseType]; the [com.healthtrainer.core.tracker.SetTracker] dispatches on
 * [ExerciseRule.mode]. `ExerciseRegistryTest.registry_isExhaustiveOverExerciseType` fails if a new
 * [ExerciseType] is added without a matching definition here, so a half-wired exercise can't ship.
 */
object ExerciseRegistry {

    /** All registered exercises. Register a NEW exercise by adding one line here. */
    val all: List<ExerciseDefinition> = listOf(
        ExerciseDefinition(ExerciseType.SQUAT, ::SquatRule),
        ExerciseDefinition(ExerciseType.PUSH_UP, ::PushUpRule),
        ExerciseDefinition(ExerciseType.PLANK, ::PlankRule),
    )

    private val byType: Map<ExerciseType, ExerciseDefinition> = all.associateBy { it.type }

    /** The definition for [type]. @throws IllegalArgumentException if the type isn't registered. */
    fun definition(type: ExerciseType): ExerciseDefinition =
        byType[type] ?: throw IllegalArgumentException("No ExerciseDefinition registered for $type")

    /** A fresh [ExerciseRule] for [type] (a new instance every call). */
    fun ruleFor(type: ExerciseType): ExerciseRule = definition(type).createRule()
}
