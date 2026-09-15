package com.andres.smarttuner.ui.tuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andres.smarttuner.R
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.NoteName
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.tuner.TunerViewModel
import com.andres.smarttuner.tuner.TuningStatus
import com.andres.smarttuner.ui.theme.Accent
import com.andres.smarttuner.ui.theme.Night
import com.andres.smarttuner.ui.theme.NightDeep
import com.andres.smarttuner.ui.theme.NightSurface
import com.andres.smarttuner.ui.theme.NightSurfaceHigh
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.TextMuted
import com.andres.smarttuner.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.roundToInt

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

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightSurface, Night, NightDeep))),
    ) {
        if (hasPermission) {
            TunerScreen(
                state = state,
                onToggleAccidentals = viewModel::toggleAccidentalStyle,
                onChangeReference = viewModel::changeReference,
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
    onToggleAccidentals: () -> Unit,
    onChangeReference: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = tuningColor(state.cents, state.hasSignal),
        animationSpec = tween(250),
        label = "tuningColor",
    )

    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(state.accidentalStyle, onToggleAccidentals)
        Spacer(Modifier.weight(1f))
        TunerDial(state, color)
        Spacer(Modifier.height(12.dp))
        ReadingsRow(state)
        Spacer(Modifier.height(12.dp))
        StatusPill(state, color)
        Spacer(Modifier.weight(1f))
        NoteStrip(state, color, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(12.dp))
        StaffCard(state, color)
        Spacer(Modifier.weight(1f))
        BottomBar(state, onChangeReference)
    }
}

@Composable
private fun TopBar(style: AccidentalStyle, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.tuner_subtitle), color = TextMuted, fontSize = 13.sp)
        }
        AccidentalToggle(style, onToggle)
    }
}

@Composable
private fun AccidentalToggle(style: AccidentalStyle, onToggle: () -> Unit) {
    val description = stringResource(R.string.toggle_accidentals)
    Row(
        Modifier
            .clip(CircleShape)
            .background(NightSurfaceHigh)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = description }
            .padding(4.dp),
    ) {
        listOf(AccidentalStyle.SHARPS to "♯", AccidentalStyle.FLATS to "♭").forEach { (option, symbol) ->
            val selected = option == style
            val background by animateColorAsState(if (selected) Accent else Color.Transparent, label = "toggleBg")
            val foreground by animateColorAsState(if (selected) NightDeep else TextMuted, label = "toggleFg")
            Box(
                Modifier
                    .size(width = 44.dp, height = 34.dp)
                    .clip(CircleShape)
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Text(symbol, color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TunerDial(state: TunerUiState, color: Color) {
    val animatedCents by animateFloatAsState(
        targetValue = if (state.hasSignal) state.cents else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "cents",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f),
    ) {
        TuningGauge(animatedCents, state.hasSignal, color, Modifier.matchParentSize())
        NoteDisplay(
            note = state.note,
            hasSignal = state.hasSignal,
            inTune = state.status == TuningStatus.IN_TUNE,
            color = color,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        NeighborLabel(
            title = stringResource(R.string.flat_side),
            note = state.lowerNeighbor,
            alignEnd = false,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        NeighborLabel(
            title = stringResource(R.string.sharp_side),
            note = state.upperNeighbor,
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
    modifier: Modifier = Modifier,
) {
    val contentAlpha by animateFloatAsState(if (hasSignal) 1f else 0.4f, tween(300), label = "noteAlpha")
    val glowAlpha by animateFloatAsState(if (inTune) 0.5f else 0f, tween(350), label = "glow")

    AnimatedContent(
        targetState = note,
        modifier = modifier
            .graphicsLayer { alpha = contentAlpha }
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
            },
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
                Text("–", color = TextMuted, fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.Light)
                Text(stringResource(R.string.play_a_note), color = TextMuted, fontSize = 15.sp)
            } else {
                Row {
                    Text(
                        text = current.letter.toString(),
                        color = TextPrimary,
                        fontSize = 104.sp,
                        lineHeight = 104.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.padding(top = 10.dp, start = 2.dp)) {
                        Text(
                            text = current.accidentalSymbol.ifEmpty { " " },
                            color = Accent,
                            fontSize = 42.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = current.octave.toString(),
                            color = TextMuted,
                            fontSize = 32.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(current.solfegeLabel, color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun NeighborLabel(title: String, note: NoteName?, alignEnd: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(bottom = 8.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(title, color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = note?.label ?: "–",
            color = TextPrimary.copy(alpha = 0.75f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReadingsRow(state: TunerUiState) {
    val hasNote = state.hasSignal && state.nearestMidi != null
    Row(Modifier.fillMaxWidth()) {
        Reading(
            label = stringResource(R.string.label_frequency),
            value = if (hasNote) String.format(Locale.US, "%.1f Hz", state.frequency) else "— Hz",
            modifier = Modifier.weight(1f),
        )
        Reading(
            label = stringResource(R.string.label_cents),
            value = if (hasNote) String.format(Locale.US, "%+.0f ¢", state.cents) else "— ¢",
            modifier = Modifier.weight(1f),
        )
        Reading(
            label = stringResource(R.string.label_target),
            value = if (state.nearestMidi != null) String.format(Locale.US, "%.1f Hz", state.targetFrequency) else "— Hz",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Reading(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusPill(state: TunerUiState, color: Color) {
    val text = when {
        state.errorMessage != null -> stringResource(R.string.error_microphone)
        state.status == TuningStatus.IN_TUNE -> stringResource(R.string.status_in_tune)
        state.status == TuningStatus.FLAT -> stringResource(R.string.status_flat)
        state.status == TuningStatus.SHARP -> stringResource(R.string.status_sharp)
        else -> stringResource(R.string.status_waiting)
    }
    val idle = state.status == TuningStatus.IDLE
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(color, pulsing = idle && state.isListening)
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = if (idle) TextMuted else color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { alpha = if (pulsing) pulse else 1f }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun BottomBar(state: TunerUiState, onChangeReference: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(if (state.isListening) R.string.listening else R.string.mic_paused),
            color = TextMuted,
            fontSize = 13.sp,
        )
        Row(
            Modifier
                .clip(CircleShape)
                .background(NightSurfaceHigh)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton("−", stringResource(R.string.decrease_reference)) { onChangeReference(-1f) }
            Text(
                text = stringResource(R.string.reference_label, state.referenceA4.roundToInt()),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            RoundButton("+", stringResource(R.string.increase_reference)) { onChangeReference(1f) }
        }
    }
}

@Composable
private fun RoundButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PermissionRequest(
    showSettingsShortcut: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.4f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎙️", fontSize = 52.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permission_title),
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            color = TextMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = NightDeep),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.permission_grant), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (showSettingsShortcut) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings), color = Accent)
            }
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
            onToggleAccidentals = {},
            onChangeReference = {},
        )
    }
}
