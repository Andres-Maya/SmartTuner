package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.smarttuner.R
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentString
import com.andres.smarttuner.tuner.TunerMode
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.theme.InTune
import com.andres.smarttuner.ui.theme.NightSurface
import com.andres.smarttuner.ui.theme.NightSurfaceHigh
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.TextMuted
import com.andres.smarttuner.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Afinación por cuerdas de un instrumento: detecta la cuerda al aire más cercana,
 * muestra su desviación y marca las cuerdas que ya quedaron afinadas.
 */
@Composable
fun InstrumentTuningScreen(
    state: TunerUiState,
    instrument: Instrument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // De grave a agudo, igual que el dial (en el ukelele queda el orden tradicional G C E A).
    val strings = remember(instrument) { instrument.strings.sortedByDescending { it.number } }
    val match = state.stringMatch
    val active = state.hasSignal && match != null
    val cents = match?.cents ?: 0f
    val inTune = active && abs(cents) <= TunerUiState.IN_TUNE_CENTS

    val color by animateColorAsState(tuningColor(cents, active), tween(250), label = "stringColor")
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

    Column(
        modifier
            .fillMaxSize()
            .background(TunerBackground)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InstrumentHeader(instrument, state.referenceA4, onBack)
        Spacer(Modifier.weight(1f))
        TunerDial(
            cents = animatedCents,
            hasSignal = active,
            inTune = inTune,
            note = match?.string?.note(state.accidentalStyle),
            color = color,
            lowerNote = lowerString?.note(state.accidentalStyle),
            upperNote = upperString?.note(state.accidentalStyle),
            emptyLabel = stringResource(R.string.tuning_play_open_string),
        )
        Spacer(Modifier.height(12.dp))
        ReadingsRow(
            frequency = if (active) formatHz(state.frequency) else EMPTY_HZ,
            cents = if (active) formatCents(cents) else EMPTY_CENTS,
            target = match?.string?.frequency(state.referenceA4)?.let(::formatHz) ?: EMPTY_HZ,
        )
        Spacer(Modifier.height(12.dp))
        StatusPill(text = statusText, color = color, idle = !active, pulsing = !active && state.isListening)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            strings.forEach { string ->
                StringCard(
                    string = string,
                    isActive = active && match?.string?.number == string.number,
                    isTuned = string.number in state.tunedStrings,
                    activeColor = color,
                    style = state.accidentalStyle,
                    referenceA4 = state.referenceA4,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val allTuned = state.tunedStrings.size == strings.size
        Text(
            text = if (allTuned) {
                stringResource(R.string.tuning_all_done)
            } else {
                stringResource(R.string.tuning_progress, state.tunedStrings.size, strings.size)
            },
            color = if (allTuned) InTune else TextMuted,
            fontSize = 14.sp,
            fontWeight = if (allTuned) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun InstrumentHeader(instrument: Instrument, referenceA4: Float, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(NightSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            InstrumentIcon(instrument, contentDescription = null, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(instrument.displayName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.tuning_subtitle, referenceA4.roundToInt()),
                color = TextMuted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val description = stringResource(R.string.tuning_back)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(NightSurfaceHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val chevron = Path().apply {
                moveTo(size.width * 0.68f, size.height * 0.12f)
                lineTo(size.width * 0.28f, size.height * 0.5f)
                lineTo(size.width * 0.68f, size.height * 0.88f)
            }
            drawPath(
                path = chevron,
                color = TextPrimary,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun StringCard(
    string: InstrumentString,
    isActive: Boolean,
    isTuned: Boolean,
    activeColor: Color,
    style: AccidentalStyle,
    referenceA4: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            isTuned -> InTune.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        label = "stringBorder",
    )
    val scale by animateFloatAsState(if (isActive) 1.08f else 1f, label = "stringScale")

    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (isTuned) InTune.copy(alpha = 0.12f) else NightSurface)
            .border(1.5.dp, borderColor, shape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isTuned) "✓" else string.number.toString(),
            color = if (isTuned) InTune else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(string.note(style).label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            text = String.format(Locale.US, "%.1f", string.frequency(referenceA4)),
            color = TextMuted,
            fontSize = 10.sp,
        )
    }
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
        )
    }
}
