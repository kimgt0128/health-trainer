package com.healthtrainer.app.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * A [FormClassifier] backed by an on-device TensorFlow Lite model loaded from
 * `assets/models/<modelAsset>` (e.g. `models/squat_form.tflite`).
 *
 * ## Optional by design — graceful without the artifact
 * The `.tflite` is produced by the ml track (Colab) and dropped into assets out-of-band; it is a
 * large binary and **gitignored**, so on a fresh checkout it is simply absent. This class makes that
 * a non-event:
 *
 * - The constructor attempts to memory-map the asset and build an [Interpreter]. **Any failure**
 *   (asset missing, corrupt model, unsupported op, OOM) is caught, logged once, and leaves the
 *   instance *inert* ([interpreter] stays null).
 * - An inert classifier's [classify] returns `null` forever, so the caller transparently falls back
 *   to the rule engine. The app never crashes for a missing/broken model.
 *
 * ## Inference contract
 * Input: a `[1, inputSize]` `float32` tensor — the `inputSize` features from the matching `:core`
 * [com.healthtrainer.core.features.ExerciseFeatureExtractor] (squat = 12), in that exact order.
 * Output: a `[1, labels.size]` `float32` tensor of per-class scores (probabilities or logits). We
 * argmax it; the index maps into [labels]. The reported `confidence` is the raw output value at the
 * argmax — already a probability if the exported model ends in soft-max, which the squat export does.
 *
 * `requires device + model artifact`: the actual TFLite native inference path can only run on a
 * device with the `.tflite` present. This machine builds the code but cannot exercise inference.
 *
 * Android-only (TFLite is an `androidx`-adjacent native lib); lives in `:app`, never `:core`.
 *
 * @param context    used to open the asset stream.
 * @param modelAsset path under `assets/` (e.g. `"models/squat_form.tflite"`).
 * @param labels     class names, index-aligned to the model's output vector.
 */
class TfliteFormClassifier(
    context: Context,
    private val modelAsset: String,
    private val labels: List<String>,
) : FormClassifier, Closeable {

    /** Null when the model failed to load — the inert state. Built once in `init`. */
    private val interpreter: Interpreter? = tryLoad(context, modelAsset)

    override fun classify(features: FloatArray): FormPrediction? {
        val itp = interpreter ?: return null // inert: no model loaded -> defer to rules.

        return try {
            // Output shape is [1, labels.size]; argmax -> class.
            val output = Array(1) { FloatArray(labels.size) }
            // TFLite accepts a [1, n] FloatArray-of-FloatArray directly for a float32 input tensor.
            val input = arrayOf(features)
            itp.run(input, output)

            val scores = output[0]
            var argmax = 0
            for (i in scores.indices) if (scores[i] > scores[argmax]) argmax = i

            // Defensive: if the model emitted more/fewer classes than we have labels, bail (treat as
            // "can't speak") rather than index out of range.
            val label = labels.getOrNull(argmax) ?: return null
            FormPrediction(label = label, confidence = scores[argmax], classIndex = argmax)
        } catch (t: Throwable) {
            // requires device: real inference unverified here. Never let a model glitch crash a rep.
            Log.w(TAG, "TFLite inference failed for '$modelAsset'; deferring to rules", t)
            null
        }
    }

    /** Release the native interpreter. Safe to call when inert (no-op). */
    override fun close() {
        interpreter?.close()
    }

    private companion object {
        const val TAG = "TfliteFormClassifier"

        /**
         * Memory-map the `.tflite` asset and build an [Interpreter]. Returns `null` — NOT throwing —
         * when the asset is absent or anything goes wrong, so the classifier degrades to inert.
         */
        fun tryLoad(context: Context, asset: String): Interpreter? = try {
            val model = mapAsset(context, asset)
            val options = Interpreter.Options().apply { numThreads = 1 }
            // requires device + model artifact: native delegate allocation is unverified on this box.
            Interpreter(model, options)
        } catch (t: Throwable) {
            // Missing asset is the common, expected case on a fresh checkout — log at info, not error.
            Log.i(TAG, "Form model '$asset' not loaded (absent or unreadable); classifier is inert", t)
            null
        }

        /** Memory-map an asset into a [MappedByteBuffer] for [Interpreter]. */
        fun mapAsset(context: Context, asset: String): MappedByteBuffer {
            context.assets.openFd(asset).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}
