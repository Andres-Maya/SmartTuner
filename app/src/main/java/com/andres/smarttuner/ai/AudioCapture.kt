package com.andres.smarttuner.ai

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Guarda en memoria el audio de una escucha y lo escribe como WAV PCM de 16 bits mono.
 * Solo se usa en compilaciones de desarrollo, para diagnosticar la IA y para ampliar el
 * dataset con audio captado exactamente como lo capta la app.
 */
class AudioCapture(
    val sampleRate: Int,
    private val maxSeconds: Int = 30,
) {
    private val chunks = mutableListOf<FloatArray>()
    private var totalSamples = 0

    val sampleCount: Int get() = totalSamples

    fun append(samples: FloatArray) {
        if (totalSamples + samples.size > sampleRate * maxSeconds) return
        chunks += samples.copyOf()
        totalSamples += samples.size
    }

    fun writeWav(file: File) {
        file.parentFile?.mkdirs()
        val dataBytes = totalSamples * BYTES_PER_SAMPLE
        BufferedOutputStream(FileOutputStream(file)).use { out ->
            out.write(header(dataBytes))
            val buffer = ByteBuffer.allocate(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            for (chunk in chunks) {
                for (sample in chunk) {
                    if (buffer.remaining() < BYTES_PER_SAMPLE) {
                        out.write(buffer.array(), 0, buffer.position())
                        buffer.clear()
                    }
                    val clamped = sample.coerceIn(-1f, 1f)
                    buffer.putShort((clamped * Short.MAX_VALUE).roundToInt().toShort())
                }
            }
            out.write(buffer.array(), 0, buffer.position())
        }
    }

    private fun header(dataBytes: Int): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(HEADER_BYTES - 8 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * BYTES_PER_SAMPLE)
            putShort(BYTES_PER_SAMPLE.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()

    private companion object {
        const val HEADER_BYTES = 44
        const val BYTES_PER_SAMPLE = 2
        const val CHUNK_BYTES = 8192
    }
}
