package com.andres.smarttuner.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/** Bloque de audio recién capturado y la altura detectada en la ventana que termina en él. */
class AudioFrame(
    val samples: FloatArray,
    val pitch: PitchResult?,
)

/**
 * Captura audio del micrófono y emite un [AudioFrame] por bloque (~21 por segundo).
 * La altura es `null` cuando hay silencio o la señal no es periódica.
 * La grabación se detiene al cancelar la colección del Flow.
 */
class MicrophoneAudioSource(private val context: Context) {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(): Flow<AudioFrame> = flow {
        val detector = YinPitchDetector(SAMPLE_RATE, BUFFER_SIZE)
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val record = AudioRecord(audioSource(), SAMPLE_RATE, CHANNEL, ENCODING, max(minBuffer, BUFFER_SIZE * 2))
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("No se pudo inicializar el micrófono")
        }

        val window = FloatArray(BUFFER_SIZE)
        val chunk = FloatArray(HOP_SIZE)
        var filled = 0
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = record.read(chunk, 0, HOP_SIZE, AudioRecord.READ_BLOCKING)
                if (read < 0) error("Error leyendo el micrófono ($read)")
                if (read == 0) continue

                // Ventana deslizante: descarta lo más antiguo y añade el bloque nuevo al final.
                System.arraycopy(window, read, window, 0, BUFFER_SIZE - read)
                System.arraycopy(chunk, 0, window, BUFFER_SIZE - read, read)
                filled = min(BUFFER_SIZE, filled + read)
                val pitch = if (filled == BUFFER_SIZE) detector.detect(window) else null
                emit(AudioFrame(chunk.copyOf(read), pitch))
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    /** UNPROCESSED evita control automático de ganancia y supresión de ruido cuando existe. */
    private fun audioSource(): Int {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val supportsUnprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.toBoolean() == true
        return if (supportsUnprocessed) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val BUFFER_SIZE = 4096
        private const val HOP_SIZE = 2048
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    }
}
