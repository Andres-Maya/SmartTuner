package com.andres.smarttuner.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchSmootherTest {

    @Test
    fun ignoresIsolatedOctaveGlitch() {
        val smoother = PitchSmoother()
        repeat(4) { smoother.add(69f) }
        val afterGlitch = smoother.add(81f)
        assertEquals(69f, afterGlitch, 0.01f)
    }

    @Test
    fun followsRealNoteChange() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.add(69f) }
        var value = 0f
        repeat(3) { value = smoother.add(71f) }
        assertEquals(71f, value, 0.01f)
    }

    @Test
    fun smoothsSmallFluctuations() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.add(69f) }
        val value = smoother.add(69.2f)
        assertEquals(69f, value, 0.01f) // la mediana todavía es 69
    }
}
