package com.andres.smarttuner.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.io.Closeable

/**
 * YAMNet (AudioSet, 521 clases) ejecutado en el dispositivo con MediaPipe.
 * MediaPipe remuestrea a 16 kHz, así que se le puede pasar el audio del micrófono tal cual.
 * La carga del modelo tarda unos cientos de ms: crear fuera del hilo principal.
 */
class YamnetClassifier(context: Context) : Closeable {

    private val classifier: AudioClassifier = AudioClassifier.createFromOptions(
        context,
        AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .build(),
    )
    private var closed = false

    /** Puntuación 0..1 por etiqueta, promediada si MediaPipe divide el clip en varias ventanas. */
    @Synchronized
    fun classify(samples: FloatArray, sampleRate: Int): Map<String, Float> {
        check(!closed) { "El clasificador ya fue cerrado" }
        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(sampleRate.toFloat())
            .build()
        val audio = AudioData.create(format, samples.size)
        audio.load(samples)

        val startedAt = SystemClock.elapsedRealtime()
        val results = classifier.classify(audio).classificationResults()
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (results.isEmpty()) return emptyMap()

        val totals = HashMap<String, Float>()
        for (result in results) {
            result.classifications().firstOrNull()?.categories()?.forEach { category ->
                totals.merge(category.categoryName(), category.score()) { a, b -> a + b }
            }
        }
        val scores = totals.mapValues { it.value / results.size }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val top = scores.entries.sortedByDescending { it.value }.take(5).joinToString { "${it.key}=%.2f".format(it.value) }
            Log.d(TAG, "${elapsedMs}ms · $top")
        }
        return scores
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        classifier.close()
    }

    private companion object {
        const val TAG = "YamnetClassifier"
        const val MODEL_ASSET = "yamnet.tflite"
    }
}
