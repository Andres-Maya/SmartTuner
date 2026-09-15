package com.andres.smarttuner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StaffLayoutTest {

    private fun position(midi: Int, style: AccidentalStyle = AccidentalStyle.SHARPS) =
        StaffLayout.position(MusicTheory.noteName(midi, style))

    @Test
    fun trebleClef_referenceNotes() {
        assertEquals(StaffPosition(Clef.TREBLE, staffStep = -2, octaveShift = 0), position(60)) // C4
        assertEquals(2, position(67).staffStep) // G4 en 2ª línea
        assertEquals(8, position(77).staffStep) // F5 en 5ª línea
    }

    @Test
    fun bassClef_referenceNotes() {
        assertEquals(Clef.BASS, position(40).clef)
        assertEquals(-2, position(40).staffStep) // E2 (6ª cuerda) con una línea adicional
        assertEquals(6, position(53).staffStep) // F3 en 4ª línea (clave de Fa)
    }

    @Test
    fun flatSpelling_movesNoteUpOneStep() {
        assertEquals(3, position(70, AccidentalStyle.SHARPS).staffStep) // A♯4 en 2º espacio
        assertEquals(4, position(70, AccidentalStyle.FLATS).staffStep) // B♭4 en 3ª línea
    }

    @Test
    fun ledgerLines() {
        assertEquals(listOf(-2), position(60).ledgerSteps)
        assertEquals(listOf(10), position(81).ledgerSteps) // A5: una línea adicional
        assertEquals(listOf(10), position(83).ledgerSteps) // B5 queda sobre la 1ª adicional
        assertEquals(listOf(-2, -4), position(36).ledgerSteps) // C2 en clave de Fa
        assertEquals(emptyList<Int>(), position(71).ledgerSteps)
    }

    @Test
    fun octaveShift_forExtremeNotes() {
        val c6 = position(84)
        assertEquals(1, c6.octaveShift)
        assertEquals("8va", c6.octaveShiftLabel)
        assertEquals(5, c6.staffStep)

        assertEquals("8vb", position(33).octaveShiftLabel) // A1
        assertNull(position(69).octaveShiftLabel)
    }
}
