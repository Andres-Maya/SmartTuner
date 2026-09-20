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

/**
 * Una afinación concreta: cuántas cuerdas tiene el instrumento y en qué nota va cada una.
 * Un mismo instrumento tiene varias (guitarra de 6 o 7 cuerdas, bajo de 4, 5 o 6…).
 */
data class Tuning(
    val name: String,
    val openStringsMidi: List<Int>,
) {
    /** Ordenadas por número de cuerda (1, 2, 3…). */
    val strings: List<InstrumentString> =
        openStringsMidi.mapIndexed { index, midi -> InstrumentString(index + 1, midi) }

    val isChromatic: Boolean get() = strings.isEmpty()

    /** Devuelve `null` en el modo cromático o si la frecuencia no es válida. */
    fun closestString(frequency: Float, a4: Float = MusicTheory.DEFAULT_A4): StringMatch? {
        if (isChromatic || frequency <= 0f) return null
        val midi = MusicTheory.frequencyToMidi(frequency, a4)
        val closest = strings.minBy { abs(midi - it.midi) }
        return StringMatch(closest, (midi - closest.midi) * 100f)
    }

    /** Desviación contra una cuerda concreta, para cuando se elige a mano cuál afinar. */
    fun match(number: Int, frequency: Float, a4: Float = MusicTheory.DEFAULT_A4): StringMatch? {
        if (frequency <= 0f) return null
        val string = strings.firstOrNull { it.number == number } ?: return null
        val midi = MusicTheory.frequencyToMidi(frequency, a4)
        return StringMatch(string, (midi - string.midi) * 100f)
    }
}

/** Familias de timbre parecido, que el modelo de audio tiende a confundir entre sí. */
enum class InstrumentFamily { BOWED, PLUCKED }

/**
 * Instrumentos con sus afinaciones (altura real, no la escrita). La primera de [tunings] es
 * la estándar.
 *
 * [playableRange] describe el instrumento **estándar**, no sus variantes: lo usa la
 * identificación para descartar instrumentos por el registro de lo que suena, y ampliarlo
 * para cubrir cuerdas extra le quita justo lo que la hace acertar (el do grave, por ejemplo,
 * es lo que separa una viola de un violín). Si alguien identifica tocando la séptima cuerda
 * de su guitarra, la IA lo penalizará; siempre puede corregir el instrumento a mano.
 *
 * [OTHER] es un modo cromático libre, sin cuerdas fijas.
 */
enum class Instrument(
    val displayName: String,
    val tunings: List<Tuning>,
    val playableRange: IntRange,
) {
    /** E4 B3 G3 D3 A2 E2, más la séptima cuerda si B1 y el Drop D. Hasta E6 (traste 24). */
    GUITAR(
        "Guitarra",
        listOf(
            Tuning("6 cuerdas", listOf(64, 59, 55, 50, 45, 40)),
            Tuning("7 cuerdas", listOf(64, 59, 55, 50, 45, 40, 35)),
            Tuning("Drop D", listOf(64, 59, 55, 50, 45, 38)),
            Tuning("Medio tono abajo", listOf(63, 58, 54, 49, 44, 39)),
        ),
        40..88,
    ),

    /** G2 D2 A1 E1, con las variantes de 5 (si grave) y 6 cuerdas (do agudo). Hasta G4. */
    BASS(
        "Bajo",
        listOf(
            Tuning("4 cuerdas", listOf(43, 38, 33, 28)),
            Tuning("5 cuerdas", listOf(43, 38, 33, 28, 23)),
            Tuning("6 cuerdas", listOf(48, 43, 38, 33, 28, 23)),
        ),
        28..67,
    ),

    /** E5 A4 D4 G3. Hasta E7. */
    VIOLIN("Violín", listOf(Tuning("4 cuerdas", listOf(76, 69, 62, 55))), 55..100),

    /** A4 D4 G3 C3; la de 5 cuerdas añade un mi agudo. Hasta E6. */
    VIOLA(
        "Viola",
        listOf(
            Tuning("4 cuerdas", listOf(69, 62, 55, 48)),
            Tuning("5 cuerdas", listOf(76, 69, 62, 55, 48)),
        ),
        48..88,
    ),

    /** A3 D3 G2 C2; la de 5 cuerdas añade un mi agudo. Hasta A5. */
    CELLO(
        "Violonchelo",
        listOf(
            Tuning("4 cuerdas", listOf(57, 50, 43, 36)),
            Tuning("5 cuerdas", listOf(64, 57, 50, 43, 36)),
        ),
        36..81,
    ),

    /**
     * Afinación re-entrante GCEA: A4 E4 C4 G4 (la 4ª cuerda es más aguda que la 3ª).
     * El tenor suele llevar el sol grave y el barítono va en DGBE. Hasta C6.
     */
    UKULELE(
        "Ukelele",
        listOf(
            Tuning("Soprano · GCEA", listOf(69, 64, 60, 67)),
            Tuning("Tenor · sol grave", listOf(69, 64, 60, 55)),
            Tuning("Barítono · DGBE", listOf(62, 59, 55, 50)),
        ),
        60..84,
    ),

    OTHER("Otro", listOf(Tuning("Cromático", emptyList())), 0..127),
    ;

    /** Afinación estándar del instrumento. */
    val standardTuning: Tuning get() = tunings.first()

    /** Cuerdas de la afinación estándar. */
    val strings: List<InstrumentString> get() = standardTuning.strings

    val isChromatic: Boolean get() = standardTuning.isChromatic

    /** Nombre de la carpeta del dataset y de la clase del modelo entrenado (ver ml/README.md). */
    val datasetLabel: String get() = name.lowercase()

    val family: InstrumentFamily?
        get() = when (this) {
            VIOLIN, VIOLA, CELLO -> InstrumentFamily.BOWED
            GUITAR, BASS, UKULELE -> InstrumentFamily.PLUCKED
            OTHER -> null
        }

    fun canPlay(midi: Float, toleranceSemitones: Float = 1f): Boolean =
        midi >= playableRange.first - toleranceSemitones && midi <= playableRange.last + toleranceSemitones

    /** Cuerda más cercana en la afinación estándar. */
    fun closestString(frequency: Float, a4: Float = MusicTheory.DEFAULT_A4): StringMatch? =
        standardTuning.closestString(frequency, a4)
}
