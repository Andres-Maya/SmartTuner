package com.andres.smarttuner.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andres.smarttuner.audio.MicrophonePitchSource
import com.andres.smarttuner.audio.PitchResult
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.MusicTheory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val pitchSource = MicrophonePitchSource(application)
    private val smoother = PitchSmoother()

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var lastSignalAt = 0L

    // El permiso se comprueba explícitamente antes de abrir el micrófono.
    @SuppressLint("MissingPermission")
    fun startListening() {
        if (listenJob?.isActive == true) return
        val granted = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        _uiState.update { it.copy(isListening = true, errorMessage = null) }
        listenJob = viewModelScope.launch {
            pitchSource.pitches()
                .catch { e ->
                    _uiState.update { it.copy(isListening = false, hasSignal = false, errorMessage = e.message) }
                }
                .collect(::onPitch)
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        smoother.reset()
        _uiState.update { it.copy(isListening = false, hasSignal = false) }
    }

    fun toggleAccidentalStyle() {
        _uiState.update {
            val next = if (it.accidentalStyle == AccidentalStyle.SHARPS) AccidentalStyle.FLATS else AccidentalStyle.SHARPS
            it.copy(accidentalStyle = next)
        }
    }

    fun changeReference(deltaHz: Float) {
        smoother.reset()
        _uiState.update {
            it.copy(referenceA4 = (it.referenceA4 + deltaHz).coerceIn(MusicTheory.MIN_A4, MusicTheory.MAX_A4))
        }
    }

    private fun onPitch(result: PitchResult?) {
        val now = SystemClock.elapsedRealtime()
        if (result == null || result.probability < MIN_PROBABILITY) {
            if (now - lastSignalAt > SIGNAL_HOLD_MS) {
                smoother.reset()
                if (_uiState.value.hasSignal) _uiState.update { it.copy(hasSignal = false) }
            }
            return
        }
        lastSignalAt = now

        val reference = _uiState.value.referenceA4
        val midi = smoother.add(MusicTheory.frequencyToMidi(result.frequency, reference))
        val nearest = midi.roundToInt()
        _uiState.update {
            it.copy(
                hasSignal = true,
                frequency = MusicTheory.midiToFrequency(midi, reference),
                nearestMidi = nearest,
                cents = (midi - nearest) * 100f,
            )
        }
    }

    override fun onCleared() {
        stopListening()
    }

    private companion object {
        const val MIN_PROBABILITY = 0.85f
        const val SIGNAL_HOLD_MS = 500L
    }
}
