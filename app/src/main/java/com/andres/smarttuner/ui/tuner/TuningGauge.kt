package com.andres.smarttuner.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.theme.InTune
import com.andres.smarttuner.ui.theme.Night
import com.andres.smarttuner.ui.theme.NearlyInTune
import com.andres.smarttuner.ui.theme.OutOfTune
import com.andres.smarttuner.ui.theme.TextMuted
import com.andres.smarttuner.ui.theme.TextPrimary
import com.andres.smarttuner.ui.theme.TickColor
import com.andres.smarttuner.ui.theme.TrackColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MAX_CENTS = 50f
private const val HALF_SWEEP_DEGREES = 70f

/** Verde afinado → ámbar cerca → coral desafinado. */
fun tuningColor(cents: Float, hasSignal: Boolean): Color {
    if (!hasSignal) return TextMuted
    val deviation = abs(cents)
    return when {
        deviation <= TunerUiState.IN_TUNE_CENTS -> InTune
        deviation <= 20f -> lerp(InTune, NearlyInTune, (deviation - 5f) / 15f)
        else -> lerp(NearlyInTune, OutOfTune, ((deviation - 20f) / 30f).coerceAtMost(1f))
    }
}

/**
 * Arco de -50 a +50 cents. El lado izquierdo es grave, el derecho agudo;
 * el marcador recorre el arco según la desviación.
 */
@Composable
fun TuningGauge(
    cents: Float,
    hasSignal: Boolean,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)

    Canvas(modifier) {
        val topPadding = 30.dp.toPx()
        val radius = size.width / 2f - 28.dp.toPx()
        val center = Offset(size.width / 2f, topPadding + radius)
        val trackWidth = 10.dp.toPx()
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        fun pointAt(distance: Float, angleDegrees: Float): Offset {
            val radians = Math.toRadians(angleDegrees.toDouble())
            return Offset(
                x = center.x + distance * cos(radians).toFloat(),
                y = center.y + distance * sin(radians).toFloat(),
            )
        }

        // Pista base.
        drawArc(
            color = TrackColor,
            startAngle = -90f - HALF_SWEEP_DEGREES,
            sweepAngle = HALF_SWEEP_DEGREES * 2f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(trackWidth, cap = StrokeCap.Round),
        )

        // Zona afinada (±5 cents).
        val zoneSweep = TunerUiState.IN_TUNE_CENTS / MAX_CENTS * HALF_SWEEP_DEGREES * 2f
        drawArc(
            color = InTune.copy(alpha = 0.35f),
            startAngle = -90f - zoneSweep / 2f,
            sweepAngle = zoneSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(trackWidth),
        )

        // Relleno desde el centro hasta la desviación actual.
        val indicatorAngle = centsToAngle(cents)
        if (hasSignal) {
            drawArc(
                color = indicatorColor.copy(alpha = 0.6f),
                startAngle = -90f,
                sweepAngle = indicatorAngle + 90f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(trackWidth * 0.5f, cap = StrokeCap.Round),
            )
        }

        // Marcas cada 5 cents.
        val tickOuter = radius - trackWidth - 4.dp.toPx()
        for (tick in -50..50 step 5) {
            val major = tick % 25 == 0
            val angle = centsToAngle(tick.toFloat())
            val length = if (major) 14.dp.toPx() else 7.dp.toPx()
            drawLine(
                color = if (tick == 0) TextPrimary else TickColor,
                start = pointAt(tickOuter - length, angle),
                end = pointAt(tickOuter, angle),
                strokeWidth = if (major) 2.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Etiquetas exteriores.
        for (tick in listOf(-50, -25, 0, 25, 50)) {
            val layout = textMeasurer.measure(if (tick > 0) "+$tick" else "$tick", labelStyle)
            val position = pointAt(radius + trackWidth / 2f + 12.dp.toPx(), centsToAngle(tick.toFloat()))
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(position.x - layout.size.width / 2f, position.y - layout.size.height / 2f),
            )
        }

        // Marcador.
        val markerColor = if (hasSignal) indicatorColor else TextMuted.copy(alpha = 0.5f)
        val marker = pointAt(radius, indicatorAngle)
        drawCircle(markerColor.copy(alpha = 0.25f), radius = 17.dp.toPx(), center = marker)
        drawCircle(markerColor, radius = 9.dp.toPx(), center = marker)
        drawCircle(Night, radius = 3.5.dp.toPx(), center = marker)
    }
}

private fun centsToAngle(cents: Float): Float =
    -90f + cents.coerceIn(-MAX_CENTS, MAX_CENTS) / MAX_CENTS * HALF_SWEEP_DEGREES
