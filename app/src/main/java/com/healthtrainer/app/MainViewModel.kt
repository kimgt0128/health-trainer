package com.healthtrainer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrainer.app.pose.MediaPipeAdapter
import com.healthtrainer.app.replay.SkeletonReplayFrame
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseRule
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.MovementPhase
import com.healthtrainer.core.exercise.PlankRule
import com.healthtrainer.core.exercise.PushUpRule
import com.healthtrainer.core.exercise.SquatRule
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.LandmarkNormalizer
import com.healthtrainer.core.pose.PoseLandmark
import com.healthtrainer.core.tracker.ExerciseSession
import com.healthtrainer.core.tracker.RepRecord
import com.healthtrainer.core.tracker.SetTracker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The `:app` orchestration hub. Owns the selected [ExerciseType], its `:core` [ExerciseRule] and
 * [SetTracker], the live Compose state the UI observes, and the replay-frame buffer.
 *
 * Per-frame path ([onPoseResult]) wires MediaPipe -> `:core` exactly as the mvp-4 plan §3 specifies:
 * ```
 * worldFrame  = MediaPipeAdapter.toPoseFrame(result, ts)     // raw world coords
 * overlay     = MediaPipeAdapter.toOverlayLandmarks(result)  // image coords for the overlay
 * normFrame   = LandmarkNormalizer.normalize(worldFrame)     // :core, safe every frame
 * feedback    = rule.evaluate(normFrame)                     // -> live overlay color
 * closedRep   = setTracker.onFrame(normFrame)                // -> authoritative rep counting
 * repCount    = setTracker.currentRepCount                   // -> live rep counter
 * ```
 *
 * Rep counting is delegated **entirely** to `:core`'s [SetTracker]: `onFrame` returns the rep it just
 * closed and `currentRepCount` exposes the in-progress count, so there is NO parallel state machine
 * in `:app`. Validity is `:core`'s call (via `aggregateRep`); `:app` never ORs per-frame
 * `hardFailures`. The separate `rule.evaluate` call is only for the live overlay color — pure, no
 * effect on counting.
 *
 * Threading: MediaPipe delivers results off the main thread, so every Compose-state write hops to
 * [Dispatchers.Main] via [viewModelScope]. NOTE (requires device): this threading and the whole
 * camera/MediaPipe path are unverified on an SDK-less machine.
 */
class MainViewModel : ViewModel() {

    // ---- Selection + tracker (rebuilt when the exercise changes) -------------------------------

    var selectedExercise by mutableStateOf(ExerciseType.SQUAT)
        private set

    private var rule: ExerciseRule = ruleFor(selectedExercise)
    private var setTracker: SetTracker = SetTracker(rule)

    // ---- Live Compose state observed by ExerciseScreen -----------------------------------------

    /** Latest per-frame feedback (drives overlay color + the live feedback text). */
    var liveFeedback by mutableStateOf<ExerciseFeedback?>(null)
        private set

    /** Image-normalized landmarks for the on-screen overlay (`x,y in [0,1]`). */
    var overlayLandmarks by mutableStateOf(emptyMap<LandmarkName, PoseLandmark>())
        private set

    /** Live rep count of the in-progress set, straight from [SetTracker.currentRepCount]. */
    var repCount by mutableStateOf(0)
        private set

    /** Whether a set is currently being recorded (gates frame counting + replay capture). */
    var isSetActive by mutableStateOf(false)
        private set

    /** Built session, set on [finishSession]; null until the session ends. Drives navigation. */
    var session by mutableStateOf<ExerciseSession?>(null)
        private set

    // ---- Replay capture ------------------------------------------------------------------------

    private val replayBuffer = mutableListOf<SkeletonReplayFrame>()

    /** Immutable snapshot of the captured replay frames (for ResultScreen / Skeleton3DViewer). */
    val replayFrames: List<SkeletonReplayFrame> get() = replayBuffer.toList()

    /** When the session began (epoch ms). `:app` supplies the clock; `:core` has none. */
    private var startedAtMs: Long = 0L

    /**
     * 1-based number of the in-progress set, mirrored locally so replay frames can be tagged before
     * any rep closes (SetTracker exposes setNo only via a closed [RepRecord]). Incremented in
     * [startSet], matching SetTracker's own per-set increment.
     */
    private var liveSetNo = 0

    // ---- Selection ----------------------------------------------------------------------------

    /**
     * Switch exercise. Resets the tracker and live state; only allowed while no set is active so we
     * never swap rules mid-rep.
     */
    fun selectExercise(type: ExerciseType) {
        if (isSetActive || type == selectedExercise) return
        selectedExercise = type
        rule = ruleFor(type)
        setTracker = SetTracker(rule)
        resetLiveState()
    }

    // ---- Set lifecycle (delegates straight to :core SetTracker) --------------------------------

    fun startSet() {
        if (session != null) return // a finished session must be cleared before a new one
        if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
        setTracker.startSet()
        liveSetNo += 1
        isSetActive = true
        repCount = 0
    }

    fun endSet() {
        if (!isSetActive) return
        setTracker.endSet()
        isSetActive = false
        repCount = 0
    }

    /**
     * Close any open set, build the [ExerciseSession] from the tracker, and expose it (+ the replay
     * buffer) for navigation to the result screen. Replay persistence is left to the caller (it
     * needs a [android.content.Context] -> [com.healthtrainer.app.replay.SkeletonReplayStore]).
     */
    fun finishSession(): ExerciseSession {
        if (isSetActive) endSet()
        val built = setTracker.build(startedAtMs)
        session = built
        return built
    }

    /** Discard the finished session and reset for a fresh one (e.g. "back" from ResultScreen). */
    fun resetSession() {
        session = null
        startedAtMs = 0L
        liveSetNo = 0
        replayBuffer.clear()
        setTracker = SetTracker(rule)
        resetLiveState()
    }

    // ---- Per-frame entry point (called from the MediaPipe result listener, off-main) -----------

    /**
     * Handle one MediaPipe result. [timestampMs] is the frame timestamp echoed by MediaPipe. Runs the
     * plan §3 pipeline, then publishes Compose state on the main dispatcher.
     */
    fun onPoseResult(result: PoseLandmarkerResult, timestampMs: Long) {
        val worldFrame = MediaPipeAdapter.toPoseFrame(result, timestampMs)
        val overlay = MediaPipeAdapter.toOverlayLandmarks(result)
        val normFrame = LandmarkNormalizer.normalize(worldFrame)

        val feedback = rule.evaluate(normFrame)              // live overlay color only (pure)

        if (isSetActive) {
            val closedRep: RepRecord? = setTracker.onFrame(normFrame)  // authoritative rep counting
            // setNo: prefer the just-closed rep's authoritative value, else the live set counter.
            // repNo: the rep this frame belongs to — the one just closed, or the next in-progress one.
            val setNoForFrame = closedRep?.setNo ?: liveSetNo
            val repNoForFrame = closedRep?.repNo ?: (setTracker.currentRepCount + 1)
            replayBuffer += SkeletonReplayFrame.from(
                timestampMs = timestampMs,
                setNo = setNoForFrame,
                repNo = repNoForFrame,
                landmarks = normFrame.landmarks,
                failures = feedback.hardFailures,            // per-frame, highlight-only
            )
        }

        val liveCount = setTracker.currentRepCount

        // MediaPipe callback is off-main; publish Compose state on the main dispatcher.
        viewModelScope.launch(Dispatchers.Main) {
            liveFeedback = feedback
            overlayLandmarks = overlay
            repCount = liveCount
        }
    }

    /** Whether the current live frame has nothing usable (UNKNOWN phase / low confidence). */
    fun isLowConfidence(feedback: ExerciseFeedback?): Boolean =
        feedback == null || feedback.phase == MovementPhase.UNKNOWN

    override fun onCleared() {
        super.onCleared()
        replayBuffer.clear()
    }

    // ---- Internals ----------------------------------------------------------------------------

    private fun resetLiveState() {
        liveFeedback = null
        overlayLandmarks = emptyMap()
        repCount = 0
    }

    private fun ruleFor(type: ExerciseType): ExerciseRule = when (type) {
        ExerciseType.SQUAT -> SquatRule()
        ExerciseType.PUSH_UP -> PushUpRule()
        ExerciseType.PLANK -> PlankRule()
    }
}
