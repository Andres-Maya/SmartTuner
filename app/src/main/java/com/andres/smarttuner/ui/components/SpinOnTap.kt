package com.andres.smarttuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlin.math.floor

/** Lo que tarda el remolino en dar la vuelta. */
private const val SPIN_MILLIS = 700

/**
 * Da una vuelta rápida, como un remolino, cada vez que se toca. Si se toca varias veces
 * seguidas encadena vueltas desde donde esté, sin saltos, y se infla un poco mientras gira.
 */
@Composable
fun Modifier.spinOnTap(
    contentDescription: String? = null,
    onTap: () -> Unit = {},
): Modifier {
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    val scale by animateFloatAsState(
        targetValue = if (turn.isRunning) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "spinScale",
    )
    return this
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = contentDescription,
        ) {
            scope.launch {
                turn.animateTo(
                    targetValue = (floor(turn.value / 360f) + 1f) * 360f,
                    animationSpec = tween(SPIN_MILLIS, easing = FastOutSlowInEasing),
                )
            }
            onTap()
        }
        .graphicsLayer {
            rotationZ = turn.value
            scaleX = scale
            scaleY = scale
        }
}
