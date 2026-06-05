package com.healthtrainer.core.exercise

import com.healthtrainer.core.pose.PoseFrame

/**
 * The exercises the rule engine understands. One [ExerciseRule] implementation per type.
 */
enum class ExerciseType { SQUAT, PUSH_UP, PLANK }

/**
 * How the tracker accounts for an exercise. Each [ExerciseRule] declares its own mode, so the
 * [com.healthtrainer.core.tracker.SetTracker] dispatches on behavior, not on a specific
 * [ExerciseType] literal.
 *
 * - [REP_COUNTED] — reps are segmented and counted (squat, push-up): TOP -> descent -> TOP.
 * - [HOLD]        — a single static hold judged once at the end of the set (plank).
 */
enum class ExerciseMode { REP_COUNTED, HOLD }

/**
 * Where a single frame sits within a movement.
 *
 * - [READY]  — a neutral/standing-by phase (reserved for the tracker; rules emit it sparingly).
 * - [TOP]    — top of a rep (standing for a squat, arms extended for a push-up).
 * - [BOTTOM] — bottom of a rep (deep squat, chest-down push-up).
 * - [HOLD]   — a static hold (plank).
 * - [UNKNOWN]— phase undecided: between top/bottom, or required landmarks not confident enough.
 */
enum class MovementPhase { READY, TOP, BOTTOM, HOLD, UNKNOWN }

/**
 * An explainable reason the rule engine surfaces. Rep-invalidating reasons are decided by
 * [ExerciseRule.aggregateRep]; soft warnings are advisory. [LOW_CONFIDENCE] is neither — it
 * signals the frame could not be evaluated.
 */
enum class FeedbackCode {
    LOW_CONFIDENCE,
    SQUAT_DEPTH_NOT_ENOUGH, SQUAT_TORSO_LEAN,
    PUSH_UP_DEPTH_NOT_ENOUGH, PUSH_UP_BODY_LINE_BROKEN,
    PLANK_HIPS_LOW, PLANK_HIPS_HIGH, PLANK_ELBOW_MISALIGNED,
}

/**
 * The result of evaluating one [PoseFrame].
 *
 * @property phase        the movement phase classified for this frame.
 * @property hardFailures per-frame hard-fault signals for the live overlay / replay highlighting.
 *                        Rep validity is decided SOLELY by [ExerciseRule.aggregateRep] — never by
 *                        OR-ing these across frames (that would bypass ratio gates).
 * @property softWarnings advisory signals (form warnings, low confidence) — never invalidate a rep.
 * @property metrics      raw measurements (angles, offsets) for debugging/replay, keyed by name.
 */
data class ExerciseFeedback(
    val phase: MovementPhase,
    val hardFailures: Set<FeedbackCode>,
    val softWarnings: Set<FeedbackCode>,
    val metrics: Map<String, Float>,
)

/**
 * Per-exercise form judgement, split into a per-frame pass and a rep-level pass.
 *
 * Contract:
 * - [evaluate] is **per-frame**: classify the phase, compute angle/offset metrics, and emit
 *   single-frame signals (e.g. a frame whose body line is broken) for the live overlay / replay.
 * - [aggregateRep] applies **rep-level policy** over a completed rep's per-frame feedbacks
 *   (min-angle, ratio of offending frames over the *visible* frames, ...) and returns the
 *   hard-failure codes for that rep. A plank "rep" is the whole hold.
 *
 * **Rep validity is determined SOLELY by [aggregateRep].** The tracker must not treat a single
 * per-frame [ExerciseFeedback.hardFailures] entry as rep-invalidating — that would bypass the ratio
 * gates (e.g. push-up body-line needs >30% of frames, not one). Per-frame hard failures exist for
 * live feedback and replay-frame highlighting only.
 *
 * **aggregateRep precondition:** callers pass the frames of one *completed* rep — for squat/push-up
 * a TOP -> descent -> TOP window, for plank the whole hold. Results over a non-rep window (e.g. an
 * all-TOP window) are not meaningful; rep segmentation is the tracker's responsibility.
 *
 * Every tunable threshold lives in the implementing rule's `companion object`.
 */
interface ExerciseRule {
    val exerciseType: ExerciseType

    /**
     * How the tracker accounts for this exercise (rep-counted vs. a single hold). The rule owns
     * this so the tracker never branches on a specific [ExerciseType] — adding a new hold-type
     * exercise just declares [ExerciseMode.HOLD].
     */
    val mode: ExerciseMode

    /** Evaluate a single (already normalized) frame. */
    fun evaluate(frame: PoseFrame): ExerciseFeedback

    /** Apply rep-level policy over a completed rep's per-frame feedbacks -> hard-failure codes. */
    fun aggregateRep(frameFeedbacks: List<ExerciseFeedback>): Set<FeedbackCode>

    /**
     * Whether this frame counts as "at the top" for rep segmentation (the tracker counts a rep on
     * TOP -> descent -> TOP). Default: [ExerciseFeedback.phase] == [MovementPhase.TOP].
     */
    fun isTop(feedback: ExerciseFeedback): Boolean = feedback.phase == MovementPhase.TOP

    /**
     * Whether the user has descended enough to count a rep *attempt*. This is intentionally LOOSER
     * than the good-form BOTTOM band so that shallow (bad-depth) reps are still counted and then
     * recorded as failures — see the "rep state machine과의 계약" section of the pose-rule-authoring
     * skill. Default: [ExerciseFeedback.phase] == [MovementPhase.BOTTOM].
     */
    fun isDescent(feedback: ExerciseFeedback): Boolean = feedback.phase == MovementPhase.BOTTOM
}
