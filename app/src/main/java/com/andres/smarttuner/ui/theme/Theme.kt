package com.andres.smarttuner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Punto de acceso al sistema de diseño:
 *
 * ```
 * Text("La 440", style = TunerTheme.typography.caption, color = TunerTheme.colors.textMuted)
 * Spacer(Modifier.height(TunerTheme.spacing.md))
 * ```
 */
object TunerTheme {
    val colors: TunerColors
        @Composable @ReadOnlyComposable get() = LocalTunerColors.current

    val spacing: TunerSpacing
        @Composable @ReadOnlyComposable get() = LocalTunerSpacing.current

    val sizes: TunerSizes
        @Composable @ReadOnlyComposable get() = LocalTunerSizes.current

    val shapes: TunerShapes
        @Composable @ReadOnlyComposable get() = LocalTunerShapes.current

    val typography: TunerTypography
        @Composable @ReadOnlyComposable get() = LocalTunerTypography.current
}

/** El afinador usa siempre un tema oscuro: mejor contraste para los colores de afinación. */
@Composable
fun SmartTunerTheme(content: @Composable () -> Unit) {
    val colors = DarkTunerColors
    val typography = TunerTypography()

    CompositionLocalProvider(
        LocalTunerColors provides colors,
        LocalTunerSpacing provides TunerSpacing(),
        LocalTunerSizes provides TunerSizes(),
        LocalTunerShapes provides TunerShapes(),
        LocalTunerTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = MaterialShapes,
            content = content,
        )
    }
}

private fun TunerColors.toMaterialColorScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    secondary = inTune,
    tertiary = nearlyInTune,
    error = outOfTune,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = textMuted,
)

private val MaterialShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
