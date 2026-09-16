package com.andres.smarttuner.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioCaptureTest {

    private fun readBytes(capture: AudioCapture): ByteBuffer {
        val file = File.createTempFile("capture", ".wav").apply { deleteOnExit() }
        capture.writeWav(file)
        return ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Test
    fun writesValidPcm16MonoHeader() {
        val capture = AudioCapture(sampleRate = 44_100)
        capture.append(FloatArray(2048) { 0.5f })
        capture.append(FloatArray(1000) { -0.5f })

        val bytes = readBytes(capture)
        fun tag(offset: Int) = String(ByteArray(4) { bytes.get(offset + it) }, Charsets.US_ASCII)

        assertEquals("RIFF", tag(0))
        assertEquals("WAVE", tag(8))
        assertEquals("fmt ", tag(12))
        assertEquals(1, bytes.getShort(20).toInt()) // PCM
        assertEquals(1, bytes.getShort(22).toInt()) // mono
        assertEquals(44_100, bytes.getInt(24))
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals("data", tag(36))
        assertEquals(3048 * 2, bytes.getInt(40))
        assertEquals(44 + 3048 * 2, bytes.limit())
    }

    @Test
    fun keepsSampleOrderAndClampsPeaks() {
        val capture = AudioCapture(sampleRate = 16_000)
        capture.append(floatArrayOf(0f, 1f, -1f, 2f))
        capture.append(floatArrayOf(0.5f))

        val bytes = readBytes(capture)
        val samples = List(5) { bytes.getShort(44 + it * 2).toInt() }
        assertEquals(listOf(0, 32767, -32767, 32767, 16384), samples)
    }

    @Test
    fun stopsAtMaximumLength() {
        val capture = AudioCapture(sampleRate = 100, maxSeconds = 1)
        repeat(5) { capture.append(FloatArray(40)) }
        assertEquals(80, capture.sampleCount)
    }
}
