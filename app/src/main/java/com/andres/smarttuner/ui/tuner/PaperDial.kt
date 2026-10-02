package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.music.NoteName
import com.andres.smarttuner.ui.theme.TunerTheme

/** Marcas de la regleta: una cada 2.5 ¢ a lo largo del arco. */
private const val TICK_CENTS = 2.5f

/**
 * Visor del modo claro, con otra idea que el del oscuro pero en el mismo sitio: sobre el
 * trazado del arco va una regleta de rayas finas y, en su hueco, la nota escrita —letra,
 * alteración y octava— dentro de un círculo sin relleno.
 *
 * Por el arco se mueve una aguja oscura, que es lo que está entrando por el micrófono; al
 * caer sobre la nota exacta, la aguja crece.
 */
@Composable
fun PaperDial(
    cents: Float,
    hasSignal: Boolean,
    inTune: Boolean,
    note: NoteName?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.45f),
    ) {
        PitchArc(
            cents = cents,
            hasSignal = hasSignal,
            inTune = inTune,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoteCircle(note)
            Spacer(Modifier.height(TunerTheme.spacing.sm))
            Text(
                text = note?.solfegeLabel ?: emptyLabel,
                color = TunerTheme.colors.textMuted,
                style = TunerTheme.typography.noteCaption,
            )
        }
    }
}

/** La nota escrita en la fuente de display, dentro de un aro fino. */
@Composable
private fun NoteCircle(note: NoteName?) {
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
}

/**
 * Regleta curvada de -50 a +50 cents sobre el arco del afinador. Las rayas son finas y las
 * de cada cuarto, más largas; la del centro marca la nota exacta. La aguja crece al afinar.
 */
@Composable
private fun PitchArc(
    cents: Float,
    hasSignal: Boolean,
    inTune: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val grow by animateFloatAsState(
        targetValue = if (inTune && hasSignal) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "needleGrow",
    )

    Canvas(modifier) {
        val arc = tunerArc()

        /** Cada raya apunta al centro del arco: va del borde de fuera hacia dentro. */
        fun tick(at: Float, length: Float, color: Color, width: Float) {
            val angle = arc.angleOf(at)
            drawLine(
                color = color,
                start = arc.positionOf(angle, arc.radius),
                end = arc.positionOf(angle, arc.radius - length),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        var at = -MAX_CENTS
        while (at <= MAX_CENTS + 0.01f) {
            val quarter = at.toInt() % 25 == 0 && at == at.toInt().toFloat()
            when {
                at == 0f -> tick(at, arc.length, colors.textPrimary.copy(alpha = 0.55f), 1.6.dp.toPx())
                quarter -> tick(at, arc.length * 0.68f, colors.tick, 1.dp.toPx())
                else -> tick(at, arc.length * 0.40f, colors.tick, 1.dp.toPx())
            }
            at += TICK_CENTS
        }

        if (!hasSignal) return@Canvas

        // Aguja: lo que entra por el micrófono. Crece al caer sobre la nota.
        tick(
            at = cents,
            length = arc.length * (1.24f + 0.6f * grow),
            color = colors.textPrimary,
            width = (2.4f + 1.4f * grow).dp.toPx(),
        )
    }
}
