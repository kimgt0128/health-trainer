package com.healthtrainer.core.scoring

import com.healthtrainer.core.exercise.ExerciseType

/**
 * The single registration point for [ExerciseScorer]s — mirrors
 * [com.healthtrainer.core.exercise.ExerciseRegistry].
 *
 * Adding a scorer for a new exercise is an open/closed change: implement its [ExerciseScorer] and add
 * ONE line below. [forType] returns `null` for an unregistered type so [SessionSummarizer] can
 * degrade gracefully (valid-ratio fallback) rather than crash.
 */
object ScorerRegistry {

    /** All registered scorers. Register a NEW exercise's scorer by adding one line here. */
    private val all: List<ExerciseScorer> = listOf(
        SquatScorer(),
        PushUpScorer(),
        PlankScorer(),
    )

    private val byType: Map<ExerciseType, ExerciseScorer> = all.associateBy { it.exerciseType }

    /** The scorer for [type], or `null` if none is registered. */
    fun forType(type: ExerciseType): ExerciseScorer? = byType[type]
}
