package com.andres.smarttuner.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andres.smarttuner.ai.AudioCapture
import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.ai.IdentificationSession
import com.andres.smarttuner.ai.InstrumentHead
import com.andres.smarttuner.ai.YamnetClassifier
import com.andres.smarttuner.audio.AudioFrame
import com.andres.smarttuner.audio.MicrophoneAudioSource
import com.andres.smarttuner.audio.PitchResult
import com.andres.smarttuner.audio.ReferenceTonePlayer
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentString
import com.andres.smarttuner.music.MusicTheory
import com.andres.smarttuner.music.Tuning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val audioSource = MicrophoneAudioSource(application)
    private val smoother = PitchSmoother()
    private val stringTracker = StringTuningTracker()
    private val tonePlayer = ReferenceTonePlayer(application)

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    // Reparte los bloques del único AudioRecord a análisis secundarios (la IA) sin abrir otro micrófono.
    private val audioFrames = MutableSharedFlow<AudioFrame>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var listenJob: Job? = null
    private var identifyJob: Job? = null
    private var referenceJob: Job? = null
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
        stopStringSound()
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
     * Cierra el resultado y abre la afinación del instrumento elegido, con la variante que se
     * haya escogido (6 o 7 cuerdas, bajo de 5…). La IA preselecciona el instrumento más probable,
     * pero el usuario puede corregirlo. Sin cuerdas (modo "Otro") solo cierra.
     */
    fun acceptIdentification(selected: Instrument?, tuning: Tuning? = null) {
        val instrument = selected?.takeUnless { it.isChromatic }

        dismissIdentification()
        if (instrument != null) {
            val chosen = tuning?.takeIf { it in instrument.tunings } ?: instrument.standardTuning
            stringTracker.reset()
            _uiState.update {
                it.copy(
                    mode = TunerMode.InstrumentTuning(instrument, chosen),
                    tunedStrings = emptySet(),
                    selectedString = null,
                )
            }
        }
    }

    /** Cambia de variante sin salir de la pantalla: lo afinado hasta ahora ya no vale. */
    fun setTuning(tuning: Tuning) {
        val mode = _uiState.value.mode as? TunerMode.InstrumentTuning ?: return
        if (mode.tuning == tuning) return
        stringTracker.reset()
        _uiState.update {
            it.copy(
                mode = mode.copy(tuning = tuning),
                tunedStrings = emptySet(),
                selectedString = null,
            )
        }
    }

    /**
     * Fija la cuerda a afinar. Volver a tocar la misma la suelta y el afinador vuelve a
     * detectar sola la más cercana.
     */
    fun selectString(number: Int?) {
        // Corta la racha en curso pero conserva las cuerdas ya afinadas.
        stringTracker.update(null)
        _uiState.update {
            it.copy(selectedString = number.takeIf { chosen -> chosen != it.selectedString })
        }
    }

    /**
     * Hace sonar una cuerda como referencia (ver assets/strings/README.md). Mientras suena,
     * el afinador deja de escuchar: el micrófono oiría la propia referencia —que está afinada
     * por definición— y marcaría la cuerda como lista sin que el instrumento haya sonado.
     */
    fun playString(string: InstrumentString) {
        val state = _uiState.value
        val instrument = (state.mode as? TunerMode.InstrumentTuning)?.instrument ?: return
        tonePlayer.play(instrument, string, state.referenceA4)
        referenceJob?.cancel()
        referenceJob = viewModelScope.launch {
            _uiState.update { it.copy(soundingString = string.number, hasSignal = false) }
            while (tonePlayer.isSounding) delay(REFERENCE_POLL_MS)
            smoother.reset()
            _uiState.update { it.copy(soundingString = null) }
        }
    }

    /** Corta la referencia: vuelve a escucharte al instante, sin esperar a que se apague. */
    fun stopStringSound() {
        referenceJob?.cancel()
        referenceJob = null
        tonePlayer.stop()
        if (_uiState.value.soundingString != null) {
            _uiState.update { it.copy(soundingString = null) }
        }
    }

    fun closeInstrumentTuning() {
        stringTracker.reset()
        stopStringSound()
        _uiState.update {
            it.copy(mode = TunerMode.Chromatic, tunedStrings = emptySet(), selectedString = null)
        }
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
        // En desarrollo se guarda cada escucha para comparar la app con el entrenamiento en el PC.
        val capture = if (isDebuggable) AudioCapture(sampleRate) else null

        // Termina por segundos de audio analizado; el tope evita quedarse colgado si el micrófono se detiene.
        withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            audioFrames.takeWhile { session.acceptedSeconds < IDENTIFY_SECONDS }.collect { frame ->
                val pitchMidi = frame.pitch
                    ?.takeIf { it.probability >= MIN_PROBABILITY }
                    ?.let { MusicTheory.frequencyToMidi(it.frequency) }
                capture?.append(frame.samples)
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
        capture?.let { saveCapture(it, session, outcome) }
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

    private val isDebuggable: Boolean
        get() = (getApplication<Application>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Guarda el audio y los números de una escucha en
     * /sdcard/Android/data/com.andres.smarttuner/files/captures/ (ver ml/README.md).
     */
    private fun saveCapture(capture: AudioCapture, session: IdentificationSession, outcome: IdentificationOutcome) {
        val directory = getApplication<Application>().getExternalFilesDir(CAPTURE_DIRECTORY) ?: return
        val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        runCatching {
            capture.writeWav(File(directory, "$name.wav"))
            val decision = (outcome as? IdentificationOutcome.Identified)?.candidates?.let { candidates ->
                JSONArray(candidates.map {
                    JSONObject().put("instrument", it.instrument.datasetLabel).put("probability", it.probability.toDouble())
                })
            }
            val windows = JSONArray(session.windowDiagnostics.map { window ->
                JSONObject()
                    .put("endSample", window.endSample)
                    .put("rms", window.rms.toDouble())
                    .put("resultCount", window.resultCount)
                    .put("categoryCount", window.categoryCount)
                    .put("scores", JSONArray(window.scores.map { it.toDouble() }))
                    .put("head", JSONObject(window.head.mapValues { it.value.toDouble() }))
            })
            val report = JSONObject()
                .put("sampleRate", capture.sampleRate)
                .put("samples", capture.sampleCount)
                .put("pitchesMidi", JSONArray(session.detectedPitches.map { it.toDouble() }))
                .put("windows", windows)
                .put("decision", decision ?: JSONObject.NULL)
            File(directory, "$name.json").writeText(report.toString())
            pruneCaptures(directory)
            Log.i(TAG, "Captura guardada: ${File(directory, "$name.wav").absolutePath}")
        }.onFailure { Log.w(TAG, "No se pudo guardar la captura", it) }
    }

    private fun pruneCaptures(directory: File) {
        val captures = directory.listFiles { file -> file.extension == "wav" }?.sortedByDescending { it.name } ?: return
        captures.drop(MAX_CAPTURES).forEach { wav ->
            wav.delete()
            File(directory, "${wav.nameWithoutExtension}.json").delete()
        }
    }

    private fun onPitch(result: PitchResult?) {
        // Mientras suena la referencia el micrófono solo devuelve el eco de la propia app.
        if (tonePlayer.isSounding) {
            stringTracker.update(null)
            return
        }
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
        val tunedStrings = (current.mode as? TunerMode.InstrumentTuning)?.let { mode ->
            val match = current.selectedString?.let { mode.tuning.match(it, frequency, reference) }
                ?: mode.tuning.closestString(frequency, reference)
            stringTracker.update(match)
        }

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
        tonePlayer.release()
        classifier?.close()
        classifier = null
    }

    private companion object {
        const val TAG = "TunerViewModel"
        const val MIN_PROBABILITY = 0.85f
        const val SIGNAL_HOLD_MS = 500L
        const val IDENTIFY_SECONDS = 4f
        const val IDENTIFY_TIMEOUT_MS = 12_000L
        const val CAPTURE_DIRECTORY = "captures"
        const val MAX_CAPTURES = 60
        const val REFERENCE_POLL_MS = 80L
    }
}
