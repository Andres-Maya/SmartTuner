package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.music.MusicTheory
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.theme.TunerTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private const val VISIBLE_SEMITONES = 5f

/**
 * Carrusel cromático: el centro fijo representa el sonido que llega al micrófono
 * y las notas se deslizan de forma continua (izquierda = grave, derecha = aguda).
 * Cuando estás afinado, la nota queda exactamente bajo el marcador.
 */
@Composable
fun NoteStrip(state: TunerUiState, color: Color, modifier: Modifier = Modifier) {
    val colors = TunerTheme.colors
    val target = state.nearestMidi?.let { it + state.cents / 100f } ?: 69f
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "stripPosition",
    )
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val labelStyle = TunerTheme.typography.stripNote
    val active = state.hasSignal

    Canvas(
        modifier
            .clip(TunerTheme.shapes.panel)
            .background(colors.surface),
    ) {
        val spacing = size.width / VISIBLE_SEMITONES
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val first = floor(position - VISIBLE_SEMITONES / 2f - 1f).toInt()
        val last = ceil(position + VISIBLE_SEMITONES / 2f + 1f).toInt()

        // Subdivisiones cada 25 cents.
        for (midi in first..last) {
            for (quarter in 0..3) {
                val x = centerX + (midi + quarter / 4f - position) * spacing
                val tickHeight = if (quarter == 0) 8.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = colors.tick,
                    start = Offset(x, size.height - 6.dp.toPx() - tickHeight),
                    end = Offset(x, size.height - 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }

        for (midi in first..last) {
            val x = centerX + (midi - position) * spacing
            val emphasis = (1f - abs(midi - position) / 2.5f).coerceIn(0f, 1f)
            val layout = textMeasurer.measure(MusicTheory.noteName(midi, state.accidentalStyle).label, labelStyle)
            val textColor = if (active && midi == state.nearestMidi) {
                lerp(colors.textPrimary, color, emphasis)
            } else {
                colors.textPrimary
            }
            scale(scale = 0.6f + 0.4f * emphasis, pivot = Offset(x, centerY - 4.dp.toPx())) {
                drawText(
                    textLayoutResult = layout,
                    color = textColor,
                    topLeft = Offset(x - layout.size.width / 2f, centerY - 4.dp.toPx() - layout.size.height / 2f),
                    alpha = (0.2f + 0.8f * emphasis) * if (active) 1f else 0.55f,
                )
            }
        }

        // Desvanecido en los bordes.
        val fadeWidth = spacing * 0.9f
        drawRect(
            brush = Brush.horizontalGradient(listOf(colors.surface, Color.Transparent), startX = 0f, endX = fadeWidth),
            size = Size(fadeWidth, size.height),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, colors.surface),
                startX = size.width - fadeWidth,
                endX = size.width,
            ),
            topLeft = Offset(size.width - fadeWidth, 0f),
            size = Size(fadeWidth, size.height),
        )

        // Marcador central (triángulos arriba y abajo).
        val half = 6.dp.toPx()
        val markerColor = if (active) color else colors.tick
        drawPath(
            Path().apply {
                moveTo(centerX - half, 0f)
                lineTo(centerX + half, 0f)
                lineTo(centerX, half * 1.3f)
                close()
            },
            markerColor,
        )
        drawPath(
            Path().apply {
                moveTo(centerX - half, size.height)
                lineTo(centerX + half, size.height)
                lineTo(centerX, size.height - half * 1.3f)
                close()
            },
            markerColor,
        )
    }
}
