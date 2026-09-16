package com.andres.smarttuner.ui.tuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andres.smarttuner.R
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.NoteName
import com.andres.smarttuner.tuner.TunerMode
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.tuner.TunerViewModel
import com.andres.smarttuner.tuner.TuningStatus
import com.andres.smarttuner.ui.components.GhostButton
import com.andres.smarttuner.ui.components.MessageBlock
import com.andres.smarttuner.ui.components.PrimaryButton
import com.andres.smarttuner.ui.components.ScreenColumn
import com.andres.smarttuner.ui.components.SegmentedToggle
import com.andres.smarttuner.ui.components.StatRow
import com.andres.smarttuner.ui.components.StatusPill
import com.andres.smarttuner.ui.components.Stepper
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.TunerTheme
import java.util.Locale
import kotlin.math.roundToInt

internal const val EMPTY_HZ = "— Hz"
internal const val EMPTY_CENTS = "— ¢"

internal fun formatHz(hz: Float): String = String.format(Locale.US, "%.1f Hz", hz)

internal fun formatCents(cents: Float): String = String.format(Locale.US, "%+.0f ¢", cents)

@Composable
fun TunerRoute(viewModel: TunerViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(context.hasRecordAudioPermission()) }
    var hasAsked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        hasAsked = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !hasAsked) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // El usuario puede conceder el permiso desde Ajustes y volver a la app.
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasRecordAudioPermission()
        onPauseOrDispose { }
    }

    // El micrófono solo está abierto mientras la app es visible.
    LifecycleStartEffect(hasPermission) {
        if (hasPermission) viewModel.startListening()
        onStopOrDispose { viewModel.stopListening() }
    }

    KeepScreenOn()

    BackHandler(enabled = state.mode is TunerMode.InstrumentTuning, onBack = viewModel::closeInstrumentTuning)

    Box(
        Modifier
            .fillMaxSize()
            .background(TunerTheme.colors.screenBrush),
    ) {
        if (hasPermission) {
            AnimatedContent(
                targetState = state.mode,
                transitionSpec = {
                    // La afinación por instrumento entra desde la derecha; al volver, el afinador desde la izquierda.
                    val direction = if (targetState is TunerMode.InstrumentTuning) 1 else -1
                    (slideInHorizontally(tween(320)) { it * direction } + fadeIn(tween(320)))
                        .togetherWith(slideOutHorizontally(tween(320)) { -it * direction / 3 } + fadeOut(tween(320)))
                },
                label = "tunerMode",
            ) { mode ->
                when (mode) {
                    TunerMode.Chromatic -> TunerScreen(
                        state = state,
                        onSelectAccidentalStyle = viewModel::setAccidentalStyle,
                        onChangeReference = viewModel::changeReference,
                        onIdentifyInstrument = viewModel::identifyInstrument,
                    )
                    is TunerMode.InstrumentTuning -> InstrumentTuningScreen(
                        state = state,
                        instrument = mode.instrument,
                        onBack = viewModel::closeInstrumentTuning,
                    )
                }
            }
            IdentificationSheet(
                state = state.identification,
                onRetry = viewModel::identifyInstrument,
                onAccept = viewModel::acceptIdentification,
                onDismiss = viewModel::dismissIdentification,
            )
        } else {
            PermissionRequest(
                showSettingsShortcut = hasAsked,
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
fun TunerScreen(
    state: TunerUiState,
    onSelectAccidentalStyle: (AccidentalStyle) -> Unit,
    onChangeReference: (Float) -> Unit,
    onIdentifyInstrument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TunerTheme.spacing
    val color by animateColorAsState(
        targetValue = tuningColor(state.cents, state.hasSignal, TunerTheme.colors),
        animationSpec = tween(250),
        label = "tuningColor",
    )
    val animatedCents by animateFloatAsState(
        targetValue = if (state.hasSignal) state.cents else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "cents",
    )
    val hasNote = state.hasSignal && state.nearestMidi != null
    val idle = state.status == TuningStatus.IDLE

    ScreenColumn(modifier) {
        TopBar(state.accidentalStyle, onSelectAccidentalStyle)
        Spacer(Modifier.weight(1f))
        TunerDial(
            cents = animatedCents,
            hasSignal = state.hasSignal,
            inTune = state.status == TuningStatus.IN_TUNE,
            note = state.note,
            color = color,
            lowerNote = state.lowerNeighbor,
            upperNote = state.upperNeighbor,
            emptyLabel = stringResource(R.string.play_a_note),
        )
        Spacer(Modifier.height(spacing.md))
        ReadingsRow(
            frequency = if (hasNote) formatHz(state.frequency) else EMPTY_HZ,
            cents = if (hasNote) formatCents(state.cents) else EMPTY_CENTS,
            target = if (state.nearestMidi != null) formatHz(state.targetFrequency) else EMPTY_HZ,
        )
        Spacer(Modifier.height(spacing.md))
        StatusPill(
            text = chromaticStatusText(state),
            color = color,
            idle = idle,
            pulsing = idle && state.isListening,
        )
        Spacer(Modifier.weight(1f))
        NoteStrip(state, color, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(spacing.md))
        StaffCard(state, color)
        Spacer(Modifier.weight(1f))
        BottomBar(state, onIdentifyInstrument, onChangeReference)
    }
}

@Composable
private fun chromaticStatusText(state: TunerUiState): String = when {
    state.errorMessage != null -> stringResource(R.string.error_microphone)
    state.status == TuningStatus.IN_TUNE -> stringResource(R.string.status_in_tune)
    state.status == TuningStatus.FLAT -> stringResource(R.string.status_flat)
    state.status == TuningStatus.SHARP -> stringResource(R.string.status_sharp)
    else -> stringResource(R.string.status_waiting)
}

@Composable
private fun TopBar(style: AccidentalStyle, onSelect: (AccidentalStyle) -> Unit) {
    val colors = TunerTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), color = colors.textPrimary, style = TunerTheme.typography.appTitle)
            Text(stringResource(R.string.tuner_subtitle), color = colors.textMuted, style = TunerTheme.typography.caption)
        }
        SegmentedToggle(
            options = listOf(AccidentalStyle.SHARPS to "♯", AccidentalStyle.FLATS to "♭"),
            selected = style,
            onSelect = onSelect,
            contentDescription = stringResource(R.string.toggle_accidentals),
        )
    }
}

/** Arco de afinación con la nota objetivo en el centro y sus vecinas grave y aguda a los lados. */
@Composable
internal fun TunerDial(
    cents: Float,
    hasSignal: Boolean,
    inTune: Boolean,
    note: NoteName?,
    color: Color,
    lowerNote: NoteName?,
    upperNote: NoteName?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.3f),
    ) {
        TuningGauge(cents, hasSignal, color, Modifier.matchParentSize())
        NoteDisplay(
            note = note,
            hasSignal = hasSignal,
            inTune = inTune,
            color = color,
            emptyLabel = emptyLabel,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        NeighborLabel(
            title = stringResource(R.string.flat_side),
            note = lowerNote,
            alignEnd = false,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        NeighborLabel(
            title = stringResource(R.string.sharp_side),
            note = upperNote,
            alignEnd = true,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun NoteDisplay(
    note: NoteName?,
    hasSignal: Boolean,
    inTune: Boolean,
    color: Color,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val typography = TunerTheme.typography
    val contentAlpha by animateFloatAsState(if (hasSignal) 1f else 0.4f, tween(300), label = "noteAlpha")
    val glowAlpha by animateFloatAsState(if (inTune) 0.5f else 0f, tween(350), label = "glow")

    AnimatedContent(
        targetState = note,
        // El brillo se dibuja fuera de la capa con alpha: dentro de ella quedaría recortado en un rectángulo.
        modifier = modifier
            .drawBehind {
                val radius = size.maxDimension * 0.8f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            }
            .graphicsLayer { alpha = contentAlpha },
        transitionSpec = {
            // Una nota más aguda entra por la derecha (lado agudo); una más grave, por la izquierda.
            val direction = if ((targetState?.midi ?: 0) >= (initialState?.midi ?: 0)) 1 else -1
            (slideInHorizontally(tween(260)) { it * direction / 2 } + fadeIn(tween(260)))
                .togetherWith(slideOutHorizontally(tween(260)) { -it * direction / 2 } + fadeOut(tween(200)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "note",
    ) { current ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (current == null) {
                Text("–", color = colors.textMuted, style = typography.notePlaceholder)
                Text(emptyLabel, color = colors.textMuted, style = typography.body)
            } else {
                Row {
                    Text(current.letter.toString(), color = colors.textPrimary, style = typography.noteLetter)
                    Column(Modifier.padding(top = TunerTheme.spacing.md, start = TunerTheme.spacing.xxs)) {
                        Text(
                            text = current.accidentalSymbol.ifEmpty { " " },
                            color = colors.accent,
                            style = typography.noteAccidental,
                        )
                        Text(current.octave.toString(), color = colors.textMuted, style = typography.noteOctave)
                    }
                }
                Text(current.solfegeLabel, color = colors.textMuted, style = typography.noteCaption)
            }
        }
    }
}

@Composable
private fun NeighborLabel(title: String, note: NoteName?, alignEnd: Boolean, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    Column(
        modifier.padding(bottom = TunerTheme.spacing.sm),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(title, color = colors.textMuted, style = TunerTheme.typography.overline)
        Text(
            text = note?.label ?: "–",
            color = colors.textPrimary.copy(alpha = 0.75f),
            style = TunerTheme.typography.noteLabel,
        )
    }
}

@Composable
internal fun ReadingsRow(frequency: String, cents: String, target: String) {
    StatRow(
        listOf(
            stringResource(R.string.label_frequency) to frequency,
            stringResource(R.string.label_cents) to cents,
            stringResource(R.string.label_target) to target,
        ),
    )
}

@Composable
private fun BottomBar(state: TunerUiState, onIdentifyInstrument: () -> Unit, onChangeReference: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TunerTheme.spacing.md),
    ) {
        PrimaryButton(
            text = stringResource(R.string.identify_instrument),
            onClick = onIdentifyInstrument,
            leading = "✦",
            height = TunerTheme.sizes.touchTarget,
            modifier = Modifier.weight(1f),
        )
        Stepper(
            label = stringResource(R.string.reference_label, state.referenceA4.roundToInt()),
            onDecrement = { onChangeReference(-1f) },
            onIncrement = { onChangeReference(1f) },
            decrementDescription = stringResource(R.string.decrease_reference),
            incrementDescription = stringResource(R.string.increase_reference),
        )
    }
}

@Composable
private fun PermissionRequest(
    showSettingsShortcut: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(TunerTheme.shapes.pill)
                .background(Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.4f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎙️", fontSize = 52.sp)
        }
        Spacer(Modifier.height(spacing.xxl))
        MessageBlock(
            title = stringResource(R.string.permission_title),
            body = stringResource(R.string.permission_body),
        )
        Spacer(Modifier.height(spacing.xxxl))
        PrimaryButton(
            text = stringResource(R.string.permission_grant),
            onClick = onRequest,
            height = TunerTheme.sizes.buttonLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showSettingsShortcut) {
            Spacer(Modifier.height(spacing.sm))
            GhostButton(
                text = stringResource(R.string.permission_open_settings),
                onClick = onOpenSettings,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun Context.hasRecordAudioPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true, backgroundColor = 0xFF090D1C, widthDp = 360, heightDp = 780)
@Composable
private fun TunerScreenPreview() {
    SmartTunerTheme {
        TunerScreen(
            state = TunerUiState(
                isListening = true,
                hasSignal = true,
                frequency = 233.8f,
                nearestMidi = 58,
                cents = 12f,
            ),
            onSelectAccidentalStyle = {},
            onChangeReference = {},
            onIdentifyInstrument = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090D1C, widthDp = 360, heightDp = 780)
@Composable
private fun PermissionRequestPreview() {
    SmartTunerTheme {
        Box(Modifier.background(TunerTheme.colors.screenBrush)) {
            PermissionRequest(showSettingsShortcut = true, onRequest = {}, onOpenSettings = {})
        }
    }
}
