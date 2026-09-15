package com.andres.smarttuner.music

enum class Clef { TREBLE, BASS }

/**
 * Posición de una nota en un pentagrama de 5 líneas.
 *
 * [staffStep] cuenta medios espacios desde la línea inferior: 0 = 1ª línea, 1 = 1er espacio,
 * 8 = 5ª línea. Valores negativos o mayores que 8 requieren líneas adicionales.
 * [octaveShift] indica 8va (+1), 15ma (+2), 8vb (-1) o 15mb (-2).
 */
data class StaffPosition(
    val clef: Clef,
    val staffStep: Int,
    val octaveShift: Int,
) {
    val ledgerSteps: List<Int>
        get() = when {
            staffStep <= -2 -> (-2 downTo staffStep).step(2).toList()
            staffStep >= 10 -> (10..staffStep).step(2).toList()
            else -> emptyList()
        }

    val octaveShiftLabel: String?
        get() = when (octaveShift) {
            2 -> "15ma"
            1 -> "8va"
            -1 -> "8vb"
            -2 -> "15mb"
            else -> null
        }
}

object StaffLayout {
    private const val TREBLE_BOTTOM_LINE = 4 * 7 + 2 // E4
    private const val BASS_BOTTOM_LINE = 2 * 7 + 4 // G2
    private const val MIDDLE_C = 60

    fun position(note: NoteName): StaffPosition {
        val clef = if (note.midi >= MIDDLE_C) Clef.TREBLE else Clef.BASS
        val shift = when {
            note.midi >= 96 -> 2
            note.midi >= 84 -> 1
            note.midi < 24 -> -2
            note.midi < 36 -> -1
            else -> 0
        }
        val bottomLine = if (clef == Clef.TREBLE) TREBLE_BOTTOM_LINE else BASS_BOTTOM_LINE
        return StaffPosition(clef, note.diatonicIndex - shift * 7 - bottomLine, shift)
    }
}
