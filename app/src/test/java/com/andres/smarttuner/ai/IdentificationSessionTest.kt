package com.andres.smarttuner.ai

import com.andres.smarttuner.music.Instrument
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class IdentificationSessionTest {

    private val sampleRate = 44_100
    private val chunkSize = 2048

    private fun toneChunk(index: Int, amplitude: Float) = FloatArray(chunkSize) { i ->
        val n = index * chunkSize + i
        (amplitude * sin(2.0 * PI * 110.0 * n / sampleRate)).toFloat()
    }

    @Test
    fun classifiesOverlappingWindows_andFusesResult() {
        var classifiedSamples = 0
        val session = IdentificationSession(sampleRate, classify = { window ->
            classifiedSamples = window.size
            mapOf("Guitar" to 0.7f, "Plucked string instrument" to 0.6f)
        })

        // 44 bloques ≈ 2 s: ventanas de 1 s cada 0.5 s → 3 ventanas.
        repeat(44) { session.accept(toneChunk(it, amplitude = 0.3f), pitchMidi = 45f) }

        assertEquals(3, session.analyzedWindows)
        assertEquals(sampleRate, classifiedSamples)
        val outcome = session.result() as IdentificationOutcome.Identified
        assertEquals(Instrument.GUITAR, outcome.best.instrument)
    }

    @Test
    fun silence_isNotClassified() {
        var calls = 0
        val session = IdentificationSession(sampleRate, classify = { calls++; emptyMap() })

        repeat(44) { session.accept(FloatArray(chunkSize), pitchMidi = null) }

        assertEquals(0, calls)
        assertEquals(IdentificationOutcome.NoInstrument, session.result())
    }
}
