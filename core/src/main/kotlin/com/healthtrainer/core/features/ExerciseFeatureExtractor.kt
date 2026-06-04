package com.healthtrainer.core.features

import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.pose.PoseFrame

/**
 * Produces the fixed-length numeric feature vector that a trained per-exercise form classifier
 * consumes. This is the generic seam between :core pose data and the ml track's models — one
 * implementation per exercise (see [SquatFeatureExtractor]).
 *
 * **Train↔inference contract.** [featureNames] is the ordered list of feature keys the model was
 * trained on; [extract] returns the values in that exact same order. The names + order are the
 * contract (they must match the ml track's `feature_config.json`); keeping them on the rule is how
 * we make drift a single, reviewable point rather than a scatter of magic indices.
 *
 * Pure :core — no Android / MediaPipe. The frame passed to [extract] is assumed already normalized
 * (the :app adapter normalizes before calling).
 */
interface ExerciseFeatureExtractor {

    /** Which exercise this extractor's feature vector is for. */
    val exerciseType: ExerciseType

    /**
     * The ordered feature names, identical in content and order to the model's `feature_config`.
     * `extract(frame)!![i]` is the value for `featureNames()[i]`.
     */
    fun featureNames(): List<String>

    /**
     * The ordered feature vector for [frame], or `null` if it can't be computed — e.g. a required
     * landmark is below the visibility gate. A `null` means "the model can't run on this frame", so
     * the app defers to the rule engine rather than feeding the model a fabricated input.
     */
    fun extract(frame: PoseFrame): FloatArray?
}
