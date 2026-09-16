package com.andres.smarttuner.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.components.containers.ClassificationResult
import com.google.mediapipe.tasks.core.BaseOptions
import java.io.Closeable

/**
 * Puntuaciones de YAMNet para una ventana de audio.
 * [byLabel] se usa con las etiquetas de AudioSet; [byIndex] alimenta la capa entrenada propia.
 */
class YamnetScores(
    val byLabel: Map<String, Float>,
    val byIndex: FloatArray,
    /** Cuántas ventanas devolvió MediaPipe para este clip (diagnóstico). */
    val resultCount: Int = 1,
    /** Cuántas clases devolvió MediaPipe por ventana; deberían ser 521 (diagnóstico). */
    val categoryCount: Int = byIndex.size,
)

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

    /** Puntuación 0..1 por clase de la primera ventana de YAMNet del clip (ver [firstWindowScores]). */
    @Synchronized
    fun classify(samples: FloatArray, sampleRate: Int): YamnetScores {
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
        val scores = firstWindowScores(results)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val top = scores.byLabel.entries.sortedByDescending { it.value }.take(5)
                .joinToString { "${it.key}=%.2f".format(it.value) }
            Log.d(TAG, "${elapsedMs}ms · ${results.size} resultado(s) · $top")
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

private const val CLASS_COUNT = 521

/**
 * YAMNet analiza bloques de 0.975 s. Con un clip de 1 s, MediaPipe devuelve una segunda ventana
 * con los 25 ms sobrantes rellenos de ceros (casi silencio). Promediarla dividía todas las
 * puntuaciones a la mitad y añadía "Silence": la capa entrenada recibía datos que nunca vio y
 * respondía "guitarra" casi siempre. Se usa solo la primera ventana, igual que en el entrenamiento.
 */
internal fun firstWindowScores(results: List<ClassificationResult>): YamnetScores {
    val categories = results.firstOrNull()?.classifications()?.firstOrNull()?.categories().orEmpty()
    if (categories.isEmpty()) {
        return YamnetScores(emptyMap(), FloatArray(0), resultCount = results.size, categoryCount = 0)
    }
    val byIndex = FloatArray(CLASS_COUNT)
    val byLabel = HashMap<String, Float>(categories.size)
    for (category in categories) {
        byLabel[category.categoryName()] = category.score()
        if (category.index() in byIndex.indices) byIndex[category.index()] = category.score()
    }
    return YamnetScores(byLabel, byIndex, resultCount = results.size, categoryCount = categories.size)
}
