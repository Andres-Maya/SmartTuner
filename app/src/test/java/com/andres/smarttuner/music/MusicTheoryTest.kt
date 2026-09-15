package com.andres.smarttuner.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTheoryTest {

    @Test
    fun a440_isMidi69() {
        assertEquals(69f, MusicTheory.frequencyToMidi(440f), 0.001f)
    }

    @Test
    fun lowE_guitarString_isMidi40() {
        assertEquals(40f, MusicTheory.frequencyToMidi(82.41f), 0.01f)
    }

    @Test
    fun middleC_frequency() {
        assertEquals(261.63f, MusicTheory.midiToFrequency(60f), 0.01f)
    }

    @Test
    fun customReference_shiftsFrequency() {
        assertEquals(69f, MusicTheory.frequencyToMidi(442f, a4 = 442f), 0.001f)
    }

    @Test
    fun centsOffset_isComputedInLogScale() {
        val cents = (MusicTheory.frequencyToMidi(445f) - 69f) * 100f
        assertEquals(19.56f, cents, 0.05f)
    }

    @Test
    fun noteNames_withSharpsAndFlats() {
        assertEquals("A4", MusicTheory.noteName(69).label)
        assertEquals("C4", MusicTheory.noteName(60).label)
        assertEquals("B3", MusicTheory.noteName(59).label)
        assertEquals("A♯4", MusicTheory.noteName(70, AccidentalStyle.SHARPS).label)
        assertEquals("B♭4", MusicTheory.noteName(70, AccidentalStyle.FLATS).label)
        assertEquals("E♭2", MusicTheory.noteName(39, AccidentalStyle.FLATS).label)
    }

    @Test
    fun solfege() {
        assertEquals("La 4", MusicTheory.noteName(69).solfegeLabel)
        assertEquals("Fa♯ 3", MusicTheory.noteName(54).solfegeLabel)
        assertEquals("Sol♭ 3", MusicTheory.noteName(54, AccidentalStyle.FLATS).solfegeLabel)
    }
}
