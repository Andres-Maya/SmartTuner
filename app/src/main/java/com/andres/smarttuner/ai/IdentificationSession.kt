package com.andres.smarttuner.ai

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Acumula audio de una escucha: agrupa los bloques en ventanas de [windowSeconds]
 * con salto de [hopSeconds], clasifica cada ventana con sonido y guarda las alturas detectadas.
 * [classify] se inyecta para poder probar la sesión sin el modelo.
 * [head] es la capa entrenada con grabaciones propias; si es `null` solo se usa YAMNet.
 */
class IdentificationSession(
    private val sampleRate: Int,
    private val classify: (FloatArray) -> YamnetScores,
    private val head: InstrumentHead? = null,
    private val fusion: InstrumentFusion = InstrumentFusion(),
    windowSeconds: Float = 1f,
    hopSeconds: Float = 0.5f,
    private val silenceRms: Float = 0.006f,
) {
    private val window = FloatArray((sampleRate * windowSeconds).toInt())
    private val hopSize = (sampleRate * hopSeconds).toInt()
    private var filled = 0
    private var samplesSinceLastWindow = 0
    private var acceptedSamples = 0L

    private val windowScores = mutableListOf<Map<String, Float>>()
    private val headScores = mutableListOf<Map<String, Float>>()
    private val pitchesMidi = mutableListOf<Float>()

    val analyzedWindows: Int get() = windowScores.size

    /** Segundos de audio recibidos; no depende de lo que tarde el modelo en procesarlos. */
    val acceptedSeconds: Float get() = acceptedSamples.toFloat() / sampleRate

    fun accept(samples: FloatArray, pitchMidi: Float?) {
        pitchMidi?.let(pitchesMidi::add)

        val count = samples.size
        acceptedSamples += count
        if (count >= window.size) {
            System.arraycopy(samples, count - window.size, window, 0, window.size)
        } else {
            System.arraycopy(window, count, window, 0, window.size - count)
            System.arraycopy(samples, 0, window, window.size - count, count)
        }
        filled = min(window.size, filled + count)
        samplesSinceLastWindow += count

        if (filled == window.size && samplesSinceLastWindow >= hopSize) {
            samplesSinceLastWindow = 0
            if (rms(window) >= silenceRms) {
                val scores = classify(window.copyOf())
                windowScores += scores.byLabel
                head?.takeIf { scores.byIndex.isNotEmpty() }?.let { headScores += it.probabilities(scores.byIndex) }
            }
        }
    }

    fun result(): IdentificationOutcome = fusion.fuse(windowScores, pitchesMidi, headScores)

    private fun rms(samples: FloatArray): Float {
        var energy = 0.0
        for (sample in samples) energy += sample * sample
        return sqrt(energy / samples.size).toFloat()
    }
}
