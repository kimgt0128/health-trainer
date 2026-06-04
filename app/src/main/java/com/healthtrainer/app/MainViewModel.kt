package com.healthtrainer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtrainer.app.replay.SkeletonReplayFrame
import com.healthtrainer.app.ui.ExerciseUiState
import com.healthtrainer.core.exercise.ExerciseRegistry
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.tracker.ExerciseSession
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The `:app` orchestration hub (MVVM state holder). Exposes ONE immutable [ExerciseUiState] the live
 * screen observes and updates it wholesale via `copy(...)` (UDF). Per-frame `:core` mechanism is
 * delegated to [FramePipeline] (SRP); this class owns selection, set lifecycle, the replay buffer, and
 * the built session for navigation.
 *
 * **Open/closed:** the rule for an exercise comes from [ExerciseRegistry.ruleFor] and the "is this a
 * hold?" decision from the rule's `mode` (surfaced as [ExerciseUiState.isHold]) — there is NO
 * `when(exerciseType)` or `== ExerciseType.PLANK` branch here. Adding an exercise is a `:core` registry
 * line + one label in `ExerciseUiText`; this control flow does not change.
 *
 * Rep counting is delegated **entirely** to `:core`'s `SetTracker` (inside the pipeline): it returns
 * the rep it just closed and exposes the in-progress count, so there is NO parallel state machine in
 * `:app`, and validity is `:core`'s call (via `aggregateRep`) — `:app` never ORs per-frame
 * `hardFailures`. The pipeline's `rule.evaluate` result is used only for the live overlay color.
 *
 * Threading: MediaPipe delivers results off the main thread, so every state write hops to
 * [Dispatchers.Main] via [viewModelScope]. NOTE (requires device): this threading and the whole
 * camera/MediaPipe path are unverified on an SDK-less machine.
 */
class MainViewModel : ViewModel() {

    // ---- Per-frame pipeline (owns the active rule + :core SetTracker) ---------------------------

    private val pipeline = FramePipeline(ExerciseRegistry.ruleFor(ExerciseType.SQUAT))

    // ---- Single observable UI state ------------------------------------------------------------

    /**
     * The one immutable state the live [com.healthtrainer.app.ui.ExerciseScreen] observes. Backed by
     * snapshot state; only this ViewModel mutates it (always via `copy(...)` — UDF).
     */
    var uiState by mutableStateOf(
        ExerciseUiState(selectedExercise = ExerciseType.SQUAT, isHold = pipeline.isHold),
    )
        private set

    // ---- Session + replay (kept separate; they drive navigation, not the live frame) -----------

    /** Built session, set on [finishSession]; null until the session ends. Drives navigation. */
    var session by mutableStateOf<ExerciseSession?>(null)
        private set

    private val replayBuffer = mutableListOf<SkeletonReplayFrame>()

    /** Immutable snapshot of the captured replay frames (for ResultScreen / Skeleton3DViewer). */
    val replayFrames: List<SkeletonReplayFrame> get() = replayBuffer.toList()

    /** When the session began (epoch ms). `:app` supplies the clock; `:core` has none. */
    private var startedAtMs: Long = 0L

    /**
     * 1-based number of the in-progress set, mirrored locally so replay frames can be tagged before
     * any rep closes (SetTracker exposes setNo only via a closed RepRecord). Incremented in
     * [startSet], matching SetTracker's own per-set increment.
     */
    private var liveSetNo = 0

    // ---- Selection ----------------------------------------------------------------------------

    /**
     * Switch exercise. Resets the pipeline (rule + tracker) and live state; only allowed while no set
     * is active so we never swap rules mid-rep. Rule resolution is registry-driven — no type switch.
     */
    fun selectExercise(type: ExerciseType) {
        if (uiState.isSetActive || type == uiState.selectedExercise) return
        pipeline.reset(ExerciseRegistry.ruleFor(type))
        uiState = ExerciseUiState(selectedExercise = type, isHold = pipeline.isHold)
    }

    // ---- Set lifecycle (delegates straight to the pipeline / :core SetTracker) -----------------

    fun startSet() {
        if (session != null) return // a finished session must be cleared before a new one
        if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
        pipeline.startSet()
        liveSetNo += 1
        uiState = uiState.copy(isSetActive = true, repCount = 0)
    }

    fun endSet() {
        if (!uiState.isSetActive) return
        pipeline.endSet()
        uiState = uiState.copy(isSetActive = false, repCount = 0)
    }

    /**
     * Close any open set, build the [ExerciseSession] from the tracker, and expose it (+ the replay
     * buffer) for navigation to the result screen. Replay persistence is left to the caller (it needs
     * a [android.content.Context] -> [com.healthtrainer.app.replay.SkeletonReplayStore]).
     */
    fun finishSession(): ExerciseSession {
        if (uiState.isSetActive) endSet()
        val built = pipeline.build(startedAtMs)
        session = built
        return built
    }

    /** Discard the finished session and reset for a fresh one (e.g. "back" from ResultScreen). */
    fun resetSession() {
        session = null
        startedAtMs = 0L
        liveSetNo = 0
        replayBuffer.clear()
        pipeline.reset() // same rule, fresh tracker
        uiState = ExerciseUiState(
            selectedExercise = uiState.selectedExercise,
            isHold = pipeline.isHold,
        )
    }

    // ---- Per-frame entry point (called from the MediaPipe result listener, off-main) -----------

    /**
     * Handle one MediaPipe result. [timestampMs] is the frame timestamp echoed by MediaPipe. Delegates
     * the §3 pipeline to [FramePipeline], captures a replay frame while a set is active, then publishes
     * the new immutable state on the main dispatcher.
     */
    fun onPoseResult(result: PoseLandmarkerResult, timestampMs: Long) {
        val active = uiState.isSetActive
        val outcome = pipeline.process(result, timestampMs, countReps = active)

        if (active) {
            // setNo: prefer the just-closed rep's authoritative value, else the live set counter.
            // repNo: the rep this frame belongs to — the one just closed, or the next in-progress one.
            val setNoForFrame = outcome.closedRep?.setNo ?: liveSetNo
            val repNoForFrame = outcome.closedRep?.repNo ?: (outcome.currentRepCount + 1)
            replayBuffer += SkeletonReplayFrame.from(
                timestampMs = timestampMs,
                setNo = setNoForFrame,
                repNo = repNoForFrame,
                landmarks = outcome.normalizedLandmarks,
                failures = outcome.feedback.hardFailures,        // per-frame, highlight-only
            )
        }

        // MediaPipe callback is off-main; publish Compose state on the main dispatcher.
        viewModelScope.launch(Dispatchers.Main) {
            uiState = uiState.copy(
                liveFeedback = outcome.feedback,
                overlayLandmarks = outcome.overlayLandmarks,
                repCount = outcome.currentRepCount,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        replayBuffer.clear()
    }
}
