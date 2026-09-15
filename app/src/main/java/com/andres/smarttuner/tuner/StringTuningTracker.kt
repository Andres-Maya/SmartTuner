package com.andres.smarttuner.tuner

import com.andres.smarttuner.music.StringMatch
import kotlin.math.abs

/**
 * Lleva la cuenta de las cuerdas afinadas. Una cuerda se marca cuando se sostiene dentro de
 * ±[inTuneCents] durante [requiredFrames] lecturas seguidas (~0.5 s) y se desmarca si luego
 * se sostiene desafinada. Desviaciones enormes se ignoran: probablemente es otra nota pisada.
 */
class StringTuningTracker(
    private val requiredFrames: Int = 10,
    private val inTuneCents: Float = TunerUiState.IN_TUNE_CENTS,
    private val detunedCents: Float = 15f,
    private val sameStringMaxCents: Float = 60f,
) {
    private val tuned = mutableSetOf<Int>()
    private var currentString: Int? = null
    private var inTuneStreak = 0
    private var detunedStreak = 0

    val tunedStrings: Set<Int> get() = tuned.toSet()

    fun update(match: StringMatch?): Set<Int> {
        if (match == null) {
            resetStreaks()
            return tunedStrings
        }

        val number = match.string.number
        if (number != currentString) {
            currentString = number
            resetStreaks()
        }

        val deviation = abs(match.cents)
        when {
            deviation <= inTuneCents -> {
                inTuneStreak++
                detunedStreak = 0
            }
            deviation in detunedCents..sameStringMaxCents -> {
                detunedStreak++
                inTuneStreak = 0
            }
            else -> resetStreaks()
        }

        if (inTuneStreak >= requiredFrames) tuned += number
        if (detunedStreak >= requiredFrames) tuned -= number
        return tunedStrings
    }

    fun reset() {
        tuned.clear()
        currentString = null
        resetStreaks()
    }

    private fun resetStreaks() {
        inTuneStreak = 0
        detunedStreak = 0
    }
}
