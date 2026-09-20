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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.R
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentString
import com.andres.smarttuner.music.Tuning
import com.andres.smarttuner.tuner.TunerMode
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.components.BackChevron
import com.andres.smarttuner.ui.components.IconCircleButton
import com.andres.smarttuner.ui.components.ScreenColumn
import com.andres.smarttuner.ui.components.StatusText
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.TunerTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Afinación por cuerdas de un instrumento: detecta la cuerda al aire más cercana —o la que
 * elijas a mano—, muestra su desviación y marca las que ya quedaron afinadas.
 */
@Composable
fun InstrumentTuningScreen(
    state: TunerUiState,
    instrument: Instrument,
    tuning: Tuning,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onSelectString: (Int) -> Unit,
    onSelectTuning: (Tuning) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    // De grave a agudo, igual que el dial (en el ukelele queda el orden tradicional G C E A).
    val strings = remember(tuning) { tuning.strings.sortedByDescending { it.number } }
    val match = state.stringMatch
    val active = state.hasSignal && match != null
    val cents = match?.cents ?: 0f
    val inTune = active && abs(cents) <= TunerUiState.IN_TUNE_CENTS
    var showTunings by rememberSaveable { mutableStateOf(false) }

    val color by animateColorAsState(tuningColor(cents, active, colors), tween(250), label = "stringColor")
    val animatedCents by animateFloatAsState(
        targetValue = if (active) cents else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "stringCents",
    )

    val activeIndex = match?.let { m -> strings.indexOfFirst { it.number == m.string.number } } ?: -1
    val lowerString = strings.getOrNull(activeIndex - 1).takeIf { activeIndex > 0 }
    val upperString = strings.getOrNull(activeIndex + 1).takeIf { activeIndex >= 0 }

    val sounding = state.soundingString
    val statusText = when {
        sounding != null -> stringResource(R.string.tuning_string_sounding, sounding)
        match == null || !active -> stringResource(R.string.tuning_play_open_string)
        inTune -> stringResource(R.string.tuning_string_in_tune, match.string.number)
        cents < 0f -> stringResource(R.string.tuning_string_flat, match.string.number)
        else -> stringResource(R.string.tuning_string_sharp, match.string.number)
    }

    ScreenColumn(modifier) {
        InstrumentHeader(
            instrument = instrument,
            tuning = tuning,
            referenceA4 = state.referenceA4,
            onBack = onBack,
            onOpenTunings = { showTunings = true },
            onOpenAppearance = onOpenAppearance,
        )
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
        StatusText(
            // Mientras suena la referencia el estado no habla de afinación, así que va en el
            // color del tema y no en el del afinador.
            text = statusText,
            color = if (sounding != null) colors.accent else color,
            idle = sounding == null && !active,
            pulsing = sounding != null || (!active && state.isListening),
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            strings.forEach { string ->
                val isActive = active && match?.string?.number == string.number
                StringCard(
                    string = string,
                    isActive = isActive,
                    isSelected = state.selectedString == string.number,
                    isTuned = string.number in state.tunedStrings,
                    cents = if (isActive) cents else 0f,
                    activeColor = color,
                    style = state.accidentalStyle,
                    referenceA4 = state.referenceA4,
                    onSelect = { onSelectString(string.number) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
        FooterHint(state, strings.size)
        Spacer(Modifier.weight(1f))
    }

    if (showTunings) {
        TuningSheet(
            instrument = instrument,
            selected = tuning,
            style = state.accidentalStyle,
            onSelect = onSelectTuning,
            onDismiss = { showTunings = false },
        )
    }
}

@Composable
private fun FooterHint(state: TunerUiState, stringCount: Int) {
    val colors = TunerTheme.colors
    val selected = state.selectedString
    val allTuned = state.tunedStrings.size == stringCount
    val text = when {
        selected != null -> stringResource(R.string.tuning_string_selected, selected)
        allTuned -> stringResource(R.string.tuning_all_done)
        else -> stringResource(R.string.tuning_progress, state.tunedStrings.size, stringCount)
    }
    Text(
        text = text,
        color = if (allTuned && selected == null) colors.inTune else colors.textMuted,
        style = TunerTheme.typography.bodySmall.copy(
            fontWeight = if (allTuned && selected == null) FontWeight.SemiBold else FontWeight.Normal,
        ),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun InstrumentHeader(
    instrument: Instrument,
    tuning: Tuning,
    referenceA4: Float,
    onBack: () -> Unit,
    onOpenTunings: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    val sizes = TunerTheme.sizes
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(onClick = onBack, contentDescription = stringResource(R.string.tuning_back)) {
            BackChevron()
        }
        Spacer(Modifier.width(spacing.sm))
        NeonInstrumentIcon(
            instrument = instrument,
            contentDescription = instrument.displayName,
            modifier = Modifier.size(sizes.avatar),
        )
        Spacer(Modifier.width(spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = instrument.displayName,
                color = colors.textPrimary,
                style = TunerTheme.typography.title,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TuningChip(tuning.name, onOpenTunings)
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = stringResource(R.string.reference_label, referenceA4.roundToInt()),
                    color = colors.textMuted,
                    style = TunerTheme.typography.footnote,
                    maxLines = 1,
                )
            }
        }
        AppearanceButton(onOpenAppearance)
    }
}

/** Píldora con la variante en uso; al tocarla se puede cambiar. */
@Composable
private fun TuningChip(name: String, onClick: () -> Unit) {
    val colors = TunerTheme.colors
    val shape = TunerTheme.shapes.pill
    Row(
        Modifier
            .clip(shape)
            .background(colors.accent.copy(alpha = 0.16f))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tuning_type_change), onClick = onClick)
            .padding(horizontal = TunerTheme.spacing.sm, vertical = TunerTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = colors.accent, style = TunerTheme.typography.footnote, maxLines = 1)
        Spacer(Modifier.width(TunerTheme.spacing.xxs))
        Text("▾", color = colors.accent, style = TunerTheme.typography.footnote)
    }
}

/**
 * Tarjeta de una cuerda. Se toca para elegir cuál afinar, y al elegirla suena como referencia.
 * Mientras te acercas a la afinación late cada vez más rápido y con más brillo; al quedar
 * afinada lanza una onda que se expande desde la tarjeta.
 */
@Composable
private fun StringCard(
    string: InstrumentString,
    isActive: Boolean,
    isSelected: Boolean,
    isTuned: Boolean,
    cents: Float,
    activeColor: Color,
    style: AccidentalStyle,
    referenceA4: Float,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val typography = TunerTheme.typography
    val shape = TunerTheme.shapes.tile
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            isSelected -> colors.accent
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
            .background(
                when {
                    isTuned -> colors.inTune.copy(alpha = 0.12f)
                    isSelected -> colors.accent.copy(alpha = 0.12f)
                    else -> colors.surface
                },
            )
            .border(TunerTheme.sizes.borderStrong, borderColor, shape)
            .selectableCard(isSelected, string.number, onSelect)
            .padding(vertical = TunerTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = string.number.toString(),
            color = when {
                isTuned -> colors.inTune
                isActive -> activeColor
                isSelected -> colors.accent
                else -> colors.textMuted
            },
            style = typography.overline,
        )
        Text(string.note(style).label, color = colors.textPrimary, style = typography.noteLabel, maxLines = 1)
        Text(
            text = String.format(Locale.US, "%.1f", string.frequency(referenceA4)),
            color = colors.textMuted,
            style = typography.tiny,
            maxLines = 1,
        )
    }
}

/** La tarjeta entera elige la cuerda; se anuncia como opción seleccionable. */
@Composable
private fun Modifier.selectableCard(selected: Boolean, number: Int, onSelect: () -> Unit): Modifier {
    val label = stringResource(R.string.tuning_select_string, number)
    return selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .semantics { contentDescription = label }
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
                mode = TunerMode.InstrumentTuning(Instrument.GUITAR, Instrument.GUITAR.standardTuning),
                tunedStrings = setOf(6, 4),
            ),
            instrument = Instrument.GUITAR,
            tuning = Instrument.GUITAR.standardTuning,
            onBack = {},
            onOpenAppearance = {},
            onSelectString = {},
            onSelectTuning = {},
        )
    }
}
