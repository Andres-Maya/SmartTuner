package com.andres.smarttuner.music

import kotlin.math.log2
import kotlin.math.pow

private const val LETTERS = "CDEFGAB"
private val SOLFEGE = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

enum class AccidentalStyle { SHARPS, FLATS }

enum class Accidental(val symbol: String) {
    SHARP("♯"),
    FLAT("♭"),
}

/**
 * Nombre de una nota en notación científica (A4 = La central de 440 Hz, C4 = Do central).
 */
data class NoteName(
    val midi: Int,
    val letter: Char,
    val accidental: Accidental?,
    val octave: Int,
) {
    private val letterIndex: Int get() = LETTERS.indexOf(letter)

    /** Índice diatónico absoluto: sube 1 por cada letra (C0 = 0, D0 = 1 … C1 = 7). */
    val diatonicIndex: Int get() = octave * 7 + letterIndex

    val accidentalSymbol: String get() = accidental?.symbol.orEmpty()

    val solfege: String get() = SOLFEGE[letterIndex]

    /** Ej: "A♯4". */
    val label: String get() = "$letter$accidentalSymbol$octave"

    /** Ej: "La♯ 4". */
    val solfegeLabel: String get() = "$solfege$accidentalSymbol $octave"
}

object MusicTheory {
    const val DEFAULT_A4 = 440f
    const val MIN_A4 = 415f
    const val MAX_A4 = 466f

    // Para cada clase de altura (0 = C): índice de letra y alteración.
    private val SHARP_SPELLING = arrayOf(
        0 to null, 0 to Accidental.SHARP, 1 to null, 1 to Accidental.SHARP, 2 to null, 3 to null,
        3 to Accidental.SHARP, 4 to null, 4 to Accidental.SHARP, 5 to null, 5 to Accidental.SHARP, 6 to null,
    )
    private val FLAT_SPELLING = arrayOf(
        0 to null, 1 to Accidental.FLAT, 1 to null, 2 to Accidental.FLAT, 2 to null, 3 to null,
        4 to Accidental.FLAT, 4 to null, 5 to Accidental.FLAT, 5 to null, 6 to Accidental.FLAT, 6 to null,
    )

    /** Número MIDI continuo (69.0 = A4). */
    fun frequencyToMidi(frequency: Float, a4: Float = DEFAULT_A4): Float =
        69f + 12f * log2(frequency / a4)

    fun midiToFrequency(midi: Float, a4: Float = DEFAULT_A4): Float =
        a4 * 2f.pow((midi - 69f) / 12f)

    fun noteName(midi: Int, style: AccidentalStyle = AccidentalStyle.SHARPS): NoteName {
        val spelling = if (style == AccidentalStyle.SHARPS) SHARP_SPELLING else FLAT_SPELLING
        val (letterIndex, accidental) = spelling[midi.mod(12)]
        return NoteName(
            midi = midi,
            letter = LETTERS[letterIndex],
            accidental = accidental,
            octave = midi.floorDiv(12) - 1,
        )
    }
}
