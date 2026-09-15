package com.andres.smarttuner.tuner

import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.StringMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringTuningTrackerTest {

    private val guitar = Instrument.GUITAR.strings
    private fun match(number: Int, cents: Float) = StringMatch(guitar.first { it.number == number }, cents)

    @Test
    fun marksStringAfterSustainedInTune() {
        val tracker = StringTuningTracker(requiredFrames = 10)
        repeat(9) { tracker.update(match(5, 2f)) }
        assertTrue(tracker.tunedStrings.isEmpty())
        tracker.update(match(5, -3f))
        assertEquals(setOf(5), tracker.tunedStrings)
    }

    @Test
    fun switchingStringRestartsStreak() {
        val tracker = StringTuningTracker(requiredFrames = 10)
        repeat(6) { tracker.update(match(5, 0f)) }
        repeat(6) { tracker.update(match(6, 0f)) }
        assertTrue(tracker.tunedStrings.isEmpty())
    }

    @Test
    fun silenceRestartsStreak() {
        val tracker = StringTuningTracker(requiredFrames = 10)
        repeat(6) { tracker.update(match(1, 0f)) }
        tracker.update(null)
        repeat(6) { tracker.update(match(1, 0f)) }
        assertTrue(tracker.tunedStrings.isEmpty())
    }

    @Test
    fun sustainedDetuning_unmarksString() {
        val tracker = StringTuningTracker(requiredFrames = 10)
        repeat(10) { tracker.update(match(3, 0f)) }
        repeat(10) { tracker.update(match(3, 25f)) }
        assertTrue(tracker.tunedStrings.isEmpty())
    }

    @Test
    fun hugeDeviation_isIgnored() {
        val tracker = StringTuningTracker(requiredFrames = 10)
        repeat(10) { tracker.update(match(3, 0f)) }
        repeat(20) { tracker.update(match(3, 180f)) } // nota pisada, no la cuerda al aire
        assertEquals(setOf(3), tracker.tunedStrings)
    }

    @Test
    fun reset_clearsEverything() {
        val tracker = StringTuningTracker(requiredFrames = 2)
        repeat(2) { tracker.update(match(2, 0f)) }
        tracker.reset()
        assertTrue(tracker.tunedStrings.isEmpty())
    }
}
