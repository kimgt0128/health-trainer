package com.healthtrainer.app.ui

import androidx.compose.ui.graphics.Color
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.ExerciseType
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.MovementPhase
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseLandmark

/**
 * The single immutable screen state the live training UI observes (UDF / MVVM). The ViewModel owns one
 * of these and replaces it wholesale via [copy] on every meaningful change; composables read it and
 * hoist events back up — they hold no business logic of their own.
 *
 * Everything the UI needs to *render* is either a stored field or a pure `val` derived here, so the
 * live-message / overlay-color decision lives in one inspectable place instead of inside a composable.
 *
 * @property selectedExercise the chosen exercise (the selector highlights it).
 * @property repCount          live rep count of the in-progress set (meaningless for a [isHold] exercise).
 * @property liveFeedback      latest per-frame feedback; `null` before the first frame is evaluated.
 * @property overlayLandmarks  image-normalized landmarks for the skeleton overlay (`x,y in [0,1]`).
 * @property isSetActive       whether a set is currently being recorded.
 * @property isHold            derived from the rule's [com.healthtrainer.core.exercise.ExerciseMode];
 *                             a hold exercise (plank) has no rep count and shows a hold indicator.
 *                             The UI reads THIS, never `selectedExercise == ExerciseType.PLANK`.
 */
data class ExerciseUiState(
    val selectedExercise: ExerciseType,
    val repCount: Int = 0,
    val liveFeedback: ExerciseFeedback? = null,
    val overlayLandmarks: Map<LandmarkName, PoseLandmark> = emptyMap(),
    val isSetActive: Boolean = false,
    val isHold: Boolean = false,
) {

    /** Overlay/skeleton tint for the current frame (green/yellow/red/gray). */
    val overlayColor: Color get() = SkeletonGraphics.overlayColor(liveFeedback)

    /** Whether the live frame has nothing usable (no feedback yet, or UNKNOWN phase). */
    val isLowConfidence: Boolean
        get() = liveFeedback == null || liveFeedback.phase == MovementPhase.UNKNOWN

    /** The big counter line: a hold indicator for plank-style holds, else the rep count. */
    val statsLine: String
        get() = if (isHold) "유지 중" else "반복 횟수: $repCount"

    /**
     * The single live message to show under the preview, by priority:
     * hard failure > soft warning (excluding low-confidence) > low-confidence > OK.
     * Mirrors the old inline `when` from `ExerciseScreen`, now owned by the state.
     */
    val liveMessage: String
        get() {
            val fb = liveFeedback ?: return "자세를 인식하는 중..."
            val softNonInfo = fb.softWarnings - FeedbackCode.LOW_CONFIDENCE
            return when {
                fb.hardFailures.isNotEmpty() ->
                    fb.hardFailures.joinToString("·") { FeedbackText.label(it) }
                softNonInfo.isNotEmpty() ->
                    softNonInfo.joinToString("·") { FeedbackText.label(it) }
                isLowConfidence -> FeedbackText.label(FeedbackCode.LOW_CONFIDENCE)
                else -> "좋은 자세입니다"
            }
        }
}
