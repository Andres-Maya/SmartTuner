package com.andres.smarttuner.ai

import com.andres.smarttuner.music.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentFusionTest {

    private val fusion = InstrumentFusion()

    /** Repite las notas para simular varios bloques con altura detectada. */
    private fun pitches(vararg midi: Int) = List(3) { midi.map(Int::toFloat) }.flatten()

    private fun windows(vararg scores: Pair<String, Float>) = List(4) { mapOf("Music" to 0.9f, *scores) }

    private fun bestOf(outcome: IdentificationOutcome): Instrument {
        assertTrue("Se esperaba un instrumento, llegó $outcome", outcome is IdentificationOutcome.Identified)
        return (outcome as IdentificationOutcome.Identified).best.instrument
    }

    @Test
    fun guitar_openStrings() {
        val outcome = fusion.fuse(
            windows("Guitar" to 0.6f, "Acoustic guitar" to 0.4f, "Plucked string instrument" to 0.5f, "Bass guitar" to 0.05f),
            pitches(40, 45, 50, 55, 59, 64),
        )
        assertEquals(Instrument.GUITAR, bestOf(outcome))
    }

    @Test
    fun bass_lowRegister_beatsGuitarLabel() {
        val outcome = fusion.fuse(
            windows("Bass guitar" to 0.5f, "Guitar" to 0.4f, "Plucked string instrument" to 0.5f),
            pitches(28, 33, 38, 43),
        )
        assertEquals(Instrument.BASS, bestOf(outcome))
    }

    @Test
    fun ukulele_reentrantTuning() {
        val outcome = fusion.fuse(
            windows("Ukulele" to 0.4f, "Guitar" to 0.3f, "Plucked string instrument" to 0.4f),
            pitches(67, 60, 64, 69),
        )
        assertEquals(Instrument.UKULELE, bestOf(outcome))
    }

    @Test
    fun violin_withViolinOnlyNotes() {
        val outcome = fusion.fuse(
            windows("Violin, fiddle" to 0.5f, "Bowed string instrument" to 0.4f, "Cello" to 0.05f),
            pitches(55, 62, 69, 76),
        )
        assertEquals(Instrument.VIOLIN, bestOf(outcome))
    }

    @Test
    fun viola_recognizedByCString_evenIfModelSaysViolin() {
        val outcome = fusion.fuse(
            windows("Violin, fiddle" to 0.5f, "Bowed string instrument" to 0.4f, "Cello" to 0.05f),
            pitches(48, 55, 62, 69),
        )
        assertEquals(Instrument.VIOLA, bestOf(outcome))
    }

    @Test
    fun cello_lowBowed() {
        val outcome = fusion.fuse(
            windows("Cello" to 0.5f, "Violin, fiddle" to 0.2f, "Bowed string instrument" to 0.4f),
            pitches(36, 43, 50, 57),
        )
        assertEquals(Instrument.CELLO, bestOf(outcome))
    }

    @Test
    fun piano_isOtherWithLabel() {
        val outcome = fusion.fuse(windows("Piano" to 0.7f, "Keyboard (musical)" to 0.6f), pitches(60, 64, 67))
        assertEquals(Instrument.OTHER, bestOf(outcome))
        assertEquals("Piano", (outcome as IdentificationOutcome.Identified).otherLabel)
    }

    @Test
    fun withoutPitch_timbreDecides() {
        val outcome = fusion.fuse(windows("Cello" to 0.6f, "Bowed string instrument" to 0.5f), emptyList())
        assertEquals(Instrument.CELLO, bestOf(outcome))
    }

    @Test
    fun speechOnly_isNoInstrument() {
        val outcome = fusion.fuse(List(4) { mapOf("Speech" to 0.95f) }, emptyList())
        assertEquals(IdentificationOutcome.NoInstrument, outcome)
    }

    @Test
    fun noWindows_isNoInstrument() {
        assertEquals(IdentificationOutcome.NoInstrument, fusion.fuse(emptyList(), pitches(69)))
    }

    @Test
    fun probabilitiesSumToOne() {
        val outcome = fusion.fuse(windows("Guitar" to 0.5f, "Violin, fiddle" to 0.3f, "Piano" to 0.2f), pitches(55, 62))
        val total = (outcome as IdentificationOutcome.Identified).candidates.sumOf { it.probability.toDouble() }
        assertEquals(1.0, total, 1e-4)
    }

    @Test
    fun impossibleNotes_penalizeInstrument() {
        val someOutOfRange = pitches(48, 55, 62, 69) // C3 no existe en el violín
        assertTrue(fusion.pitchLikelihood(Instrument.VIOLIN, someOutOfRange) < 0.6f)
        assertEquals(1f, fusion.pitchLikelihood(Instrument.VIOLA, someOutOfRange))
        assertEquals(2f, fusion.openStringBonus(Instrument.VIOLA, someOutOfRange))
    }
}
