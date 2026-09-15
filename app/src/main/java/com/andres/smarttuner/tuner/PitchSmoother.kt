package com.andres.smarttuner.tuner

import kotlin.math.abs

/**
 * Suaviza lecturas de altura (en semitonos MIDI continuos):
 * una mediana elimina saltos de octava esporádicos y un filtro exponencial
 * estabiliza la aguja. Si la nota cambia de verdad, salta sin arrastrar.
 */
class PitchSmoother(
    private val medianSize: Int = 5,
    private val alpha: Float = 0.3f,
    private val jumpSemitones: Float = 0.7f,
) {
    private val history = ArrayDeque<Float>()
    private var smoothed: Float? = null

    fun add(midi: Float): Float {
        history.addLast(midi)
        if (history.size > medianSize) history.removeFirst()
        val median = history.sorted()[history.size / 2]

        val current = smoothed
        val next = if (current == null || abs(median - current) > jumpSemitones) {
            median
        } else {
            current + alpha * (median - current)
        }
        smoothed = next
        return next
    }

    fun reset() {
        history.clear()
        smoothed = null
    }
}
