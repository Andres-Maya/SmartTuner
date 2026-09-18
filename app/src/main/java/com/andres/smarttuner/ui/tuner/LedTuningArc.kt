package com.andres.smarttuner.ui.tuner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.tuner.TunerUiState
import com.andres.smarttuner.ui.theme.TunerColors
import com.andres.smarttuner.ui.theme.TunerTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MAX_CENTS = 50f

/** Luces a cada lado de la central: 21 en total, como las de un pedal afinador. */
private const val SIDE_LIGHTS = 10

/** Medio recorrido del arco, en grados desde la vertical. */
private const val HALF_SWEEP = 62f

/** Largo de cada rayita respecto al radio del arco. */
private const val LIGHT_LENGTH = 0.19f

/** Verde afinado → ámbar cerca → coral desafinado. */
fun tuningColor(cents: Float, hasSignal: Boolean, colors: TunerColors): Color {
    if (!hasSignal) return colors.textMuted
    val deviation = abs(cents)
    return when {
        deviation <= TunerUiState.IN_TUNE_CENTS -> colors.inTune
        deviation <= 20f -> lerp(colors.inTune, colors.nearlyInTune, (deviation - 5f) / 15f)
        else -> lerp(colors.nearlyInTune, colors.outOfTune, ((deviation - 20f) / 30f).coerceAtMost(1f))
    }
}

/**
 * Arco de luces del afinador: rayitas apuntando al centro del arco, como las de un pedal.
 * La central marca el punto afinado y las de los lados se encienden hacia la izquierda
 * cuando la nota está grave y hacia la derecha cuando está aguda, con la punta más
 * brillante. Sin sonido, una luz recorre el arco despacio.
 *
 * El arco ocupa la parte alta del espacio que reciba y deja libre el hueco de abajo,
 * pensado para que quepa el visor de la nota.
 */
@Composable
fun LedTuningArc(
    cents: Float,
    hasSignal: Boolean,
    inTune: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val transition = rememberInfiniteTransition(label = "ledArc")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "scan",
    )

    Canvas(modifier) {
        val sweep = HALF_SWEEP * PI.toFloat() / 180f
        val padding = 6.dp.toPx()
        // El radio lo limita el lado más ajustado: el ancho disponible o el alto que ocupa el arco.
        val radius = min(
            (size.width / 2f - padding) / sin(sweep),
            (size.height - padding * 2f) / (1f - cos(sweep) + LIGHT_LENGTH * cos(sweep)),
        )
        val length = radius * LIGHT_LENGTH
        val center = Offset(size.width / 2f, padding + radius)
        val step = radius * sweep / SIDE_LIGHTS

        fun angleOf(index: Float) = -90f + index / SIDE_LIGHTS * HALF_SWEEP

        fun positionOf(index: Float, distance: Float): Offset {
            val radians = angleOf(index) * PI.toFloat() / 180f
            return Offset(center.x + distance * cos(radians), center.y + distance * sin(radians))
        }

        // Al quedar afinado, el aire alrededor de la luz central se tiñe.
        if (inTune && hasSignal) {
            val apex = positionOf(0f, radius - length / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                    center = apex,
                    radius = radius * 0.5f,
                ),
                radius = radius * 0.5f,
                center = apex,
            )
        }

        val target = (cents / MAX_CENTS).coerceIn(-1f, 1f) * SIDE_LIGHTS
        val reach = abs(target)
        // El barrido de reposo va y vuelve de un extremo a otro del arco.
        val idlePosition = sin(scan * 2f * PI.toFloat()) * SIDE_LIGHTS

        for (index in -SIDE_LIGHTS..SIDE_LIGHTS) {
            val distance = abs(index).toFloat()
            val intensity = when {
                !hasSignal -> (1f - abs(index - idlePosition) / 2f).coerceAtLeast(0f) * 0.4f
                inTune -> when (abs(index)) {
                    0 -> 1f
                    1 -> 0.4f
                    else -> 0f
                }
                // Solo se enciende el lado hacia el que está desviada la nota.
                index != 0 && (index > 0) != (target > 0f) -> 0f
                distance > reach -> (1f - (distance - reach)).coerceAtLeast(0f) * 0.85f
                else -> 0.3f + 0.7f * distance / reach.coerceAtLeast(0.001f)
            }
            // La central es más larga y ancha; crece hacia dentro para no salirse del arco.
            val lightLength = length * if (index == 0) 1.3f else 1f
            drawArcLight(
                center = positionOf(index.toFloat(), radius - lightLength / 2f),
                rotation = angleOf(index.toFloat()) + 90f,
                width = step * if (index == 0) 0.58f else 0.42f,
                length = lightLength,
                color = if (hasSignal) color else colors.accent,
                offColor = colors.track,
                halo = step * 2.2f,
                intensity = intensity,
            )
        }
    }
}

/**
 * Una rayita del arco: la pastilla apagada, el halo alrededor y el núcleo blanco al máximo.
 * [rotation] la inclina para que apunte al centro del arco.
 */
private fun DrawScope.drawArcLight(
    center: Offset,
    rotation: Float,
    width: Float,
    length: Float,
    color: Color,
    offColor: Color,
    halo: Float,
    intensity: Float,
) {
    val topLeft = Offset(center.x - width / 2f, center.y - length / 2f)
    val size = Size(width, length)
    val corner = CornerRadius(width / 2f)

    // El halo es redondo: se dibuja sin girar, por debajo de la rayita.
    if (intensity > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.28f * intensity), Color.Transparent),
                center = center,
                radius = halo,
            ),
            radius = halo,
            center = center,
        )
    }

    rotate(degrees = rotation, pivot = center) {
        drawRoundRect(color = offColor, topLeft = topLeft, size = size, cornerRadius = corner)
        if (intensity <= 0.01f) return@rotate

        drawRoundRect(
            color = color.copy(alpha = 0.25f + 0.75f * intensity),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
        )
        if (intensity > 0.7f) {
            val inset = width * 0.3f
            drawRoundRect(
                color = Color.White.copy(alpha = (intensity - 0.7f) * 1.2f),
                topLeft = Offset(topLeft.x + inset, topLeft.y + length * 0.14f),
                size = Size(width - inset * 2f, length * 0.72f),
                cornerRadius = corner,
            )
        }
    }
}
