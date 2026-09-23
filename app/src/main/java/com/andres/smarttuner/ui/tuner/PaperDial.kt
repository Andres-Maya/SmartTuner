package com.andres.smarttuner.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.music.NoteName
import com.andres.smarttuner.ui.theme.TunerTheme

/** Lo que abarca la regleta a cada lado de la nota. */
private const val RULER_CENTS = 50f

/**
 * Visor del modo claro, con otra idea que el del oscuro: la nota escrita —letra, alteración
 * y octava— dentro de un círculo sin relleno y, debajo, una regleta recta.
 *
 * Por la regleta se mueve una aguja oscura con sus hercios debajo, que es lo que está
 * entrando por el micrófono; al caer sobre la nota exacta, la aguja crece.
 */
@Composable
fun PaperDial(
    cents: Float,
    frequency: Float,
    hasSignal: Boolean,
    inTune: Boolean,
    note: NoteName?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        NoteCircle(note, emptyLabel)
        Spacer(Modifier.height(TunerTheme.spacing.xl))
        PitchRuler(
            cents = cents,
            frequency = frequency,
            hasSignal = hasSignal,
            inTune = inTune,
            modifier = Modifier
                .fillMaxWidth()
                .height(TunerTheme.sizes.ruler),
        )
    }
}

/** La nota escrita en la fuente de display, dentro de un aro fino. */
@Composable
private fun NoteCircle(note: NoteName?, emptyLabel: String) {
    val colors = TunerTheme.colors
    val typography = TunerTheme.typography
    Box(
        Modifier
            .size(TunerTheme.sizes.noteCircle)
            .border(TunerTheme.sizes.borderStrong, colors.textPrimary.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (note == null) {
            Text("–", color = colors.textMuted, style = typography.noteGlyph)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.letter.toString(), color = colors.textPrimary, style = typography.noteGlyph)
                Column(
                    Modifier.padding(start = TunerTheme.spacing.xxs),
                    horizontalAlignment = Alignment.Start,
                ) {
                    // La alteración arriba y la octava abajo, como en la notación de toda la vida.
                    Text(
                        text = note.accidentalSymbol.ifEmpty { " " },
                        color = colors.accent,
                        style = typography.noteGlyphSmall,
                    )
                    Text(note.octave.toString(), color = colors.accent, style = typography.noteGlyphSmall)
                }
            }
        }
    }
    Spacer(Modifier.height(TunerTheme.spacing.sm))
    Text(
        text = note?.solfegeLabel ?: emptyLabel,
        color = colors.textMuted,
        style = typography.noteCaption,
    )
}

/**
 * Regleta recta de -50 a +50 cents. Las marcas son finas y las de cada cuarto, más largas;
 * la del centro es la nota exacta. La aguja lleva los hercios debajo y crece al afinar.
 */
@Composable
private fun PitchRuler(
    cents: Float,
    frequency: Float,
    hasSignal: Boolean,
    inTune: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val measurer = rememberTextMeasurer()
    val valueStyle = TunerTheme.typography.rulerValue.copy(color = colors.textPrimary)
    val grow by animateFloatAsState(
        targetValue = if (inTune && hasSignal) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "needleGrow",
    )
    val reading = if (hasSignal && frequency > 0f) formatHz(frequency) else null

    Canvas(modifier) {
        val marksTop = 0f
        val marksBottom = size.height * 0.52f
        val middle = (marksTop + marksBottom) / 2f
        val centerX = size.width / 2f
        val usable = size.width - 8.dp.toPx()

        // Marcas cada 2.5 cents; cada 25 se alarga y la del centro es la más larga.
        var tick = -RULER_CENTS
        while (tick <= RULER_CENTS + 0.01f) {
            val x = centerX + tick / RULER_CENTS * usable / 2f
            val quarter = (tick.toInt() % 25 == 0) && tick == tick.toInt().toFloat()
            val half = when {
                tick == 0f -> (marksBottom - marksTop) / 2f
                quarter -> (marksBottom - marksTop) * 0.34f
                else -> (marksBottom - marksTop) * 0.20f
            }
            drawLine(
                color = if (tick == 0f) colors.textPrimary.copy(alpha = 0.55f) else colors.tick,
                start = Offset(x, middle - half),
                end = Offset(x, middle + half),
                strokeWidth = if (tick == 0f) 1.6.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            tick += 2.5f
        }

        if (!hasSignal) return@Canvas

        // Aguja: lo que entra por el micrófono. Crece al caer sobre la nota.
        val needleX = centerX + (cents / RULER_CENTS).coerceIn(-1f, 1f) * usable / 2f
        val needleHalf = (marksBottom - marksTop) * (0.62f + 0.30f * grow)
        drawLine(
            color = colors.textPrimary,
            start = Offset(needleX, middle - needleHalf),
            end = Offset(needleX, middle + needleHalf),
            strokeWidth = (2.4f + 1.4f * grow).dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Los hercios viajan con la aguja, sin salirse por los lados.
        reading?.let { text ->
            val layout = measurer.measure(text, valueStyle)
            val x = (needleX - layout.size.width / 2f)
                .coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, middle + needleHalf + 8.dp.toPx()))
        }
    }
}
