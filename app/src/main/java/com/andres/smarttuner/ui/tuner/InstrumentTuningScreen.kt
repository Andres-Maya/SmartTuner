package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.R
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentString
import com.andres.smarttuner.tuner.TunerMode
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.components.BackChevron
import com.andres.smarttuner.ui.components.IconCircleButton
import com.andres.smarttuner.ui.components.ScreenColumn
import com.andres.smarttuner.ui.components.StatusPill
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.TunerTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Afinación por cuerdas de un instrumento: detecta la cuerda al aire más cercana,
 * muestra su desviación y marca las cuerdas que ya quedaron afinadas.
 */
@Composable
fun InstrumentTuningScreen(
    state: TunerUiState,
    instrument: Instrument,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    // De grave a agudo, igual que el dial (en el ukelele queda el orden tradicional G C E A).
    val strings = remember(instrument) { instrument.strings.sortedByDescending { it.number } }
    val match = state.stringMatch
    val active = state.hasSignal && match != null
    val cents = match?.cents ?: 0f
    val inTune = active && abs(cents) <= TunerUiState.IN_TUNE_CENTS

    val color by animateColorAsState(tuningColor(cents, active, colors), tween(250), label = "stringColor")
    val animatedCents by animateFloatAsState(
        targetValue = if (active) cents else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "stringCents",
    )

    val activeIndex = match?.let { m -> strings.indexOfFirst { it.number == m.string.number } } ?: -1
    val lowerString = strings.getOrNull(activeIndex - 1).takeIf { activeIndex > 0 }
    val upperString = strings.getOrNull(activeIndex + 1).takeIf { activeIndex >= 0 }

    val statusText = when {
        match == null || !active -> stringResource(R.string.tuning_play_open_string)
        inTune -> stringResource(R.string.tuning_string_in_tune, match.string.number)
        cents < 0f -> stringResource(R.string.tuning_string_flat, match.string.number)
        else -> stringResource(R.string.tuning_string_sharp, match.string.number)
    }

    ScreenColumn(modifier) {
        InstrumentHeader(instrument, state.referenceA4, onBack, onOpenAppearance)
        Spacer(Modifier.weight(1f))
        TunerDial(
            cents = animatedCents,
            hasSignal = active,
            inTune = inTune,
            note = match?.string?.note(state.accidentalStyle),
            accidentalStyle = state.accidentalStyle,
            color = color,
            lowerNote = lowerString?.note(state.accidentalStyle),
            upperNote = upperString?.note(state.accidentalStyle),
            emptyLabel = stringResource(R.string.tuning_play_open_string),
        )
        Spacer(Modifier.height(spacing.md))
        ReadingsRow(
            frequency = if (active) formatHz(state.frequency) else EMPTY_HZ,
            cents = if (active) formatCents(cents) else EMPTY_CENTS,
            target = match?.string?.frequency(state.referenceA4)?.let(::formatHz) ?: EMPTY_HZ,
        )
        Spacer(Modifier.height(spacing.md))
        StatusPill(text = statusText, color = color, idle = !active, pulsing = !active && state.isListening)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            strings.forEach { string ->
                val isActive = active && match?.string?.number == string.number
                StringCard(
                    string = string,
                    isActive = isActive,
                    isTuned = string.number in state.tunedStrings,
                    cents = if (isActive) cents else 0f,
                    activeColor = color,
                    style = state.accidentalStyle,
                    referenceA4 = state.referenceA4,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(spacing.lg))
        val allTuned = state.tunedStrings.size == strings.size
        Text(
            text = if (allTuned) {
                stringResource(R.string.tuning_all_done)
            } else {
                stringResource(R.string.tuning_progress, state.tunedStrings.size, strings.size)
            },
            color = if (allTuned) colors.inTune else colors.textMuted,
            style = TunerTheme.typography.bodySmall.copy(
                fontWeight = if (allTuned) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun InstrumentHeader(
    instrument: Instrument,
    referenceA4: Float,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    val sizes = TunerTheme.sizes
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(onClick = onBack, contentDescription = stringResource(R.string.tuning_back)) {
            BackChevron()
        }
        Spacer(Modifier.width(spacing.md))
        Box(
            Modifier
                .size(sizes.avatar)
                .clip(TunerTheme.shapes.pill)
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            InstrumentIcon(instrument, contentDescription = null, modifier = Modifier.size(sizes.iconMedium))
        }
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f)) {
            Text(instrument.displayName, color = colors.textPrimary, style = TunerTheme.typography.title)
            Text(
                text = stringResource(R.string.tuning_subtitle, referenceA4.roundToInt()),
                color = colors.textMuted,
                style = TunerTheme.typography.caption,
            )
        }
        AppearanceButton(onOpenAppearance)
    }
}

/**
 * Tarjeta de una cuerda. Mientras te acercas a la afinación late cada vez más rápido y
 * con más brillo; al quedar afinada lanza una onda que se expande desde la tarjeta.
 */
@Composable
private fun StringCard(
    string: InstrumentString,
    isActive: Boolean,
    isTuned: Boolean,
    cents: Float,
    activeColor: Color,
    style: AccidentalStyle,
    referenceA4: Float,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val typography = TunerTheme.typography
    val shape = TunerTheme.shapes.tile
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            isTuned -> colors.inTune.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        label = "stringBorder",
    )
    val scale by animateFloatAsState(if (isActive) 1.08f else 1f, label = "stringScale")

    // 0 lejos, 1 justo en el centro: manda en la velocidad y el brillo del latido.
    val proximity = if (isActive && !isTuned) (1f - (abs(cents) / 50f).coerceIn(0f, 1f)) else 0f
    val beat by beatPhase(running = isActive && !isTuned) { 0.8f + 2.6f * proximity }
    val wave = remember { Animatable(0f) }
    LaunchedEffect(isTuned) {
        wave.snapTo(0f)
        if (isTuned) wave.animateTo(1f, tween(durationMillis = 900, easing = LinearOutSlowInEasing))
    }

    Column(
        modifier
            .drawBehind {
                val corner = 16.dp.toPx()
                // Aura que respira mientras la cuerda se acerca a su nota.
                val pulse = sin(beat * 2f * PI.toFloat()) * 0.5f + 0.5f
                val aura = proximity * (0.4f + 0.6f * pulse)
                if (aura > 0.01f) {
                    val spread = 12.dp.toPx()
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(activeColor.copy(alpha = 0.45f * aura), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.8f,
                        ),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2f, size.height + spread * 2f),
                    )
                }
                // Onda de confirmación: dos anillos que se expanden y se apagan.
                if (wave.value > 0f && wave.value < 1f) {
                    repeat(RINGS) { ring ->
                        val progress = (wave.value - ring * 0.2f) / (1f - ring * 0.2f)
                        if (progress <= 0f) return@repeat
                        val spread = progress * 22.dp.toPx()
                        drawRoundRect(
                            color = colors.inTune.copy(alpha = (1f - progress) * 0.55f),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + spread * 2f, size.height + spread * 2f),
                            cornerRadius = CornerRadius(corner + spread),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
            .graphicsLayer {
                val pulse = sin(beat * 2f * PI.toFloat()) * 0.5f + 0.5f
                val breath = 1f + 0.05f * proximity * pulse
                scaleX = scale * breath
                scaleY = scale * breath
            }
            .clip(shape)
            .background(if (isTuned) colors.inTune.copy(alpha = 0.12f) else colors.surface)
            .border(TunerTheme.sizes.borderStrong, borderColor, shape)
            .padding(vertical = TunerTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = string.number.toString(),
            color = when {
                isTuned -> colors.inTune
                isActive -> activeColor
                else -> colors.textMuted
            },
            style = typography.overline,
        )
        Text(string.note(style).label, color = colors.textPrimary, style = typography.noteLabel)
        Text(
            text = String.format(Locale.US, "%.1f", string.frequency(referenceA4)),
            color = colors.textMuted,
            style = typography.tiny,
        )
    }
}

private const val RINGS = 2

/**
 * Fase de 0 a 1 que avanza sola mientras [running] sea cierto. [speed] son vueltas por
 * segundo y se consulta en cada cuadro, así el latido se acelera sin saltos.
 */
@Composable
private fun beatPhase(running: Boolean, speed: () -> Float): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) {
            phase.floatValue = 0f
            return@LaunchedEffect
        }
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                phase.floatValue = (phase.floatValue + (now - previous) / 1_000_000_000f * speed()).mod(1f)
                previous = now
            }
        }
    }
    return phase
}

@Preview(showBackground = true, backgroundColor = 0xFF090D1C, widthDp = 360, heightDp = 780)
@Composable
private fun InstrumentTuningScreenPreview() {
    SmartTunerTheme {
        InstrumentTuningScreen(
            state = TunerUiState(
                isListening = true,
                hasSignal = true,
                frequency = 111f,
                nearestMidi = 45,
                cents = 15f,
                mode = TunerMode.InstrumentTuning(Instrument.GUITAR),
                tunedStrings = setOf(6, 4),
            ),
            instrument = Instrument.GUITAR,
            onBack = {},
            onOpenAppearance = {},
        )
    }
}
