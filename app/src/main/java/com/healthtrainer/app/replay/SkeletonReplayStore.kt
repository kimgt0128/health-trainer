package com.healthtrainer.app.replay

import android.content.Context
import com.healthtrainer.core.exercise.ExerciseType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * kotlinx.serialization JSON persistence for replay frames, in app-private storage
 * ([Context.filesDir]). One file per session, named `"<exerciseType>-<startedAtMs>.json"`
 * (e.g. `SQUAT-1717000000000.json`), holding the session's [SkeletonReplayFrame] list.
 *
 * NOTE (requires device): all file IO here is unverified on an SDK-less machine. The
 * serialization shape ([SkeletonReplayFrame] is `@Serializable`) is the only inspectable guarantee.
 */
class SkeletonReplayStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val replayDir: File
        get() = File(context.filesDir, REPLAY_SUBDIR).apply { if (!exists()) mkdirs() }

    /** Persist [frames] for a session, returning the written file. Overwrites an existing file. */
    fun save(exerciseType: ExerciseType, startedAtMs: Long, frames: List<SkeletonReplayFrame>): File {
        val file = File(replayDir, fileName(exerciseType, startedAtMs))
        file.writeText(json.encodeToString(frames))
        return file
    }

    /** Load the replay frames from [file]; empty list on any read/parse failure. */
    fun load(file: File): List<SkeletonReplayFrame> =
        runCatching { json.decodeFromString<List<SkeletonReplayFrame>>(file.readText()) }
            .getOrElse { emptyList() }

    /** Load by session identity (convenience over [load]). */
    fun load(exerciseType: ExerciseType, startedAtMs: Long): List<SkeletonReplayFrame> =
        load(File(replayDir, fileName(exerciseType, startedAtMs)))

    /** All saved replay files, newest first by last-modified time. */
    fun list(): List<File> =
        replayDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private fun fileName(exerciseType: ExerciseType, startedAtMs: Long): String =
        "${exerciseType.name}-$startedAtMs.json"

    companion object {
        private const val REPLAY_SUBDIR = "replays"
    }
}
