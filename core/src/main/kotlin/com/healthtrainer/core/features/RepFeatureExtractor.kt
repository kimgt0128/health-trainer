package com.healthtrainer.core.features

import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseType

/**
 * Produces the fixed-length numeric feature vector for a **rep-level** form classifier — one that
 * judges a *completed rep*, not a single frame. This is the rep counterpart to the frame-level
 * [ExerciseFeatureExtractor]; together they are the two seams between :core and the ml track's models.
 *
 * **Frame-level vs rep-level.** [ExerciseFeatureExtractor.extract] takes one [com.healthtrainer.core.pose.PoseFrame]
 * and is the right seam when the model classifies an instant (e.g. squat form on the deepest frame).
 * A [RepFeatureExtractor] instead takes the rep's per-frame [ExerciseFeedback]s (the rule's own
 * metrics) plus the rep duration, and is the right seam when the verdict is about the *whole rep* —
 * the push-up assist model. Reusing the rule's already-computed metrics means features are built with
 * zero angle recomputation, so there is no train↔inference drift from a second math path.
 *
 * **Train↔inference contract.** [featureNames] is the ordered list of feature keys the model was
 * trained on; [extract] returns the values in that exact same order. The names + order are the
 * contract — they must match the ml track's push-up `feature_config` — and pinning them here makes a
 * contract change one reviewable edit rather than scattered magic indices.
 *
 * Pure :core — no Android / MediaPipe. The feedbacks come from the rule engine, not raw sensors.
 */
interface RepFeatureExtractor {

    /** Which exercise this extractor's feature vector is for. */
    val exerciseType: ExerciseType

    /**
     * The ordered feature names, identical in content and order to the model's `feature_config`.
     * `extract(...)!![i]` is the value for `featureNames()[i]`.
     */
    fun featureNames(): List<String>

    /**
     * The ordered feature vector for the rep described by [frameFeedbacks] (the rep's per-frame
     * feedbacks, in order) and [repDurationMs] (end − start, in milliseconds), or `null` if it can't
     * be computed — e.g. no frame carried the required joint metrics (every requested joint was
     * occluded). A `null` means "the model can't run on this rep", so the app defers to the rule
     * engine rather than feeding the model a fabricated input.
     */
    fun extract(frameFeedbacks: List<ExerciseFeedback>, repDurationMs: Long): FloatArray?
}
