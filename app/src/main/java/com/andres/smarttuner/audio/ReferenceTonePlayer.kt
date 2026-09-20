package com.andres.smarttuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentString
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.random.Random

/**
 * Hace sonar una cuerda al pulsar su botón.
 *
 * Busca primero un audio propio y, si no hay ninguno, sintetiza una cuerda pulsada, así el
 * botón funciona desde el primer momento. El orden de búsqueda, con extensión wav, mp3, ogg,
 * m4a o flac (ver `assets/strings/README.md`):
 *
 * 1. `Android/data/com.andres.smarttuner/files/strings/` en el teléfono, para probar audios
 *    sin recompilar (`adb push mi_audio.wav …`).
 * 2. `app/src/main/assets/strings/`, que viaja dentro de la app.
 *
 * En ambas carpetas valen estos nombres, del más específico al más general:
 * `guitar_6.wav` (instrumento y número de cuerda), `guitar_40.wav` (instrumento y nota MIDI)
 * y `40.wav` (solo la nota MIDI, para un juego de sonidos común a todos los instrumentos).
 */
class ReferenceTonePlayer(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val externalDirectory: File? get() = context.getExternalFilesDir(DIRECTORY)

    private val assetNames: Set<String> by lazy {
        runCatching { context.assets.list(DIRECTORY)?.toSet() }.getOrNull().orEmpty()
    }

    @Volatile
    private var player: MediaPlayer? = null

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var soundingUntil = 0L

    /**
     * Si la referencia está sonando ahora mismo. Mientras lo esté, el afinador no debe hacer
     * caso al micrófono: estaría oyéndose a sí mismo y daría la cuerda por afinada.
     */
    val isSounding: Boolean get() = SystemClock.elapsedRealtime() < soundingUntil

    /** Suena la cuerda. Si ya sonaba otra, la corta. */
    fun play(instrument: Instrument, string: InstrumentString, a4: Float) {
        val names = fileNames(instrument, string)
        val frequency = string.frequency(a4)
        // Se silencia el análisis desde ya, sin esperar a saber cuánto dura el audio.
        soundingUntil = SystemClock.elapsedRealtime() + MIN_SOUNDING_MS
        executor.execute {
            stopCurrent()
            runCatching {
                val file = externalDirectory?.let { directory ->
                    names.firstNotNullOfOrNull { File(directory, it).takeIf(File::isFile) }
                }
                val asset = names.firstOrNull { it in assetNames }
                when {
                    file != null -> playFile(file)
                    asset != null -> playAsset("$DIRECTORY/$asset")
                    else -> playPluck(frequency)
                }
            }.onFailure { Log.w(TAG, "No se pudo sonar la cuerda ${string.number}", it) }
        }
    }

    fun stop() {
        soundingUntil = 0L
        executor.execute { stopCurrent() }
    }

    fun release() {
        soundingUntil = 0L
        executor.execute { stopCurrent() }
        executor.shutdown()
    }

    private fun fileNames(instrument: Instrument, string: InstrumentString): List<String> {
        val label = instrument.datasetLabel
        val bases = listOf("${label}_${string.number}", "${label}_${string.midi}", "${string.midi}")
        return bases.flatMap { base -> EXTENSIONS.map { "$base.$it" } }
    }

    private fun playFile(file: File) {
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepareAndStart()
        }
    }

    private fun playAsset(path: String) {
        context.assets.openFd(path).use { descriptor ->
            player = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                prepareAndStart()
            }
        }
    }

    private fun MediaPlayer.prepareAndStart() {
        setAudioAttributes(musicAttributes())
        setOnCompletionListener { it.release(); if (player === it) player = null }
        prepare()
        start()
        val length = duration.takeIf { it > 0 }?.toLong() ?: UNKNOWN_LENGTH_MS
        markSounding(length)
    }

    /** Cuerda pulsada sintetizada (Karplus-Strong): ruido en un anillo que se filtra y se apaga. */
    private fun playPluck(frequency: Float) {
        val samples = pluck(frequency)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(musicAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack.write(samples, 0, samples.size)
        audioTrack.notificationMarkerPosition = samples.size
        audioTrack.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(played: AudioTrack) {
                    played.release()
                    if (track === played) track = null
                }

                override fun onPeriodicNotification(played: AudioTrack) = Unit
            },
            mainHandler,
        )
        audioTrack.play()
        track = audioTrack
        markSounding((SUSTAIN_SECONDS * 1000).toLong())
    }

    /** La cola cubre el rebote de la habitación, que el micrófono sigue oyendo un poco. */
    private fun markSounding(lengthMs: Long) {
        soundingUntil = SystemClock.elapsedRealtime() + lengthMs + TAIL_MS
    }

    private fun pluck(frequency: Float): ShortArray {
        val total = (SAMPLE_RATE * SUSTAIN_SECONDS).toInt()
        val period = (SAMPLE_RATE / frequency).toInt().coerceIn(2, SAMPLE_RATE / 16)
        val ring = FloatArray(period) { Random.nextFloat() * 2f - 1f }
        // Redondea el ruido inicial: en crudo el ataque suena a chasquido.
        for (i in ring.indices) ring[i] = (ring[i] + ring[(i + 1) % period]) * 0.5f

        // Cada vuelta del anillo atenúa, de modo que al final queda al 25% del volumen.
        val decay = 0.25.pow(1.0 / (SUSTAIN_SECONDS * frequency)).toFloat()
        val pcm = ShortArray(total)
        var index = 0
        for (i in 0 until total) {
            val value = ring[index]
            ring[index] = (value + ring[(index + 1) % period]) * 0.5f * decay
            index = (index + 1) % period
            val fade = 1f - i.toFloat() / total
            pcm[i] = (value * fade * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    private fun musicAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun stopCurrent() {
        player?.runCatching { stop(); release() }
        player = null
        track?.runCatching { stop(); release() }
        track = null
    }

    private companion object {
        const val TAG = "ReferenceTonePlayer"
        const val DIRECTORY = "strings"
        val EXTENSIONS = listOf("wav", "mp3", "ogg", "m4a", "flac")
        const val SAMPLE_RATE = 44_100
        const val SUSTAIN_SECONDS = 1.6f
        const val AMPLITUDE = 0.55f
        /** Silencio mínimo desde el toque, hasta saber la duración real del audio. */
        const val MIN_SOUNDING_MS = 400L
        const val UNKNOWN_LENGTH_MS = 2_000L
        const val TAIL_MS = 250L
    }
}
