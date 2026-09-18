package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.ui.theme.TunerTheme

private const val QUESTION_ICON = "question.svg"

/**
 * Icono SVG desde assets, teñido con el color del tema para que siga el aspecto de la app.
 * Con `null` (instrumento no reconocido) muestra el signo de interrogación.
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
 * El mismo icono con un halo de luz que respira a su alrededor, para destacar el
 * instrumento en el identificador.
 */
@Composable
fun GlowingInstrumentIcon(
    instrument: Instrument?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    glowColor: Color = TunerTheme.colors.accent,
) {
    val transition = rememberInfiniteTransition(label = "instrumentGlow")
    val breath by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Box(
        modifier.drawBehind {
            val radius = size.maxDimension * 0.78f * breath
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.40f),
                        glowColor.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        InstrumentIcon(
            instrument = instrument,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            tint = glowColor,
        )
    }
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
