package com.andres.smarttuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.andres.smarttuner.ui.theme.TunerTheme
import kotlin.math.min

/*
 * Visor de siete segmentos, con el aire de un microondas o un radio despertador:
 * los segmentos apagados se intuyen y los encendidos brillan sobre el cristal.
 *
 *    aaa
 *   f   b
 *    ggg
 *   e   c
 *    ddd
 */

private const val SEG_A = 1
private const val SEG_B = 1 shl 1
private const val SEG_C = 1 shl 2
private const val SEG_D = 1 shl 3
private const val SEG_E = 1 shl 4
private const val SEG_F = 1 shl 5
private const val SEG_G = 1 shl 6

/** Las siete letras musicales (C D E F G A B) y las cifras caben en siete segmentos. */
private val GLYPHS = mapOf(
    '0' to (SEG_A or SEG_B or SEG_C or SEG_D or SEG_E or SEG_F),
    '1' to (SEG_B or SEG_C),
    '2' to (SEG_A or SEG_B or SEG_D or SEG_E or SEG_G),
    '3' to (SEG_A or SEG_B or SEG_C or SEG_D or SEG_G),
    '4' to (SEG_B or SEG_C or SEG_F or SEG_G),
    '5' to (SEG_A or SEG_C or SEG_D or SEG_F or SEG_G),
    '6' to (SEG_A or SEG_C or SEG_D or SEG_E or SEG_F or SEG_G),
    '7' to (SEG_A or SEG_B or SEG_C),
    '8' to (SEG_A or SEG_B or SEG_C or SEG_D or SEG_E or SEG_F or SEG_G),
    '9' to (SEG_A or SEG_B or SEG_C or SEG_D or SEG_F or SEG_G),
    'A' to (SEG_A or SEG_B or SEG_C or SEG_E or SEG_F or SEG_G),
    'B' to (SEG_C or SEG_D or SEG_E or SEG_F or SEG_G),
    'C' to (SEG_A or SEG_D or SEG_E or SEG_F),
    'D' to (SEG_B or SEG_C or SEG_D or SEG_E or SEG_G),
    'E' to (SEG_A or SEG_D or SEG_E or SEG_F or SEG_G),
    'F' to (SEG_A or SEG_E or SEG_F or SEG_G),
    'G' to (SEG_A or SEG_C or SEG_D or SEG_E or SEG_F),
    '-' to SEG_G,
    ' ' to 0,
)

/** Inclinación de los dígitos, como la cursiva de los visores de verdad. */
private const val SLANT = 0.07f

/** Lo que se ve de un segmento apagado: se intuye sin competir con los encendidos. */
private const val OFF_ALPHA = 0.075f

/**
 * Texto de siete segmentos repartido a lo ancho del espacio disponible.
 * [glow] va de 0 (sin halo, visor en reposo) a 1 (encendido a tope).
 */
@Composable
fun SegmentText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    glow: Float = 1f,
) {
    Canvas(modifier) {
        val count = text.length.coerceAtLeast(1)
        val gap = if (count > 1) size.width * 0.10f else 0f
        val charWidth = (size.width - gap * (count - 1)) / count
        text.forEachIndexed { index, char ->
            drawSegmentChar(
                char = char,
                origin = Offset(index * (charWidth + gap), 0f),
                area = Size(charWidth, size.height),
                color = color,
                glow = glow,
            )
        }
    }
}

/**
 * Alteración dibujada con el mismo trazo del visor. [sharp] elige el signo (♯ o ♭) y
 * [lit] lo enciende: en una nota natural el signo queda apenas marcado, igual que un
 * segmento apagado.
 */
@Composable
fun SegmentAccidental(
    sharp: Boolean,
    lit: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    glow: Float = 1f,
) {
    Canvas(modifier) {
        val stroke = min(size.width, size.height) * 0.13f
        val path = if (sharp) sharpPath(size.width, size.height) else flatPath(size.width, size.height)
        if (lit) {
            drawGlowingPath(path, color, stroke, glow)
        } else {
            drawPath(path, color.copy(alpha = OFF_ALPHA), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Recuadro del visor, con su bisel, para los símbolos pequeños de al lado de la nota. */
@Composable
fun DisplayCell(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TunerTheme.colors
    val shape = TunerTheme.shapes.chip
    Box(
        modifier
            .clip(shape)
            .background(colors.display)
            .border(TunerTheme.sizes.border, colors.displayFrame, shape)
            .padding(TunerTheme.spacing.sm),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun DrawScope.drawSegmentChar(
    char: Char,
    origin: Offset,
    area: Size,
    color: Color,
    glow: Float,
) {
    val mask = GLYPHS[char.uppercaseChar()] ?: 0
    val height = area.height
    val width = (area.width - height * SLANT).coerceAtLeast(1f)
    val thickness = min(height * 0.135f, width * 0.30f)
    val half = thickness / 2f
    val gap = thickness * 0.34f

    // La inclinación crece hacia arriba, así que el dígito se apoya sobre su base.
    fun point(x: Float, y: Float) = Offset(origin.x + x + (height - y) * SLANT, origin.y + y)

    fun horizontal(y: Float): Path {
        val left = gap
        val right = width - gap
        return Path().apply {
            moveTo(point(left, y))
            lineTo(point(left + half, y - half))
            lineTo(point(right - half, y - half))
            lineTo(point(right, y))
            lineTo(point(right - half, y + half))
            lineTo(point(left + half, y + half))
            close()
        }
    }

    fun vertical(x: Float, top: Float, bottom: Float): Path {
        val start = top + gap
        val end = bottom - gap
        return Path().apply {
            moveTo(point(x, start))
            lineTo(point(x + half, start + half))
            lineTo(point(x + half, end - half))
            lineTo(point(x, end))
            lineTo(point(x - half, end - half))
            lineTo(point(x - half, start + half))
            close()
        }
    }

    val middle = height / 2f
    val segments = listOf(
        SEG_A to horizontal(half),
        SEG_G to horizontal(middle),
        SEG_D to horizontal(height - half),
        SEG_F to vertical(half, half, middle),
        SEG_B to vertical(width - half, half, middle),
        SEG_E to vertical(half, middle, height - half),
        SEG_C to vertical(width - half, middle, height - half),
    )

    segments.forEach { (flag, path) ->
        if (mask and flag != 0) {
            // Un halo ancho muy tenue y otro pegado al borde: brilla sin emborronar el dígito.
            if (glow > 0f) {
                drawPath(path, color.copy(alpha = 0.05f * glow), style = Stroke(thickness * 1.3f))
                drawPath(path, color.copy(alpha = 0.16f * glow), style = Stroke(thickness * 0.45f))
            }
            drawPath(path, color)
        } else {
            drawPath(path, color.copy(alpha = OFF_ALPHA))
        }
    }
}

/** Dos barras verticales cruzadas por dos horizontales inclinadas hacia arriba. */
private fun sharpPath(width: Float, height: Float) = Path().apply {
    moveTo(width * 0.38f, height * 0.08f)
    lineTo(width * 0.30f, height * 0.92f)
    moveTo(width * 0.70f, height * 0.08f)
    lineTo(width * 0.62f, height * 0.92f)
    moveTo(width * 0.14f, height * 0.44f)
    lineTo(width * 0.86f, height * 0.34f)
    moveTo(width * 0.14f, height * 0.72f)
    lineTo(width * 0.86f, height * 0.62f)
}

/** Asta vertical con la panza a la derecha. */
private fun flatPath(width: Float, height: Float) = Path().apply {
    moveTo(width * 0.32f, height * 0.06f)
    lineTo(width * 0.32f, height * 0.90f)
    moveTo(width * 0.32f, height * 0.48f)
    cubicTo(
        width * 0.82f, height * 0.42f,
        width * 0.78f, height * 0.84f,
        width * 0.32f, height * 0.90f,
    )
}

private fun DrawScope.drawGlowingPath(path: Path, color: Color, width: Float, glow: Float) {
    if (glow > 0f) {
        drawPath(path, color.copy(alpha = 0.06f * glow), style = Stroke(width * 2.2f, cap = StrokeCap.Round))
        drawPath(path, color.copy(alpha = 0.16f * glow), style = Stroke(width * 1.4f, cap = StrokeCap.Round))
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun Path.moveTo(point: Offset) = moveTo(point.x, point.y)

private fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)
