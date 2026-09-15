package com.andres.smarttuner.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class PitchResult(
    val frequency: Float,
    /** 0..1, qué tan periódica es la señal (1 = tono puro). */
    val probability: Float,
    val rms: Float,
)

/**
 * Detector de frecuencia fundamental basado en el algoritmo YIN
 * (de Cheveigné & Kawahara, 2002). Robusto frente a armónicos, ideal para instrumentos.
 *
 * No es thread-safe: reutiliza buffers internos entre llamadas.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val bufferSize: Int,
    minFrequency: Float = 40f,
    maxFrequency: Float = 2000f,
    private val threshold: Float = 0.12f,
    private val silenceRms: Float = 0.006f,
) {
    private val minTau = max(2, (sampleRate / maxFrequency).toInt())
    private val maxTau = (sampleRate / minFrequency).toInt()
    private val window = bufferSize - maxTau - 1
    private val yin = FloatArray(maxTau + 2)

    init {
        require(window > maxTau) {
            "bufferSize=$bufferSize es muy pequeño para detectar $minFrequency Hz a $sampleRate Hz"
        }
    }

    fun detect(samples: FloatArray): PitchResult? {
        require(samples.size >= bufferSize)

        var energy = 0.0
        for (i in 0 until bufferSize) energy += samples[i] * samples[i]
        val rms = sqrt(energy / bufferSize).toFloat()
        if (rms < silenceRms) return null

        // 1. Función diferencia.
        for (tau in 1..maxTau + 1) {
            var sum = 0f
            for (i in 0 until window) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
            }
            yin[tau] = sum
        }

        // 2. Diferencia normalizada por la media acumulada.
        yin[0] = 1f
        var runningSum = 0f
        for (tau in 1..maxTau + 1) {
            runningSum += yin[tau]
            yin[tau] = if (runningSum > 0f) yin[tau] * tau / runningSum else 1f
        }

        // 3. Primer mínimo por debajo del umbral.
        var tauEstimate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (yin[tau] < threshold) {
                while (tau + 1 <= maxTau && yin[tau + 1] < yin[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate < 0) return null

        // 4. Interpolación parabólica para precisión sub-muestra.
        val refinedTau = parabolicInterpolation(tauEstimate)
        return PitchResult(
            frequency = sampleRate / refinedTau,
            probability = 1f - yin[tauEstimate],
            rms = rms,
        )
    }

    private fun parabolicInterpolation(tau: Int): Float {
        val s0 = yin[tau - 1]
        val s1 = yin[tau]
        val s2 = yin[tau + 1]
        val denominator = s0 - 2f * s1 + s2
        return if (abs(denominator) < 1e-9f) tau.toFloat() else tau + (s0 - s2) / (2f * denominator)
    }
}
