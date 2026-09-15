package com.andres.smarttuner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentTest {

    private fun labels(instrument: Instrument) = instrument.strings.map { it.note().label }

    @Test
    fun standardTunings() {
        assertEquals(listOf("E4", "B3", "G3", "D3", "A2", "E2"), labels(Instrument.GUITAR))
        assertEquals(listOf("G2", "D2", "A1", "E1"), labels(Instrument.BASS))
        assertEquals(listOf("E5", "A4", "D4", "G3"), labels(Instrument.VIOLIN))
        assertEquals(listOf("A4", "D4", "G3", "C3"), labels(Instrument.VIOLA))
        assertEquals(listOf("A3", "D3", "G2", "C2"), labels(Instrument.CELLO))
        assertEquals(listOf("A4", "E4", "C4", "G4"), labels(Instrument.UKULELE))
    }

    @Test
    fun stringNumbersStartAtOne() {
        assertEquals((1..6).toList(), Instrument.GUITAR.strings.map { it.number })
    }

    @Test
    fun openStringFrequencies() {
        assertEquals(82.41f, Instrument.GUITAR.strings.last().frequency(), 0.01f)
        assertEquals(41.20f, Instrument.BASS.strings.last().frequency(), 0.01f)
        assertEquals(65.41f, Instrument.CELLO.strings.last().frequency(), 0.01f)
        assertEquals(659.26f, Instrument.VIOLIN.strings.first().frequency(), 0.02f)
    }

    @Test
    fun closestString_returnsStringAndCents() {
        val match = Instrument.GUITAR.closestString(111f)!!
        assertEquals(5, match.string.number) // A2 = 110 Hz
        assertEquals(15.7f, match.cents, 0.2f)
    }

    @Test
    fun closestString_ukuleleReentrantG() {
        assertEquals(4, Instrument.UKULELE.closestString(392f)!!.string.number) // G4
    }

    @Test
    fun closestString_respectsReference() {
        val match = Instrument.VIOLIN.closestString(442f, a4 = 442f)!!
        assertEquals(2, match.string.number)
        assertEquals(0f, match.cents, 0.05f)
    }

    @Test
    fun otherIsChromatic() {
        assertTrue(Instrument.OTHER.isChromatic)
        assertNull(Instrument.OTHER.closestString(440f))
    }
}
