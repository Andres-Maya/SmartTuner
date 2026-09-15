package com.andres.smarttuner.music

import kotlin.math.abs

/**
 * Una cuerda al aire. [number] sigue la numeración estándar del instrumento
 * (en la guitarra, 1 = Mi agudo; en el ukelele, 1 = La).
 */
data class InstrumentString(
    val number: Int,
    val midi: Int,
) {
    fun note(style: AccidentalStyle = AccidentalStyle.SHARPS): NoteName = MusicTheory.noteName(midi, style)

    fun frequency(a4: Float = MusicTheory.DEFAULT_A4): Float = MusicTheory.midiToFrequency(midi.toFloat(), a4)
}

/** Cuerda más cercana a una frecuencia y su desviación en cents (negativo = grave). */
data class StringMatch(
    val string: InstrumentString,
    val cents: Float,
)

/** Familias de timbre parecido, que el modelo de audio tiende a confundir entre sí. */
enum class InstrumentFamily { BOWED, PLUCKED }

/**
 * Instrumentos con su afinación estándar (altura real, no la escrita).
 * [playableRange] es el rango habitual de fundamentales en MIDI.
 * [OTHER] es un modo cromático libre, sin cuerdas fijas.
 */
enum class Instrument(
    val displayName: String,
    openStringsMidi: List<Int>,
    val playableRange: IntRange,
) {
    /** E4 B3 G3 D3 A2 E2. Hasta E6 (traste 24). */
    GUITAR("Guitarra", listOf(64, 59, 55, 50, 45, 40), 40..88),

    /** Bajo de 4 cuerdas: G2 D2 A1 E1. Hasta G4. */
    BASS("Bajo", listOf(43, 38, 33, 28), 28..67),

    /** E5 A4 D4 G3. Hasta E7. */
    VIOLIN("Violín", listOf(76, 69, 62, 55), 55..100),

    /** A4 D4 G3 C3. Hasta E6. */
    VIOLA("Viola", listOf(69, 62, 55, 48), 48..88),

    /** A3 D3 G2 C2. Hasta A5. */
    CELLO("Violonchelo", listOf(57, 50, 43, 36), 36..81),

    /** Afinación re-entrante GCEA: A4 E4 C4 G4 (la 4ª cuerda es más aguda que la 3ª). Hasta C6. */
    UKULELE("Ukelele", listOf(69, 64, 60, 67), 60..84),

    OTHER("Otro", emptyList(), 0..127);

    /** Ordenadas por número de cuerda (1, 2, 3…). */
    val strings: List<InstrumentString> =
        openStringsMidi.mapIndexed { index, midi -> InstrumentString(index + 1, midi) }

    val isChromatic: Boolean get() = strings.isEmpty()

    val family: InstrumentFamily?
        get() = when (this) {
            VIOLIN, VIOLA, CELLO -> InstrumentFamily.BOWED
            GUITAR, BASS, UKULELE -> InstrumentFamily.PLUCKED
            OTHER -> null
        }

    fun canPlay(midi: Float, toleranceSemitones: Float = 1f): Boolean =
        midi >= playableRange.first - toleranceSemitones && midi <= playableRange.last + toleranceSemitones

    /** Devuelve `null` en el modo cromático o si la frecuencia no es válida. */
    fun closestString(frequency: Float, a4: Float = MusicTheory.DEFAULT_A4): StringMatch? {
        if (isChromatic || frequency <= 0f) return null
        val midi = MusicTheory.frequencyToMidi(frequency, a4)
        val closest = strings.minBy { abs(midi - it.midi) }
        return StringMatch(closest, (midi - closest.midi) * 100f)
    }
}
