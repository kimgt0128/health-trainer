package com.healthtrainer.app

import com.healthtrainer.app.ml.FormClassifier
import com.healthtrainer.app.ml.FormPrediction
import com.healthtrainer.app.pose.MediaPipeAdapter
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseMode
import com.healthtrainer.core.exercise.ExerciseRule
import com.healthtrainer.core.features.ExerciseFeatureExtractor
import com.healthtrainer.core.features.RepFeatureExtractor
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.LandmarkNormalizer
import com.healthtrainer.core.pose.PoseFrame
import com.healthtrainer.core.pose.PoseLandmark
import com.healthtrainer.core.tracker.RepRecord
import com.healthtrainer.core.tracker.SetTracker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * The per-frame processing pipeline, split out of the state holder (SRP). Turns one raw MediaPipe
 * result into a [FrameOutcome] by running the mvp-4 plan §3 path against `:core`:
 *
 * ```
 * worldFrame  = MediaPipeAdapter.toPoseFrame(result, ts)     // raw world coords
 * overlay     = MediaPipeAdapter.toOverlayLandmarks(result)  // image coords for the overlay
 * normFrame   = LandmarkNormalizer.normalize(worldFrame)     // :core, safe every frame
 * feedback    = rule.evaluate(normFrame)                     // -> live overlay color (pure)
 * closedRep   = setTracker.onFrame(normFrame)                // -> authoritative rep counting (set only)
 * repCount    = setTracker.currentRepCount                   // -> live rep counter
 * ```
 *
 * This class holds the active [rule] + [SetTracker] (they are rebuilt together when the exercise
 * changes or a session resets, via [reset]) but no Compose/UI state — [MainViewModel] folds the
 * returned [FrameOutcome] into its immutable `ExerciseUiState` and owns the replay buffer. Rep
 * validity stays entirely `:core`'s call (`aggregateRep` inside [SetTracker]); this pipeline never
 * ORs per-frame `hardFailures`.
 *
 * ## Optional form-model ASSIST (rules stay primary)
 * An optional on-device [FormClassifier] ([formClassifier], with its matching `:core`
 * [featureExtractor]) is consulted **only at rep boundaries** — when [process] closes a rep — so
 * inference is off the per-frame hot path. The model is a *secondary signal*: it can surface an extra
 * form hint ([FrameOutcome.formAssist]) but it NEVER touches rep counting or validity (still 100%
 * `:core`'s `aggregateRep`). When there is no classifier, the model is inert, features are null, or
 * the verdict is low-confidence / `correct`, the fusion yields `null` and the rule feedback stands
 * alone. The confidence gate + `correct`-suppression live in [fuseAssist] (one place).
 *
 * Pure mechanism + no Compose imports beyond the MediaPipe input type, so the wiring is inspectable.
 * NOTE (requires device): the MediaPipe adapter input is unverified on an SDK-less box, and the
 * TFLite inference path needs a device + the model artifact (see [TfliteFormClassifier]).
 *
 * @param formClassifier optional assist model; `null` = rules-only (the app's default, fully fine).
 * @param featureExtractor the `:core` FRAME-level extractor that builds [formClassifier]'s input from
 *                         the single closing frame (squat). Features are sourced from `:core`.
 * @param repFeatureExtractor the `:core` REP-level extractor (push-up): builds the model input from
 *                         the just-closed rep's per-frame feedbacks + duration, since the closing
 *                         frame alone is the uninformative TOP. Exactly one of [featureExtractor] /
 *                         [repFeatureExtractor] is wired per exercise (or neither = rules-only).
 */
class FramePipeline(
    rule: ExerciseRule,
    private val formClassifier: FormClassifier? = null,
    private val featureExtractor: ExerciseFeatureExtractor? = null,
    private val repFeatureExtractor: RepFeatureExtractor? = null,
) {

    var rule: ExerciseRule = rule
        private set

    var setTracker: SetTracker = SetTracker(rule)
        private set

    /** Last timestamp a HOLD-exercise assist classify ran, to throttle inference (see [classifyHoldAssist]). */
    private var lastHoldAssistAtMs: Long? = null

    /** The active rule's tracking mode is a hold (vs. rep-counted). Drives the UI hold indicator. */
    val isHold: Boolean
        get() = rule.mode == ExerciseMode.HOLD

    /** Rebuild rule + tracker for a (possibly new) [newRule] — exercise switch or session reset. */
    fun reset(newRule: ExerciseRule = rule) {
        rule = newRule
        setTracker = SetTracker(newRule)
        lastHoldAssistAtMs = null
    }

    fun startSet() = setTracker.startSet()

    fun endSet() = setTracker.endSet()

    /** Build the session from the tracker (caller supplies the wall-clock start). */
    fun build(startedAtMs: Long) = setTracker.build(startedAtMs)

    /**
     * Run one frame. When [countReps] is true (a set is active) the frame is fed to the tracker for
     * authoritative rep counting and any just-closed [RepRecord] is returned; otherwise only the pure
     * `rule.evaluate` runs (live overlay color), leaving the tracker untouched.
     */
    fun process(
        result: PoseLandmarkerResult,
        timestampMs: Long,
        countReps: Boolean,
    ): FrameOutcome {
        val worldFrame = MediaPipeAdapter.toPoseFrame(result, timestampMs)
        val overlay = MediaPipeAdapter.toOverlayLandmarks(result)
        val normFrame = LandmarkNormalizer.normalize(worldFrame)

        val feedback = rule.evaluate(normFrame)                 // live overlay color only (pure)
        val closedRep = if (countReps) setTracker.onFrame(normFrame) else null

        // Form-model ASSIST. Rep exercises: run ONLY when a rep just closed (off the hot path). Hold
        // exercises (plank close no reps): run a THROTTLED frame classify while the hold set is active.
        // Either way the model is a secondary signal — never rep counting/validity. Null unless it
        // passes the fusion gate.
        val formAssist = when {
            closedRep != null -> classifyAssist(normFrame, closedRep)
            countReps && isHold -> classifyHoldAssist(normFrame, timestampMs)
            else -> null
        }

        return FrameOutcome(
            feedback = feedback,
            overlayLandmarks = overlay,
            normalizedLandmarks = normFrame.landmarks,
            closedRep = closedRep,
            currentRepCount = setTracker.currentRepCount,
            formAssist = formAssist,
        )
    }

    /**
     * Run the optional form classifier on [normFrame] and apply the fusion gate. Returns the
     * surfaced [FormPrediction] only when it should become a UI assist hint, else `null` (defer to
     * rules). Cheap to call with no model: short-circuits before touching `:core`/TFLite.
     */
    private fun classifyAssist(normFrame: PoseFrame, closedRep: RepRecord): FormPrediction? {
        val classifier = formClassifier ?: return null
        val features: FloatArray = when {
            // Rep-level (push-up): score the just-closed rep's per-frame feedbacks (reused from :core,
            // no recompute) + its duration. The single closing frame is the TOP — uninformative here.
            repFeatureExtractor != null -> {
                val feedbacks = setTracker.lastClosedRepFrameFeedbacks ?: return null
                repFeatureExtractor.extract(
                    feedbacks,
                    closedRep.endTimestampMs - closedRep.startTimestampMs,
                )
            }
            // Frame-level (squat): score the single closing frame.
            featureExtractor != null -> featureExtractor.extract(normFrame)
            else -> null
        } ?: return null // null = model can't run on this rep/frame -> defer to rules.
        return fuseAssist(classifier.classify(features))
    }

    /**
     * HOLD-exercise assist (plank): a plank closes no reps, so instead of a rep-boundary trigger we
     * classify the live normalized frame at most once per [HOLD_ASSIST_INTERVAL_MS] (off the hot
     * path). Frame-level extractor only. Returns a hint past the fusion gate, else `null`.
     */
    private fun classifyHoldAssist(normFrame: PoseFrame, timestampMs: Long): FormPrediction? {
        val classifier = formClassifier ?: return null
        val extractor = featureExtractor ?: return null
        val last = lastHoldAssistAtMs
        if (last != null && timestampMs - last < HOLD_ASSIST_INTERVAL_MS) return null
        lastHoldAssistAtMs = timestampMs
        val features = extractor.extract(normFrame) ?: return null
        return fuseAssist(classifier.classify(features))
    }

    companion object {
        /**
         * Minimum model confidence to surface its verdict as an assist hint. Below this the model is
         * too unsure to override the rule feedback, so we stay silent and defer to the rules.
         */
        const val ASSIST_CONFIDENCE_THRESHOLD = 0.6f

        /** The model's "good form" class — suppressed (it adds no hint beyond what the rules say). */
        const val LABEL_CORRECT = "correct"

        /**
         * Throttle interval for HOLD-exercise (plank) assist inference. The hold runs for many frames,
         * so we classify at most this often instead of every frame.
         */
        const val HOLD_ASSIST_INTERVAL_MS = 750L

        /**
         * The fusion rule, isolated for clarity/testability: a [prediction] becomes an assist hint
         * ONLY when it exists, clears [ASSIST_CONFIDENCE_THRESHOLD], and is not the `correct` class.
         * Otherwise `null` — the rule-based feedback stands alone. This NEVER affects rep validity.
         */
        fun fuseAssist(prediction: FormPrediction?): FormPrediction? {
            val p = prediction ?: return null
            if (p.confidence < ASSIST_CONFIDENCE_THRESHOLD) return null
            if (p.label == LABEL_CORRECT) return null
            return p
        }
    }
}

/**
 * The product of one [FramePipeline.process] call — everything [MainViewModel] needs to update state
 * and capture a replay frame, with no further `:core` calls required.
 *
 * @property feedback            per-frame feedback for the overlay color + live message.
 * @property overlayLandmarks    image-normalized landmarks for the on-screen overlay.
 * @property normalizedLandmarks normalized landmarks for the captured replay frame.
 * @property closedRep           the rep just closed this frame, if any (its authoritative set/rep no).
 * @property currentRepCount     the tracker's in-progress rep count after this frame.
 * @property formAssist          OPTIONAL form-model hint for the just-closed rep, already past the
 *                               fusion gate (confidence ≥ threshold, not `correct`). `null` on every
 *                               non-rep-boundary frame and whenever the model defers to the rules.
 *                               ADVISORY ONLY — never affects rep counting or validity.
 */
data class FrameOutcome(
    val feedback: ExerciseFeedback,
    val overlayLandmarks: Map<LandmarkName, PoseLandmark>,
    val normalizedLandmarks: Map<LandmarkName, PoseLandmark>,
    val closedRep: RepRecord?,
    val currentRepCount: Int,
    val formAssist: FormPrediction? = null,
)
