package com.healthtrainer.app.ml

/**
 * One on-device form-classifier verdict for a single feature vector.
 *
 * @property label      the predicted class name (e.g. `"shallow_squat"`), from the labels the
 *                      classifier was constructed with — index-aligned to the model's output.
 * @property confidence the soft-max probability of [classIndex] in `[0,1]`.
 * @property classIndex argmax index into the model's output / the labels list.
 */
data class FormPrediction(
    val label: String,
    val confidence: Float,
    val classIndex: Int,
)

/**
 * An OPTIONAL on-device form classifier — a **secondary ASSIST signal**, never the source of truth.
 *
 * Per docs/model-training-plan the model only ever *adds* form hints; the rule engine
 * ([com.healthtrainer.core.exercise.ExerciseRule]) stays primary and the app must run fully without
 * any model. So this interface is deliberately nullable on every axis:
 *
 * - [classify] returns `null` when the classifier can't speak — the model artifact is missing/failed
 *   to load, or the features are unusable. A `null` means "defer to the rules", not "good form".
 * - The factory ([FormClassifierRegistry.forExercise]) returns `null` for exercises with no model.
 *
 * Implementations must be **inert, not fatal**, when their backing artifact is absent (see
 * [TfliteFormClassifier]); the caller treats a missing classifier and a `null` prediction identically.
 */
interface FormClassifier {

    /**
     * Run the model on a feature vector (produced by the matching `:core`
     * [com.healthtrainer.core.features.ExerciseFeatureExtractor]) and return the argmax verdict, or
     * `null` if the classifier is not loaded or the input can't be scored. NEVER throws for a missing
     * model — a degraded classifier simply returns `null` forever.
     */
    fun classify(features: FloatArray): FormPrediction?
}
