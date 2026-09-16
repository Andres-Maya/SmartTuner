package com.andres.smarttuner.ai

import com.andres.smarttuner.music.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class InstrumentHeadTest {

    private val head = InstrumentHead(
        labels = listOf("guitar", "cello"),
        weights = arrayOf(floatArrayOf(4f, 0f), floatArrayOf(0f, 4f)),
        bias = floatArrayOf(0f, 0f),
        logFeatures = false,
    )

    @Test
    fun probabilities_followTheStrongestFeature() {
        val guitar = head.probabilities(floatArrayOf(1f, 0f))
        assertTrue(guitar.getValue("guitar") > guitar.getValue("cello"))

        val cello = head.probabilities(floatArrayOf(0f, 1f))
        assertTrue(cello.getValue("cello") > cello.getValue("guitar"))
    }

    @Test
    fun probabilities_sumToOne() {
        val total = head.probabilities(floatArrayOf(0.3f, 0.7f)).values.sum()
        assertEquals(1f, total, 1e-5f)
    }

    @Test
    fun extraFeatures_areIgnored() {
        // La app envía 521 puntuaciones aunque el modelo se entrenara con menos columnas.
        val short = head.probabilities(floatArrayOf(1f, 0f))
        val long = head.probabilities(FloatArray(521).also { it[0] = 1f })
        assertTrue(abs(short.getValue("guitar") - long.getValue("guitar")) < 1e-6f)
    }

    @Test
    fun logTransform_matchesTraining() {
        val logHead = InstrumentHead(
            labels = listOf("guitar", "cello"),
            weights = arrayOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            bias = floatArrayOf(0f, 0f),
            logFeatures = true,
        )
        val probabilities = logHead.probabilities(floatArrayOf(0.9f, 0.01f))
        assertTrue(probabilities.getValue("guitar") > probabilities.getValue("cello"))
    }

    @Test
    fun normalization_isApplied() {
        val scaled = InstrumentHead(
            labels = listOf("guitar", "cello"),
            weights = arrayOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            bias = floatArrayOf(0f, 0f),
            logFeatures = false,
            mean = floatArrayOf(0.5f, 0.5f),
            scale = floatArrayOf(0.1f, 0.1f),
        )
        // (0.6 - 0.5) / 0.1 = +1 y (0.4 - 0.5) / 0.1 = -1 → softmax(1, -1).
        val probabilities = scaled.probabilities(floatArrayOf(0.6f, 0.4f))
        assertEquals(0.8808f, probabilities.getValue("guitar"), 0.001f)
    }

    @Test
    fun zeroScale_doesNotDivideByZero() {
        val degenerate = InstrumentHead(
            labels = listOf("guitar", "cello"),
            weights = arrayOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            bias = floatArrayOf(0f, 0f),
            logFeatures = false,
            mean = floatArrayOf(0.5f, 0.5f),
            scale = floatArrayOf(0f, 0f),
        )
        val probabilities = degenerate.probabilities(floatArrayOf(0.6f, 0.4f))
        assertTrue(probabilities.values.all { it.isFinite() })
    }

    @Test
    fun labelsMatchDatasetFolders() {
        assertEquals("guitar", Instrument.GUITAR.datasetLabel)
        assertEquals("cello", Instrument.CELLO.datasetLabel)
        assertEquals("other", Instrument.OTHER.datasetLabel)
    }
}
