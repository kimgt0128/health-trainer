package com.healthtrainer.app.ui

import androidx.compose.ui.graphics.Color
import com.healthtrainer.core.exercise.ExerciseFeedback
import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.exercise.MovementPhase
import com.healthtrainer.core.pose.LandmarkName

/**
 * Shared skeleton-drawing constants + color logic for [SkeletonOverlay] and
 * [com.healthtrainer.app.replay.Skeleton3DViewer]. Pure Compose graphics — no `:core` mutation, no
 * Android device dependency beyond `androidx.compose.ui.graphics.Color`.
 */
object SkeletonGraphics {

    /** Overlay/replay skeleton colors (semantic, per mvp-4 plan §4). */
    val GREEN = Color(0xFF2ECC71)
    val YELLOW = Color(0xFFF1C40F)
    val RED = Color(0xFFE74C3C)
    val GRAY = Color(0xFF9E9E9E)

    /**
     * Bone connections to draw, as [LandmarkName] pairs. Reused by the live overlay and the 3D
     * replay viewer so both render the same topology:
     * torso box (shoulders <-> hips), arms (shoulder->elbow->wrist), legs
     * (hip->knee->ankle->heel->foot_index), and a nose anchor to the shoulder line.
     */
    val BONES: List<Pair<LandmarkName, LandmarkName>> = listOf(
        // Torso box
        LandmarkName.LEFT_SHOULDER to LandmarkName.RIGHT_SHOULDER,
        LandmarkName.LEFT_HIP to LandmarkName.RIGHT_HIP,
        LandmarkName.LEFT_SHOULDER to LandmarkName.LEFT_HIP,
        LandmarkName.RIGHT_SHOULDER to LandmarkName.RIGHT_HIP,
        // Arms
        LandmarkName.LEFT_SHOULDER to LandmarkName.LEFT_ELBOW,
        LandmarkName.LEFT_ELBOW to LandmarkName.LEFT_WRIST,
        LandmarkName.RIGHT_SHOULDER to LandmarkName.RIGHT_ELBOW,
        LandmarkName.RIGHT_ELBOW to LandmarkName.RIGHT_WRIST,
        // Legs
        LandmarkName.LEFT_HIP to LandmarkName.LEFT_KNEE,
        LandmarkName.LEFT_KNEE to LandmarkName.LEFT_ANKLE,
        LandmarkName.LEFT_ANKLE to LandmarkName.LEFT_HEEL,
        LandmarkName.LEFT_HEEL to LandmarkName.LEFT_FOOT_INDEX,
        LandmarkName.RIGHT_HIP to LandmarkName.RIGHT_KNEE,
        LandmarkName.RIGHT_KNEE to LandmarkName.RIGHT_ANKLE,
        LandmarkName.RIGHT_ANKLE to LandmarkName.RIGHT_HEEL,
        LandmarkName.RIGHT_HEEL to LandmarkName.RIGHT_FOOT_INDEX,
        // Nose anchor (to mid-shoulder, drawn to each shoulder for simplicity)
        LandmarkName.NOSE to LandmarkName.LEFT_SHOULDER,
        LandmarkName.NOSE to LandmarkName.RIGHT_SHOULDER,
    )

    /**
     * Live overlay color from the **per-frame** [ExerciseFeedback] (mvp-4 plan §4):
     * - any [ExerciseFeedback.hardFailures] -> [RED]
     * - else any soft warning other than [FeedbackCode.LOW_CONFIDENCE] -> [YELLOW]
     * - else phase [MovementPhase.UNKNOWN] or LOW_CONFIDENCE present -> [GRAY]
     * - else -> [GREEN]
     *
     * This is the one place per-frame `hardFailures` is legitimately consumed: it colors the overlay,
     * it does NOT decide rep validity (that is `:core`'s [ExerciseRule.aggregateRep] via the tracker).
     */
    fun overlayColor(feedback: ExerciseFeedback?): Color {
        if (feedback == null) return GRAY
        val softNonInfo = feedback.softWarnings - FeedbackCode.LOW_CONFIDENCE
        return when {
            feedback.hardFailures.isNotEmpty() -> RED
            softNonInfo.isNotEmpty() -> YELLOW
            feedback.phase == MovementPhase.UNKNOWN ||
                FeedbackCode.LOW_CONFIDENCE in feedback.softWarnings -> GRAY
            else -> GREEN
        }
    }

    /** Replay frame color: red when this frame carried any hard failure, else green. */
    fun replayColor(failures: Set<FeedbackCode>): Color = if (failures.isEmpty()) GREEN else RED
}
