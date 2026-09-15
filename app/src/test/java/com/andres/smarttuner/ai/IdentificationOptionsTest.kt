package com.andres.smarttuner.ai

import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationOptionsTest {

    private fun outcome(vararg probabilities: Pair<Instrument, Float>) = IdentificationOutcome.Identified(
        candidates = probabilities
            .map { (instrument, probability) -> InstrumentCandidate(instrument, probability) }
            .sortedByDescending { it.probability },
        otherLabel = null,
    )

    private val allZero = Instrument.entries.map { it to 0f }.toTypedArray()

    @Test
    fun bowedBest_alwaysOffersViolinViolaAndCello() {
        val options = outcome(
            *allZero,
            Instrument.VIOLIN to 0.6f,
            Instrument.GUITAR to 0.3f,
            Instrument.OTHER to 0.1f,
        ).let { it.copy(candidates = it.candidates.distinctBy { c -> c.instrument }) }.options.map { it.instrument }

        assertEquals(Instrument.VIOLIN, options.first())
        assertTrue(options.containsAll(listOf(Instrument.VIOLA, Instrument.CELLO)))
        assertTrue(Instrument.GUITAR in options)
        assertFalse(Instrument.BASS in options)
    }

    @Test
    fun pluckedBest_offersWholePluckedFamily() {
        val options = outcome(
            Instrument.GUITAR to 0.8f,
            Instrument.OTHER to 0.2f,
            Instrument.BASS to 0f,
            Instrument.UKULELE to 0f,
            Instrument.VIOLIN to 0f,
        ).options.map { it.instrument }

        assertTrue(options.containsAll(listOf(Instrument.GUITAR, Instrument.BASS, Instrument.UKULELE)))
        assertFalse(Instrument.VIOLIN in options)
    }

    @Test
    fun otherBest_onlyLikelyOptions() {
        val options = outcome(
            Instrument.OTHER to 0.7f,
            Instrument.GUITAR to 0.2f,
            Instrument.VIOLIN to 0.1f,
            Instrument.CELLO to 0f,
        ).options.map { it.instrument }

        assertEquals(listOf(Instrument.OTHER, Instrument.GUITAR, Instrument.VIOLIN), options)
    }

    @Test
    fun options_areDistinctAndSorted() {
        val options = outcome(
            Instrument.CELLO to 0.5f,
            Instrument.VIOLIN to 0.3f,
            Instrument.VIOLA to 0.2f,
        ).options

        assertEquals(options.map { it.instrument }.distinct().size, options.size)
        assertEquals(options.sortedByDescending { it.probability }, options)
    }

    @Test
    fun families() {
        assertEquals(InstrumentFamily.BOWED, Instrument.VIOLA.family)
        assertEquals(InstrumentFamily.BOWED, Instrument.CELLO.family)
        assertEquals(InstrumentFamily.PLUCKED, Instrument.UKULELE.family)
        assertNull(Instrument.OTHER.family)
    }
}
