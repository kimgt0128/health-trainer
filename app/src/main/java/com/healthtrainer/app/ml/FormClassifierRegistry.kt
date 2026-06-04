package com.healthtrainer.app.ml

import android.content.Context
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.features.ExerciseFeatureExtractor
import com.healthtrainer.core.features.SquatFeatureExtractor

/**
 * The extensibility seam (OCP) for the optional on-device form classifier.
 *
 * Maps an [ExerciseType] to its `(`:core` feature extractor, `.tflite` asset, class labels`)` bundle.
 * Only exercises with a trained model are registered; everything else returns `null`, which the
 * caller treats as "no assist signal — rules only".
 *
 * ## Adding a new exercise's model = ONE entry
 * 1. (ml track) train + export `<exercise>_form.tflite`, drop it in `assets/models/`.
 * 2. (here) add one `register(...)` line in [REGISTRY] with the `:core` extractor, asset name, and
 *    label list (in the model's output order).
 *
 * No call site, ViewModel, or pipeline code changes — they ask [forExercise] / [extractorFor] and
 * get `null` until a model exists. (Mirrors `:core`'s `ExerciseRegistry` philosophy on the `:app` ml
 * side.)
 */
object FormClassifierRegistry {

    /**
     * Static description of one exercise's classifier wiring. Kept separate from the live
     * [FormClassifier] so the registry table has no [Context] dependency and stays a pure data map.
     *
     * @property extractor the `:core` extractor that produces this model's input vector. REUSED from
     *                     `:core` — features are never recomputed in `:app`.
     * @property modelAsset path under `assets/` for the `.tflite` (e.g. `"models/squat_form.tflite"`).
     * @property labels    class names in the model's output order (index-aligned to its logits).
     */
    data class Spec(
        val extractor: ExerciseFeatureExtractor,
        val modelAsset: String,
        val labels: List<String>,
    )

    /**
     * The 6 squat-form classes the ml track's model emits, in output order. Three of these
     * (`KNEES_CAVING_IN`, `HEELS_OFF_GROUND`, `ASYMMETRIC_SQUAT`) are exactly the faults the
     * rule engine can't see from a sagittal view — the model's main value-add as an assist signal.
     */
    val SQUAT_LABELS: List<String> = listOf(
        "correct",
        "shallow_squat",
        "forward_lean",
        "knees_caving_in",
        "heels_off_ground",
        "asymmetric_squat",
    )

    /** Asset path of the squat form model (gitignored binary; absent until the ml track drops it). */
    const val SQUAT_MODEL_ASSET = "models/squat_form.tflite"

    /**
     * The registry table. ONE entry per exercise that has a model. Add a line here to extend.
     * Exercises absent from this map have no classifier (rules-only) — that is the default.
     */
    private val REGISTRY: Map<ExerciseType, Spec> = mapOf(
        ExerciseType.SQUAT to Spec(
            extractor = SquatFeatureExtractor(),
            modelAsset = SQUAT_MODEL_ASSET,
            labels = SQUAT_LABELS,
        ),
        // PUSH_UP / PLANK: no model yet -> not registered -> forExercise returns null (rules only).
    )

    /**
     * A live [FormClassifier] for [type], or `null` if no model is registered for it.
     *
     * A non-null result may still be **inert** (returns `null` predictions) when the `.tflite` asset
     * is missing — that is the graceful no-model path; [TfliteFormClassifier] never throws for it.
     * The classifier owns a native interpreter; the owner should [TfliteFormClassifier.close] it.
     */
    fun forExercise(type: ExerciseType, context: Context): FormClassifier? {
        val spec = REGISTRY[type] ?: return null
        return TfliteFormClassifier(
            context = context,
            modelAsset = spec.modelAsset,
            labels = spec.labels,
        )
    }

    /**
     * The `:core` feature extractor paired with [type]'s model, or `null` if none. The caller uses
     * this to build the model's input from a normalized frame — features are sourced from `:core`,
     * never recomputed in `:app`.
     */
    fun extractorFor(type: ExerciseType): ExerciseFeatureExtractor? = REGISTRY[type]?.extractor
}
