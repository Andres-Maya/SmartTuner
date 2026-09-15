package com.andres.smarttuner.tuner

import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.MusicTheory
import com.andres.smarttuner.music.NoteName
import kotlin.math.abs

enum class TuningStatus { IDLE, FLAT, IN_TUNE, SHARP }

sealed interface IdentificationUiState {
    data object Hidden : IdentificationUiState

    data class Listening(val progress: Float) : IdentificationUiState

    data class Finished(val outcome: IdentificationOutcome) : IdentificationUiState

    data object Failed : IdentificationUiState
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

    companion object {
        const val IN_TUNE_CENTS = 5f
    }
}
