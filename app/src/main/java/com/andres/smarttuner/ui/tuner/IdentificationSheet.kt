package com.andres.smarttuner.ui.tuner

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.smarttuner.R
import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.ai.InstrumentCandidate
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentFamily
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
    onAccept: (Instrument?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == IdentificationUiState.Hidden) return

    // La IA preselecciona el más probable; el usuario puede elegir otro de las opciones.
    val identified = (state as? IdentificationUiState.Finished)?.outcome as? IdentificationOutcome.Identified
    var selected by rememberSaveable(identified) { mutableStateOf(identified?.best?.instrument) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NightSurface,
        contentColor = TextPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                            is IdentificationOutcome.Identified -> IdentifiedContent(
                                outcome = outcome,
                                selected = selected ?: outcome.best.instrument,
                                onSelect = { selected = it },
                            )
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
                val tuningInstrument = selected?.takeIf { identified != null }?.takeUnless { it.isChromatic }
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
                        onClick = { onAccept(if (identified != null) selected else null) },
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
private fun IdentifiedContent(
    outcome: IdentificationOutcome.Identified,
    selected: Instrument,
    onSelect: (Instrument) -> Unit,
) {
    val best = outcome.best
    val selectedCandidate = outcome.candidates.first { it.instrument == selected }
    val confidence = Confidence.of(selectedCandidate.probability)

    Crossfade(targetState = selected, label = "selectedInstrument") { instrument ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val name = instrumentName(instrument, outcome.otherLabel)
            InstrumentIcon(instrument, contentDescription = name, modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(10.dp))
            Text(text = name, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(8.dp))
    Surface(shape = CircleShape, color = confidence.color.copy(alpha = 0.14f)) {
        Text(
            text = stringResource(
                R.string.identify_confidence,
                (selectedCandidate.probability * 100).roundToInt(),
                stringResource(confidence.label),
            ),
            color = confidence.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (selected != best.instrument) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.identify_selected_by_user, instrumentName(best.instrument, outcome.otherLabel)),
            color = TextMuted,
            fontSize = 12.sp,
        )
    }

    Spacer(Modifier.height(22.dp))
    Text(
        text = stringResource(R.string.identify_choose_title),
        color = TextMuted,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(R.string.identify_choose_hint),
        color = TextMuted,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        outcome.options.forEach { candidate ->
            CandidateOption(
                candidate = candidate,
                otherLabel = outcome.otherLabel,
                selected = candidate.instrument == selected,
                onSelect = { onSelect(candidate.instrument) },
            )
        }
    }

    if (best.instrument.family == InstrumentFamily.BOWED) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.identify_bowed_hint),
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CandidateOption(
    candidate: InstrumentCandidate,
    otherLabel: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor by animateColorAsState(if (selected) Accent else Color.Transparent, label = "optionBorder")
    val background by animateColorAsState(
        targetValue = if (selected) Accent.copy(alpha = 0.12f) else NightSurfaceHigh.copy(alpha = 0.5f),
        label = "optionBackground",
    )
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(candidate.probability) {
        fraction.animateTo(candidate.probability, tween(700))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.5.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentIcon(candidate.instrument, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = instrumentName(candidate.instrument, otherLabel),
            color = if (selected) TextPrimary else TextMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(NightSurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.value)
                    .clip(CircleShape)
                    .background(if (selected) Accent else TextMuted.copy(alpha = 0.6f)),
            )
        }
        Text(
            text = "${(candidate.probability * 100).roundToInt()}%",
            color = if (selected) TextPrimary else TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
        // onClick = null: la fila completa es la que se selecciona.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = TextMuted),
            modifier = Modifier.padding(start = 8.dp),
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
