package com.healthtrainer.app

import com.healthtrainer.app.pose.MediaPipeAdapter
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseMode
import com.healthtrainer.core.exercise.ExerciseRule
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.LandmarkNormalizer
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
 * Pure mechanism + no Android/Compose imports beyond the MediaPipe input type, so the wiring is
 * inspectable. NOTE (requires device): the MediaPipe adapter input is unverified on an SDK-less box.
 */
class FramePipeline(rule: ExerciseRule) {

    var rule: ExerciseRule = rule
        private set

    var setTracker: SetTracker = SetTracker(rule)
        private set

    /** The active rule's tracking mode is a hold (vs. rep-counted). Drives the UI hold indicator. */
    val isHold: Boolean
        get() = rule.mode == ExerciseMode.HOLD

    /** Rebuild rule + tracker for a (possibly new) [newRule] — exercise switch or session reset. */
    fun reset(newRule: ExerciseRule = rule) {
        rule = newRule
        setTracker = SetTracker(newRule)
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

        return FrameOutcome(
            feedback = feedback,
            overlayLandmarks = overlay,
            normalizedLandmarks = normFrame.landmarks,
            closedRep = closedRep,
            currentRepCount = setTracker.currentRepCount,
        )
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
 */
data class FrameOutcome(
    val feedback: ExerciseFeedback,
    val overlayLandmarks: Map<LandmarkName, PoseLandmark>,
    val normalizedLandmarks: Map<LandmarkName, PoseLandmark>,
    val closedRep: RepRecord?,
    val currentRepCount: Int,
)
