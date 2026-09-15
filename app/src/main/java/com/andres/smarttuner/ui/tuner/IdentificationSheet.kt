package com.andres.smarttuner.ui.tuner

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.smarttuner.R
import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.ai.InstrumentCandidate
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.tuner.IdentificationUiState
import com.andres.smarttuner.ui.theme.Accent
import com.andres.smarttuner.ui.theme.InTune
import com.andres.smarttuner.ui.theme.NearlyInTune
import com.andres.smarttuner.ui.theme.NightDeep
import com.andres.smarttuner.ui.theme.NightSurface
import com.andres.smarttuner.ui.theme.NightSurfaceHigh
import com.andres.smarttuner.ui.theme.OutOfTune
import com.andres.smarttuner.ui.theme.TextMuted
import com.andres.smarttuner.ui.theme.TextPrimary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationSheet(
    state: IdentificationUiState,
    onRetry: () -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == IdentificationUiState.Hidden) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NightSurface,
        contentColor = TextPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "identification",
            ) { current ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (current) {
                        is IdentificationUiState.Listening -> ListeningContent(current.progress)
                        is IdentificationUiState.Finished -> when (val outcome = current.outcome) {
                            is IdentificationOutcome.Identified -> IdentifiedContent(outcome)
                            IdentificationOutcome.NoInstrument -> MessageContent(
                                title = R.string.identify_none_title,
                                body = R.string.identify_none_body,
                            )
                        }
                        IdentificationUiState.Failed -> MessageContent(
                            title = R.string.identify_error_title,
                            body = R.string.identify_error_body,
                        )
                        IdentificationUiState.Hidden -> Unit
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (state is IdentificationUiState.Listening) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.identify_cancel), color = TextMuted)
                }
            } else {
                val tuningInstrument = ((state as? IdentificationUiState.Finished)?.outcome as? IdentificationOutcome.Identified)
                    ?.best
                    ?.instrument
                    ?.takeUnless { it.isChromatic }
                if (tuningInstrument != null) {
                    Text(
                        text = stringResource(R.string.identify_accept_hint, tuningInstrument.displayName),
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(stringResource(R.string.identify_retry), color = Accent)
                    }
                    Button(
                        onClick = onAccept,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = NightDeep),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(stringResource(R.string.identify_accept), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningContent(progress: Float) {
    val animatedProgress by animateFloatAsState(progress, tween(150), label = "identifyProgress")
    val transition = rememberInfiniteTransition(label = "rings")
    val ringPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "ringPhase",
    )

    Text(stringResource(R.string.identify_listening_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.identify_listening_body),
        color = TextMuted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Box(
        Modifier
            .size(180.dp)
            .drawBehind {
                // Ondas que se expanden, como sonido saliendo del instrumento.
                for (ring in 0..2) {
                    val phase = (ringPhase + ring / 3f) % 1f
                    drawCircle(
                        color = Accent.copy(alpha = 0.3f * (1f - phase)),
                        radius = size.minDimension / 2f * (0.6f + 0.4f * phase),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(NightDeep),
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(116.dp),
            color = Accent,
            trackColor = NightSurfaceHigh,
            strokeWidth = 6.dp,
            strokeCap = StrokeCap.Round,
        )
        Text("${(animatedProgress * 100).roundToInt()}%", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.identify_listening_footer), color = TextMuted, fontSize = 12.sp)
}

@Composable
private fun IdentifiedContent(outcome: IdentificationOutcome.Identified) {
    val best = outcome.best
    val confidence = Confidence.of(best.probability)
    val name = instrumentName(best.instrument, outcome.otherLabel)

    InstrumentIcon(best.instrument, contentDescription = name, modifier = Modifier.size(96.dp))
    Spacer(Modifier.height(12.dp))
    Text(text = name, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Surface(shape = CircleShape, color = confidence.color.copy(alpha = 0.14f)) {
        Text(
            text = stringResource(
                R.string.identify_confidence,
                (best.probability * 100).roundToInt(),
                stringResource(confidence.label),
            ),
            color = confidence.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.identify_probabilities),
        color = TextMuted,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )
    outcome.candidates
        .take(3)
        .filter { it.probability >= 0.01f }
        .forEach { candidate ->
            Spacer(Modifier.height(10.dp))
            CandidateBar(candidate, outcome.otherLabel, highlighted = candidate == best)
        }

    if (best.instrument == Instrument.VIOLIN || best.instrument == Instrument.VIOLA) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.identify_violin_viola_hint),
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CandidateBar(candidate: InstrumentCandidate, otherLabel: String?, highlighted: Boolean) {
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(candidate.probability) {
        fraction.animateTo(candidate.probability, tween(700))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InstrumentIcon(candidate.instrument, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = instrumentName(candidate.instrument, otherLabel),
            color = if (highlighted) TextPrimary else TextMuted,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.width(104.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(NightSurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.value)
                    .clip(CircleShape)
                    .background(if (highlighted) Accent else TextMuted.copy(alpha = 0.6f)),
            )
        }
        Text(
            text = "${(candidate.probability * 100).roundToInt()}%",
            color = if (highlighted) TextPrimary else TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun MessageContent(@StringRes title: Int, @StringRes body: Int) {
    InstrumentIcon(
        instrument = null,
        contentDescription = stringResource(R.string.unknown_instrument),
        modifier = Modifier.size(88.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(title), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(body), color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun instrumentName(instrument: Instrument, otherLabel: String?): String =
    if (instrument == Instrument.OTHER && otherLabel != null) {
        stringResource(R.string.identify_other_named, OTHER_LABELS_ES[otherLabel] ?: otherLabel)
    } else {
        instrument.displayName
    }

private enum class Confidence(@StringRes val label: Int, val color: Color) {
    HIGH(R.string.confidence_high, InTune),
    MEDIUM(R.string.confidence_medium, NearlyInTune),
    LOW(R.string.confidence_low, OutOfTune),
    ;

    companion object {
        fun of(probability: Float) = when {
            probability >= 0.6f -> HIGH
            probability >= 0.4f -> MEDIUM
            else -> LOW
        }
    }
}

private val OTHER_LABELS_ES = mapOf(
    "Banjo" to "Banjo",
    "Sitar" to "Sitar",
    "Mandolin" to "Mandolina",
    "Zither" to "Cítara",
    "Harp" to "Arpa",
    "Keyboard (musical)" to "Teclado",
    "Piano" to "Piano",
    "Electric piano" to "Piano eléctrico",
    "Organ" to "Órgano",
    "Electronic organ" to "Órgano electrónico",
    "Hammond organ" to "Órgano Hammond",
    "Synthesizer" to "Sintetizador",
    "Harpsichord" to "Clavecín",
    "Accordion" to "Acordeón",
    "Drum kit" to "Batería",
    "Drum" to "Tambor",
    "Snare drum" to "Redoblante",
    "Bass drum" to "Bombo",
    "Timpani" to "Timbales",
    "Tabla" to "Tabla",
    "Cymbal" to "Platillo",
    "Percussion" to "Percusión",
    "Marimba, xylophone" to "Marimba",
    "Glockenspiel" to "Glockenspiel",
    "Vibraphone" to "Vibráfono",
    "Steelpan" to "Steelpan",
    "Tubular bells" to "Campanas tubulares",
    "Brass instrument" to "Metales",
    "Trumpet" to "Trompeta",
    "Trombone" to "Trombón",
    "French horn" to "Corno francés",
    "Wind instrument, woodwind instrument" to "Viento madera",
    "Flute" to "Flauta",
    "Saxophone" to "Saxofón",
    "Clarinet" to "Clarinete",
    "Harmonica" to "Armónica",
    "Bagpipes" to "Gaita",
    "Didgeridoo" to "Didgeridoo",
    "Theremin" to "Theremín",
    "Singing" to "Voz",
    "Choir" to "Coro",
)
