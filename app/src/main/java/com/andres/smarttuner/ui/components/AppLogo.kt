package com.andres.smarttuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.andres.smarttuner.ui.theme.TunerTheme
import kotlinx.coroutines.launch
import kotlin.math.floor

/** Lo que tarda el remolino en dar la vuelta. */
private const val SPIN_MILLIS = 700

/**
 * Logo de neón de la app. Cambia con el tema de color y, al tocarlo, da una vuelta
 * rápida como un remolino. Va recortado en círculo porque los PNG traen fondo negro.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = TunerTheme.palettes.palette
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    // Se infla un poco mientras gira, para que el remolino se sienta con impulso.
    val scale by animateFloatAsState(
        targetValue = if (turn.isRunning) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "logoScale",
    )

    Image(
        painter = painterResource(palette.logo),
        contentDescription = contentDescription,
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                scope.launch {
                    // Sigue desde donde esté hasta la siguiente vuelta completa: tocarlo
                    // varias veces seguidas encadena giros sin saltos.
                    val next = (floor(turn.value / 360f) + 1f) * 360f
                    turn.animateTo(next, tween(SPIN_MILLIS, easing = FastOutSlowInEasing))
                }
                onClick?.invoke()
            }
            .graphicsLayer {
                rotationZ = turn.value
                scaleX = scale
                scaleY = scale
            },
    )
}
