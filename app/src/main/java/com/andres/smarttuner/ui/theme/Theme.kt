package com.andres.smarttuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TunerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = NightDeep,
    secondary = InTune,
    tertiary = NearlyInTune,
    error = OutOfTune,
    background = Night,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = TextMuted,
)

/** El afinador usa siempre un tema oscuro: mejor contraste para los colores de afinación. */
@Composable
fun SmartTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TunerColorScheme,
        typography = Typography,
        content = content,
    )
}
