package com.andres.smarttuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class YinPitchDetectorTest {

    private val sampleRate = 44_100
    private val bufferSize = 4096
    private val detector = YinPitchDetector(sampleRate, bufferSize)

    private fun tone(frequency: Double, harmonics: Boolean = false) = FloatArray(bufferSize) { i ->
        val t = 2.0 * PI * frequency * i / sampleRate
        var sample = 0.5 * sin(t)
        if (harmonics) sample += 0.35 * sin(2 * t) + 0.2 * sin(3 * t) + 0.1 * sin(4 * t)
        (sample * 0.5).toFloat()
    }

    private fun detect(frequency: Double, harmonics: Boolean = false): Float {
        val result = detector.detect(tone(frequency, harmonics))
        assertNotNull("No se detectó $frequency Hz", result)
        return result!!.frequency
    }

    @Test
    fun detectsA4() {
        assertEquals(440f, detect(440.0), 0.5f)
    }

    @Test
    fun detectsLowE_withHarmonics_withoutOctaveErrors() {
        assertEquals(82.41f, detect(82.41, harmonics = true), 0.3f)
    }

    @Test
    fun detectsBassLowE() {
        assertEquals(41.2f, detect(41.2, harmonics = true), 0.3f)
    }

    @Test
    fun detectsHighNotes() {
        assertEquals(1318.51f, detect(1318.51), 3f)
    }

    @Test
    fun silence_returnsNull() {
        assertNull(detector.detect(FloatArray(bufferSize)))
    }
}
