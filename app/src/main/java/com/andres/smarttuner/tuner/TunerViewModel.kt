package com.andres.smarttuner.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.ai.IdentificationSession
import com.andres.smarttuner.ai.InstrumentHead
import com.andres.smarttuner.ai.YamnetClassifier
import com.andres.smarttuner.audio.AudioFrame
import com.andres.smarttuner.audio.MicrophoneAudioSource
import com.andres.smarttuner.audio.PitchResult
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.MusicTheory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val audioSource = MicrophoneAudioSource(application)
    private val smoother = PitchSmoother()
    private val stringTracker = StringTuningTracker()

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    // Reparte los bloques del único AudioRecord a análisis secundarios (la IA) sin abrir otro micrófono.
    private val audioFrames = MutableSharedFlow<AudioFrame>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var listenJob: Job? = null
    private var identifyJob: Job? = null
    private var lastSignalAt = 0L

    @Volatile
    private var classifier: YamnetClassifier? = null

    // Capa entrenada con grabaciones propias (ml/README.md); null mientras no exista el asset.
    @Volatile
    private var head: InstrumentHead? = null
    @Volatile
    private var headLoaded = false

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
            audioSource.frames()
                .catch { e ->
                    _uiState.update { it.copy(isListening = false, hasSignal = false, errorMessage = e.message) }
                }
                .collect { frame ->
                    audioFrames.tryEmit(frame)
                    onPitch(frame.pitch)
                }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        if (identifyJob?.isActive == true) dismissIdentification()
        smoother.reset()
        stringTracker.update(null)
        _uiState.update { it.copy(isListening = false, hasSignal = false) }
    }

    fun setAccidentalStyle(style: AccidentalStyle) {
        _uiState.update { it.copy(accidentalStyle = style) }
    }

    fun changeReference(deltaHz: Float) {
        smoother.reset()
        _uiState.update {
            it.copy(referenceA4 = (it.referenceA4 + deltaHz).coerceIn(MusicTheory.MIN_A4, MusicTheory.MAX_A4))
        }
    }

    fun identifyInstrument() {
        if (identifyJob?.isActive == true) return
        startListening()
        if (listenJob?.isActive != true) return

        _uiState.update { it.copy(identification = IdentificationUiState.Listening(progress = 0f)) }
        identifyJob = viewModelScope.launch {
            val next = try {
                IdentificationUiState.Finished(withContext(Dispatchers.Default) { runIdentification() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Falló la identificación de instrumento", e)
                IdentificationUiState.Failed
            }
            _uiState.update { it.copy(identification = next) }
        }
    }

    fun dismissIdentification() {
        identifyJob?.cancel()
        identifyJob = null
        _uiState.update { it.copy(identification = IdentificationUiState.Hidden) }
    }

    /**
     * Cierra el resultado y abre la afinación del instrumento elegido. La IA preselecciona el más
     * probable, pero el usuario puede corregirla. Sin cuerdas (modo "Otro") solo cierra.
     */
    fun acceptIdentification(selected: Instrument?) {
        val instrument = selected?.takeUnless { it.isChromatic }

        dismissIdentification()
        if (instrument != null) {
            stringTracker.reset()
            _uiState.update { it.copy(mode = TunerMode.InstrumentTuning(instrument), tunedStrings = emptySet()) }
        }
    }

    fun closeInstrumentTuning() {
        stringTracker.reset()
        _uiState.update { it.copy(mode = TunerMode.Chromatic, tunedStrings = emptySet()) }
    }

    private suspend fun runIdentification(): IdentificationOutcome {
        val sampleRate = MicrophoneAudioSource.SAMPLE_RATE
        val yamnet = classifier ?: YamnetClassifier(getApplication()).also {
            // La primera inferencia es mucho más lenta: se hace con silencio antes de escuchar.
            it.classify(FloatArray(sampleRate), sampleRate)
            classifier = it
        }
        if (!headLoaded) {
            head = InstrumentHead.loadFromAssets(getApplication())
            headLoaded = true
            Log.i(TAG, head?.let { "Modelo propio cargado: ${it.labels}" } ?: "Sin modelo propio: solo YAMNet")
        }
        val session = IdentificationSession(
            sampleRate = sampleRate,
            classify = { yamnet.classify(it, sampleRate) },
            head = head,
        )

        // Termina por segundos de audio analizado; el tope evita quedarse colgado si el micrófono se detiene.
        withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            audioFrames.takeWhile { session.acceptedSeconds < IDENTIFY_SECONDS }.collect { frame ->
                val pitchMidi = frame.pitch
                    ?.takeIf { it.probability >= MIN_PROBABILITY }
                    ?.let { MusicTheory.frequencyToMidi(it.frequency) }
                session.accept(frame.samples, pitchMidi)

                val progress = session.acceptedSeconds / IDENTIFY_SECONDS
                _uiState.update { state ->
                    if (state.identification is IdentificationUiState.Listening) {
                        state.copy(identification = IdentificationUiState.Listening(progress.coerceIn(0f, 1f)))
                    } else {
                        state
                    }
                }
            }
        }
        val outcome = session.result()
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val learned = session.headSummary.entries
                .sortedByDescending { it.value }
                .take(4)
                .joinToString { "${it.key}=%.2f".format(it.value) }
            val decision = (outcome as? IdentificationOutcome.Identified)?.candidates
                ?.take(3)
                ?.joinToString { "${it.instrument.datasetLabel}=%.2f".format(it.probability) }
                ?: "sin instrumento"
            Log.d(TAG, "${session.analyzedWindows} ventanas · capa: $learned · decisión: $decision")
        }
        return outcome
    }

    private fun onPitch(result: PitchResult?) {
        val now = SystemClock.elapsedRealtime()
        if (result == null || result.probability < MIN_PROBABILITY) {
            stringTracker.update(null)
            if (now - lastSignalAt > SIGNAL_HOLD_MS) {
                smoother.reset()
                if (_uiState.value.hasSignal) _uiState.update { it.copy(hasSignal = false) }
            }
            return
        }
        lastSignalAt = now

        val current = _uiState.value
        val reference = current.referenceA4
        val midi = smoother.add(MusicTheory.frequencyToMidi(result.frequency, reference))
        val nearest = midi.roundToInt()
        val frequency = MusicTheory.midiToFrequency(midi, reference)

        // Se calcula fuera de update {} porque el tracker tiene estado y update puede reintentar.
        val tunedStrings = (current.mode as? TunerMode.InstrumentTuning)
            ?.let { stringTracker.update(it.instrument.closestString(frequency, reference)) }

        _uiState.update {
            it.copy(
                hasSignal = true,
                frequency = frequency,
                nearestMidi = nearest,
                cents = (midi - nearest) * 100f,
                tunedStrings = tunedStrings ?: it.tunedStrings,
            )
        }
    }

    override fun onCleared() {
        stopListening()
        classifier?.close()
        classifier = null
    }

    private companion object {
        const val TAG = "TunerViewModel"
        const val MIN_PROBABILITY = 0.85f
        const val SIGNAL_HOLD_MS = 500L
        const val IDENTIFY_SECONDS = 4f
        const val IDENTIFY_TIMEOUT_MS = 12_000L
    }
}
