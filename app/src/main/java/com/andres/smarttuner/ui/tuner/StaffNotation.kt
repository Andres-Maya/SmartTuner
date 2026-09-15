package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.smarttuner.R
import com.andres.smarttuner.music.Clef
import com.andres.smarttuner.music.StaffLayout
import com.andres.smarttuner.music.StaffPosition
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.theme.NightSurface
import com.andres.smarttuner.ui.theme.StaffLineColor
import com.andres.smarttuner.ui.theme.TextMuted
import com.andres.smarttuner.ui.theme.TextPrimary
import kotlin.math.roundToInt

private const val TREBLE_CLEF = "𝄞" // 𝄞
private const val BASS_CLEF = "𝄢" // 𝄢

@Composable
fun StaffCard(state: TunerUiState, color: Color, modifier: Modifier = Modifier) {
    val note = state.note
    val position = note?.let(StaffLayout::position)
    val clefName = when (position?.clef) {
        Clef.BASS -> stringResource(R.string.clef_bass)
        else -> stringResource(R.string.clef_treble)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NightSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.staff_title),
                color = TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(clefName, color = TextMuted, fontSize = 12.sp)
        }
        StaffNotation(
            position = position,
            accidental = note?.accidentalSymbol.orEmpty(),
            label = note?.label,
            color = if (state.hasSignal) color else TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
    }
}

@Composable
fun StaffNotation(
    position: StaffPosition?,
    accidental: String,
    label: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedStep by animateFloatAsState(
        targetValue = (position?.staffStep ?: 4).toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "staffStep",
    )
    val textMeasurer = rememberTextMeasurer()
    val clef = position?.clef ?: Clef.TREBLE

    Canvas(modifier) {
        // Rango visible: pasos -4 (C2 / C4 con dos líneas adicionales) a 11, más margen para plicas.
        val halfSpace = size.height / 18f
        val middleY = size.height / 2f
        fun yOf(step: Float) = middleY + (4f - step) * halfSpace

        val startX = 2.dp.toPx()
        val endX = size.width - 2.dp.toPx()
        for (line in 0..4) {
            val y = yOf(line * 2f)
            drawLine(StaffLineColor, Offset(startX, y), Offset(endX, y), strokeWidth = 1.2.dp.toPx())
        }
        drawLine(StaffLineColor, Offset(startX, yOf(0f)), Offset(startX, yOf(8f)), strokeWidth = 1.2.dp.toPx())
        drawLine(StaffLineColor, Offset(endX, yOf(0f)), Offset(endX, yOf(8f)), strokeWidth = 2.5.dp.toPx())

        drawClef(textMeasurer, clef, halfSpace, ::yOf)

        if (position == null || label == null) return@Canvas

        val headX = size.width * 0.55f
        val y = yOf(animatedStep)
        val displayedStep = animatedStep.roundToInt()

        // Líneas adicionales.
        position.copy(staffStep = displayedStep).ledgerSteps.forEach { step ->
            drawLine(
                color = TextPrimary.copy(alpha = 0.6f),
                start = Offset(headX - 2.3f * halfSpace, yOf(step.toFloat())),
                end = Offset(headX + 2.3f * halfSpace, yOf(step.toFloat())),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        // Cabeza de nota (redonda rellena, inclinada).
        rotate(degrees = -20f, pivot = Offset(headX, y)) {
            drawOval(
                color = color,
                topLeft = Offset(headX - 1.4f * halfSpace, y - halfSpace),
                size = Size(2.8f * halfSpace, 2f * halfSpace),
            )
        }

        // Plica: hacia arriba bajo la 3ª línea, hacia abajo desde ella.
        val stemWidth = 1.6.dp.toPx()
        if (displayedStep < 4) {
            val x = headX + 1.25f * halfSpace
            drawLine(color, Offset(x, y - 0.3f * halfSpace), Offset(x, y - 7f * halfSpace), stemWidth, StrokeCap.Round)
        } else {
            val x = headX - 1.25f * halfSpace
            drawLine(color, Offset(x, y + 0.3f * halfSpace), Offset(x, y + 7f * halfSpace), stemWidth, StrokeCap.Round)
        }

        // Alteración.
        if (accidental.isNotEmpty()) {
            val layout = textMeasurer.measure(accidental, TextStyle(fontSize = (halfSpace * 4.5f).toSp()))
            drawText(
                textLayoutResult = layout,
                color = color,
                topLeft = Offset(headX - 2.4f * halfSpace - layout.size.width, y - layout.size.height / 2f),
            )
        }

        // Indicación de octava (8va / 8vb …).
        position.octaveShiftLabel?.let { shift ->
            val layout = textMeasurer.measure(
                shift,
                TextStyle(fontSize = 12.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
            )
            val shiftY = if (position.octaveShift > 0) yOf(12f) - layout.size.height else yOf(-4f)
            drawText(layout, color = TextMuted, topLeft = Offset(headX - layout.size.width / 2f, shiftY))
        }

        // Nombre de la nota junto a la figura.
        val nameLayout = textMeasurer.measure(label, TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        drawText(
            textLayoutResult = nameLayout,
            color = color,
            topLeft = Offset(headX + 3.5f * halfSpace, y - nameLayout.size.height / 2f),
        )
    }
}

private fun DrawScope.drawClef(
    textMeasurer: TextMeasurer,
    clef: Clef,
    halfSpace: Float,
    yOf: (Float) -> Float,
) {
    val treble = clef == Clef.TREBLE
    val layout = textMeasurer.measure(
        text = if (treble) TREBLE_CLEF else BASS_CLEF,
        style = TextStyle(fontSize = (halfSpace * if (treble) 8f else 6f).toSp()),
    )
    val anchorY = if (treble) yOf(3.2f) else yOf(5f)
    drawText(
        textLayoutResult = layout,
        color = TextPrimary.copy(alpha = 0.85f),
        topLeft = Offset(8.dp.toPx(), anchorY - layout.size.height / 2f),
    )
}
