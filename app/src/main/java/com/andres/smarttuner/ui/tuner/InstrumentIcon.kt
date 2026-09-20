package com.andres.smarttuner.ui.tuner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.andres.smarttuner.R
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.ui.components.spinOnTap
import com.andres.smarttuner.ui.theme.TunerTheme

private const val QUESTION_ICON = "question.svg"

/**
 * Icono de línea del instrumento (SVG desde assets), teñido con el color del tema.
 * Se usa donde el dibujo va pequeño y el neón no se leería. Con `null` (instrumento no
 * reconocido) muestra el signo de interrogación.
 */
@Composable
fun InstrumentIcon(
    instrument: Instrument?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = TunerTheme.colors.accent,
) {
    val context = LocalContext.current
    val asset = instrument?.iconAsset ?: QUESTION_ICON
    val request = remember(asset) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/$asset")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

/**
 * El instrumento dibujado como un tubo de neón del color del tema. Al tocarlo da una vuelta
 * rápida, igual que el logo de la app.
 */
@Composable
fun NeonInstrumentIcon(
    instrument: Instrument?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = TunerTheme.colors.accent,
) {
    Image(
        painter = painterResource(instrument.neonIcon),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.spinOnTap(contentDescription),
    )
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

@get:DrawableRes
private val Instrument?.neonIcon: Int
    get() = when (this) {
        Instrument.GUITAR -> R.drawable.neon_guitar
        Instrument.BASS -> R.drawable.neon_bass
        Instrument.VIOLIN, Instrument.VIOLA -> R.drawable.neon_violin
        Instrument.CELLO -> R.drawable.neon_chelo
        Instrument.UKULELE -> R.drawable.neon_ukulele
        Instrument.OTHER, null -> R.drawable.neon_question
    }
