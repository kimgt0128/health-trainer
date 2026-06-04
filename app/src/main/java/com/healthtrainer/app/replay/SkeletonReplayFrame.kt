package com.healthtrainer.app.replay

import com.healthtrainer.core.exercise.FeedbackCode
import com.healthtrainer.core.pose.LandmarkName
import com.healthtrainer.core.pose.PoseLandmark
import kotlinx.serialization.Serializable

/**
 * One persisted replay frame: a normalized pose snapshot plus where it sits in the session and which
 * per-frame hard failures fired (for red-highlighting that frame in [Skeleton3DViewer]).
 *
 * `:core`'s [PoseLandmark] / [LandmarkName] / [FeedbackCode] are plain data but live in the pure
 * `:core` module, which must not depend on kotlinx.serialization. Rather than annotate them from
 * `:app` (impossible without editing `:core`), this frame stores a flat [ReplayLandmark] surrogate
 * list and the failure codes as their string names — both fully `@Serializable` in `:app`. Mapping
 * back to `:core` types for rendering is done by [landmarkMap] / [failureCodes].
 *
 * The stored coordinates are the **normalized world** coords (post
 * [com.healthtrainer.core.pose.LandmarkNormalizer]) so the pseudo-3D projection is body-relative.
 *
 * NOTE (requires device): only the data shape is verified here; round-trip JSON IO is exercised by
 * [SkeletonReplayStore] and is unverified on an SDK-less machine.
 */
@Serializable
data class SkeletonReplayFrame(
    val timestampMs: Long,
    val setNo: Int,
    val repNo: Int,
    val landmarks: List<ReplayLandmark>,
    /** Per-frame hard-failure codes, stored as [FeedbackCode.name] strings. Highlight-only. */
    val failures: List<String>,
) {
    /** Rebuild the `:core` landmark map for rendering. */
    fun landmarkMap(): Map<LandmarkName, PoseLandmark> =
        landmarks.associate { it.name() to it.toCore() }

    /** Rebuild the `:core` failure code set (unknown names are dropped defensively). */
    fun failureCodes(): Set<FeedbackCode> =
        failures.mapNotNull { name -> runCatching { FeedbackCode.valueOf(name) }.getOrNull() }.toSet()

    companion object {
        /** Build a replay frame from a normalized `:core` landmark map + per-frame hard failures. */
        fun from(
            timestampMs: Long,
            setNo: Int,
            repNo: Int,
            landmarks: Map<LandmarkName, PoseLandmark>,
            failures: Set<FeedbackCode>,
        ): SkeletonReplayFrame = SkeletonReplayFrame(
            timestampMs = timestampMs,
            setNo = setNo,
            repNo = repNo,
            landmarks = landmarks.values.map { ReplayLandmark.from(it) },
            failures = failures.map { it.name },
        )
    }
}

/**
 * Flat, `@Serializable` surrogate for `:core`'s [PoseLandmark]. [name] is stored as the
 * [LandmarkName.name] string so `:core` needs no serialization annotations.
 */
@Serializable
data class ReplayLandmark(
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
) {
    fun name(): LandmarkName = LandmarkName.valueOf(name)

    fun toCore(): PoseLandmark = PoseLandmark(
        name = name(),
        x = x,
        y = y,
        z = z,
        visibility = visibility,
    )

    companion object {
        fun from(lm: PoseLandmark): ReplayLandmark = ReplayLandmark(
            name = lm.name.name,
            x = lm.x,
            y = lm.y,
            z = lm.z,
            visibility = lm.visibility,
        )
    }
}
