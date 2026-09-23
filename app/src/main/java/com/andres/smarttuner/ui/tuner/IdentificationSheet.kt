package com.andres.smarttuner.ui.tuner

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.R
import com.andres.smarttuner.ai.IdentificationOutcome
import com.andres.smarttuner.ai.InstrumentCandidate
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.InstrumentFamily
import com.andres.smarttuner.music.Tuning
import com.andres.smarttuner.tuner.IdentificationUiState
import com.andres.smarttuner.ui.components.Badge
import com.andres.smarttuner.ui.components.GhostButton
import com.andres.smarttuner.ui.components.MessageBlock
import com.andres.smarttuner.ui.components.PrimaryButton
import com.andres.smarttuner.ui.components.ProbabilityBar
import com.andres.smarttuner.ui.components.SecondaryButton
import com.andres.smarttuner.ui.components.SectionLabel
import com.andres.smarttuner.ui.components.SelectableOption
import com.andres.smarttuner.ui.theme.TunerColors
import com.andres.smarttuner.ui.theme.TunerTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationSheet(
    state: IdentificationUiState,
    accidentalStyle: AccidentalStyle,
    onRetry: () -> Unit,
    onAccept: (Instrument?, Tuning?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == IdentificationUiState.Hidden) return

    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing

    // La IA preselecciona el más probable; el usuario puede elegir otro de las opciones.
    val identified = (state as? IdentificationUiState.Finished)?.outcome as? IdentificationOutcome.Identified
    var selected by rememberSaveable(identified) { mutableStateOf(identified?.best?.instrument) }
    // La variante vuelve a la estándar cada vez que cambia el instrumento elegido.
    var tuning by remember(selected) { mutableStateOf(selected?.standardTuning) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xxl)
                .padding(bottom = spacing.lg),
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
                                tuning = tuning ?: outcome.best.instrument.standardTuning,
                                accidentalStyle = accidentalStyle,
                                onSelect = { selected = it },
                                onSelectTuning = { tuning = it },
                            )
                            IdentificationOutcome.NoInstrument -> UnknownContent(
                                title = R.string.identify_none_title,
                                body = R.string.identify_none_body,
                            )
                        }
                        IdentificationUiState.Failed -> UnknownContent(
                            title = R.string.identify_error_title,
                            body = R.string.identify_error_body,
                        )
                        IdentificationUiState.Hidden -> Unit
                    }
                }
            }

            Spacer(Modifier.height(spacing.xxl))
            if (state is IdentificationUiState.Listening) {
                GhostButton(stringResource(R.string.identify_cancel), onClick = onDismiss)
            } else {
                val tuningInstrument = selected?.takeIf { identified != null }?.takeUnless { it.isChromatic }
                if (tuningInstrument != null) {
                    val label = tuning
                        ?.takeIf { tuningInstrument.tunings.size > 1 }
                        ?.let { "${tuningInstrument.displayName} · ${it.name}" }
                        ?: tuningInstrument.displayName
                    Text(
                        text = stringResource(R.string.identify_accept_hint, label),
                        color = colors.textMuted,
                        style = TunerTheme.typography.caption,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.md))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    SecondaryButton(
                        text = stringResource(R.string.identify_retry),
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.identify_accept),
                        onClick = { onAccept(if (identified != null) selected else null, tuning) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningContent(progress: Float) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    val typography = TunerTheme.typography
    val animatedProgress by animateFloatAsState(progress, tween(150), label = "identifyProgress")
    val transition = rememberInfiniteTransition(label = "rings")
    val ringPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "ringPhase",
    )

    Text(stringResource(R.string.identify_listening_title), color = colors.textPrimary, style = typography.title)
    Spacer(Modifier.height(spacing.sm))
    Text(
        text = stringResource(R.string.identify_listening_body),
        color = colors.textMuted,
        style = typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(spacing.xl))
    Box(
        Modifier
            .size(180.dp)
            .drawBehind {
                // Ondas que se expanden, como sonido saliendo del instrumento.
                for (ring in 0..2) {
                    val phase = (ringPhase + ring / 3f) % 1f
                    drawCircle(
                        color = colors.accent.copy(alpha = 0.3f * (1f - phase)),
                        radius = size.minDimension / 2f * (0.6f + 0.4f * phase),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(116.dp)
                .clip(TunerTheme.shapes.pill)
                .background(colors.backgroundDeep),
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(116.dp),
            color = colors.accent,
            trackColor = colors.surfaceHigh,
            strokeWidth = 6.dp,
            strokeCap = StrokeCap.Round,
        )
        Text("${(animatedProgress * 100).roundToInt()}%", color = colors.textPrimary, style = typography.progress)
    }
    Spacer(Modifier.height(spacing.md))
    Text(stringResource(R.string.identify_listening_footer), color = colors.textMuted, style = typography.footnote)
}

@Composable
private fun IdentifiedContent(
    outcome: IdentificationOutcome.Identified,
    selected: Instrument,
    tuning: Tuning,
    accidentalStyle: AccidentalStyle,
    onSelect: (Instrument) -> Unit,
    onSelectTuning: (Tuning) -> Unit,
) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    val typography = TunerTheme.typography
    val best = outcome.best
    val selectedCandidate = outcome.candidates.first { it.instrument == selected }
    val confidence = Confidence.of(selectedCandidate.probability)

    Spacer(Modifier.height(spacing.sm))
    Crossfade(targetState = selected, label = "selectedInstrument") { instrument ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val name = instrumentName(instrument, outcome.otherLabel)
            InstrumentGlyph(
                instrument = instrument,
                contentDescription = name,
                modifier = Modifier.size(TunerTheme.sizes.iconLarge),
            )
            Spacer(Modifier.height(spacing.lg))
            Text(text = name, color = colors.textPrimary, style = typography.screenTitle, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(spacing.sm))
    Badge(
        text = stringResource(
            R.string.identify_confidence,
            (selectedCandidate.probability * 100).roundToInt(),
            stringResource(confidence.label),
        ),
        color = confidence.color(colors),
    )
    if (selected != best.instrument) {
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = stringResource(R.string.identify_selected_by_user, instrumentName(best.instrument, outcome.otherLabel)),
            color = colors.textMuted,
            style = typography.footnote,
        )
    }

    Spacer(Modifier.height(spacing.xxl))
    SectionLabel(stringResource(R.string.identify_choose_title), Modifier.fillMaxWidth())
    Text(
        text = stringResource(R.string.identify_choose_hint),
        color = colors.textMuted,
        style = typography.footnote,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.xxs),
    )
    Spacer(Modifier.height(spacing.md))
    Column(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
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

    // Variantes del instrumento elegido: 7 cuerdas, bajo de 5, ukelele barítono…
    if (selected.tunings.size > 1) {
        Spacer(Modifier.height(spacing.xl))
        SectionLabel(stringResource(R.string.identify_type_title), Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.identify_type_hint),
            color = colors.textMuted,
            style = typography.footnote,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxs),
        )
        Spacer(Modifier.height(spacing.md))
        TuningOptions(
            instrument = selected,
            selected = tuning,
            onSelect = onSelectTuning,
            style = accidentalStyle,
        )
    }

    if (best.instrument.family == InstrumentFamily.BOWED) {
        Spacer(Modifier.height(spacing.lg))
        Text(
            text = stringResource(R.string.identify_bowed_hint),
            color = colors.textMuted,
            style = typography.footnote,
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
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    val typography = TunerTheme.typography
    val textColor = if (selected) colors.textPrimary else colors.textMuted

    SelectableOption(selected = selected, onSelect = onSelect) {
        InstrumentIcon(
            instrument = candidate.instrument,
            contentDescription = null,
            modifier = Modifier.size(TunerTheme.sizes.iconSmall),
            tint = if (selected) colors.accent else colors.textMuted,
        )
        Spacer(Modifier.width(spacing.md))
        Text(
            text = instrumentName(candidate.instrument, otherLabel),
            color = textColor,
            style = typography.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
        ProbabilityBar(
            fraction = candidate.probability,
            color = if (selected) colors.accent else colors.textMuted.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(candidate.probability * 100).roundToInt()}%",
            color = textColor,
            style = typography.caption,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun UnknownContent(@StringRes title: Int, @StringRes body: Int) {
    InstrumentGlyph(
        instrument = null,
        contentDescription = stringResource(R.string.unknown_instrument),
        modifier = Modifier.size(TunerTheme.sizes.iconLarge),
    )
    Spacer(Modifier.height(TunerTheme.spacing.lg))
    MessageBlock(title = stringResource(title), body = stringResource(body))
}

@Composable
private fun instrumentName(instrument: Instrument, otherLabel: String?): String =
    if (instrument == Instrument.OTHER && otherLabel != null) {
        stringResource(R.string.identify_other_named, OTHER_LABELS_ES[otherLabel] ?: otherLabel)
    } else {
        instrument.displayName
    }

private enum class Confidence(@StringRes val label: Int) {
    HIGH(R.string.confidence_high),
    MEDIUM(R.string.confidence_medium),
    LOW(R.string.confidence_low),
    ;

    fun color(colors: TunerColors): Color = when (this) {
        HIGH -> colors.inTune
        MEDIUM -> colors.nearlyInTune
        LOW -> colors.outOfTune
    }

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
