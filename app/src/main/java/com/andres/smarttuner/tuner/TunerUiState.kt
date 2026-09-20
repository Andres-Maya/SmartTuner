package com.andres.smarttuner.tuner

import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.MusicTheory
import com.andres.smarttuner.music.NoteName
import com.andres.smarttuner.music.StringMatch
import com.andres.smarttuner.music.Tuning
import kotlin.math.abs

enum class TuningStatus { IDLE, FLAT, IN_TUNE, SHARP }

sealed interface IdentificationUiState {
    data object Hidden : IdentificationUiState

    data class Listening(val progress: Float) : IdentificationUiState

    data class Finished(val outcome: IdentificationOutcome) : IdentificationUiState

    data object Failed : IdentificationUiState
}

/** Pantalla visible: afinador cromático o afinación por cuerdas de un instrumento. */
sealed interface TunerMode {
    data object Chromatic : TunerMode

    /** [tuning] es la variante elegida: 6 o 7 cuerdas, bajo de 5, barítono… */
    data class InstrumentTuning(val instrument: Instrument, val tuning: Tuning) : TunerMode
}

data class TunerUiState(
    val isListening: Boolean = false,
    val hasSignal: Boolean = false,
    /** Frecuencia suavizada detectada, en Hz. */
    val frequency: Float = 0f,
    /** Nota más cercana (MIDI). Se conserva tras el silencio para mostrarla atenuada. */
    val nearestMidi: Int? = null,
    /** Desviación respecto a la nota más cercana, -50..+50. */
    val cents: Float = 0f,
    val referenceA4: Float = MusicTheory.DEFAULT_A4,
    val accidentalStyle: AccidentalStyle = AccidentalStyle.SHARPS,
    val errorMessage: String? = null,
    val identification: IdentificationUiState = IdentificationUiState.Hidden,
    val mode: TunerMode = TunerMode.Chromatic,
    /** Números de cuerda ya afinados en [TunerMode.InstrumentTuning]. */
    val tunedStrings: Set<Int> = emptySet(),
    /** Cuerda elegida a mano; con `null` el afinador busca la más cercana. */
    val selectedString: Int? = null,
    /**
     * Cuerda cuya referencia está sonando. Mientras suena, el micrófono se ignora: si no,
     * la app se oiría a sí misma y daría la cuerda por afinada.
     */
    val soundingString: Int? = null,
) {
    val note: NoteName? get() = nearestMidi?.let { MusicTheory.noteName(it, accidentalStyle) }

    val lowerNeighbor: NoteName? get() = nearestMidi?.let { MusicTheory.noteName(it - 1, accidentalStyle) }

    val upperNeighbor: NoteName? get() = nearestMidi?.let { MusicTheory.noteName(it + 1, accidentalStyle) }

    val targetFrequency: Float
        get() = nearestMidi?.let { MusicTheory.midiToFrequency(it.toFloat(), referenceA4) } ?: 0f

    val status: TuningStatus
        get() = when {
            !hasSignal || nearestMidi == null -> TuningStatus.IDLE
            abs(cents) <= IN_TUNE_CENTS -> TuningStatus.IN_TUNE
            cents < 0f -> TuningStatus.FLAT
            else -> TuningStatus.SHARP
        }

    /**
     * Cuerda que se está afinando y su desviación; se conserva atenuada durante el silencio.
     * Con una cuerda elegida a mano siempre se compara contra esa, aunque suene otra nota.
     */
    val stringMatch: StringMatch?
        get() {
            val tuning = (mode as? TunerMode.InstrumentTuning)?.tuning ?: return null
            return selectedString?.let { tuning.match(it, frequency, referenceA4) }
                ?: tuning.closestString(frequency, referenceA4)
        }

    companion object {
        const val IN_TUNE_CENTS = 5f
    }
}
