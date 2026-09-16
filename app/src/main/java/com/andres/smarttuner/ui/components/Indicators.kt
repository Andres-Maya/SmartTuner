package com.andres.smarttuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.andres.smarttuner.ui.theme.TunerTheme

/** Estado actual con un punto de color que late mientras se espera sonido. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    idle: Boolean = false,
    pulsing: Boolean = false,
) {
    val spacing = TunerTheme.spacing
    Surface(
        modifier = modifier,
        shape = TunerTheme.shapes.pill,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(TunerTheme.sizes.border, color.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(horizontal = spacing.lg + spacing.xxs, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(color, pulsing)
            Spacer(Modifier.width(spacing.md))
            Text(
                text = text,
                color = if (idle) TunerTheme.colors.textMuted else color,
                style = TunerTheme.typography.status,
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
            .size(TunerTheme.sizes.statusDot)
            .graphicsLayer { alpha = if (pulsing) pulse else 1f }
            .clip(TunerTheme.shapes.pill)
            .background(color),
    )
}

/** Etiqueta pequeña con fondo tenue del color indicado (confianza, estado). */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = TunerTheme.shapes.pill, color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            color = color,
            style = TunerTheme.typography.caption,
            modifier = Modifier.padding(horizontal = TunerTheme.spacing.md, vertical = TunerTheme.spacing.xs + TunerTheme.spacing.xxs),
        )
    }
}

/** Dato con etiqueta encima: FRECUENCIA / 440.0 Hz. */
@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TunerTheme.colors.textMuted, style = TunerTheme.typography.statLabel)
        Text(value, color = TunerTheme.colors.textPrimary, style = TunerTheme.typography.statValue)
    }
}

/** Varios [StatItem] repartidos a lo ancho. */
@Composable
fun StatRow(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        stats.forEach { (label, value) -> StatItem(label, value, Modifier.weight(1f)) }
    }
}

/** Barra de progreso horizontal que se anima desde cero al aparecer. */
@Composable
fun ProbabilityBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = TunerTheme.colors.accent,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) { animated.animateTo(fraction.coerceIn(0f, 1f), tween(700)) }
    Box(
        modifier
            .height(TunerTheme.sizes.progressBar)
            .clip(TunerTheme.shapes.pill)
            .background(TunerTheme.colors.surfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.value)
                .clip(TunerTheme.shapes.pill)
                .background(color),
        )
    }
}
