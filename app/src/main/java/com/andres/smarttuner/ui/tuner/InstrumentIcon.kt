package com.andres.smarttuner.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.andres.smarttuner.music.Instrument

private const val QUESTION_ICON = "question.svg"

/** Icono SVG desde assets. Con `null` (instrumento no reconocido) muestra el signo de interrogación. */
@Composable
fun InstrumentIcon(
    instrument: Instrument?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val asset = instrument?.iconAsset ?: QUESTION_ICON
    val request = remember(asset) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/$asset")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
    AsyncImage(model = request, contentDescription = contentDescription, modifier = modifier)
}

private val Instrument.iconAsset: String
    get() = when (this) {
        Instrument.GUITAR -> "guitar.svg"
        Instrument.BASS -> "bass.svg"
        Instrument.VIOLIN, Instrument.VIOLA -> "violin.svg"
        Instrument.CELLO -> "chelo.svg"
        Instrument.UKULELE -> "ukulele.svg"
        Instrument.OTHER -> QUESTION_ICON
    }
