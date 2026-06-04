package com.healthtrainer.app.ui

import com.healthtrainer.core.exercise.ExerciseType

/**
 * The single presentation catalog for exercise display names.
 *
 * Centralizing labels here is the `:app`-side half of the open/closed extensibility contract: adding
 * a new exercise is one `:core` registry line plus **one** entry in this `when`. No other `:app` code
 * (selector, stats, ViewModel control flow) branches on a specific [ExerciseType]; the selector
 * iterates [com.healthtrainer.core.exercise.ExerciseRegistry] / [ExerciseType.entries] and asks here
 * for each label.
 *
 * (String resources would also work; a single Kotlin file is the lighter choice for this skeleton and
 * keeps the catalog co-located with the other pure presentation mappings in this package.)
 */
object ExerciseUiText {

    /** Korean display name shown in the selector chip for [type]. */
    fun label(type: ExerciseType): String = when (type) {
        ExerciseType.SQUAT -> "스쿼트"
        ExerciseType.PUSH_UP -> "푸쉬업"
        ExerciseType.PLANK -> "플랭크"
    }
}
