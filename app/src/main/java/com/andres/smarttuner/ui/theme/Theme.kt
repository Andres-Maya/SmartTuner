package com.andres.smarttuner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

    /** Aspecto activo (claro u oscuro) y cómo alternarlo desde cualquier pantalla. */
    val appearance: ThemeController
        @Composable @ReadOnlyComposable get() = LocalThemeController.current
}

/** Permite a cualquier pantalla saber si está en claro u oscuro y cambiarlo. */
@Immutable
class ThemeController(
    val mode: ThemeMode,
    val toggle: () -> Unit,
)

internal val LocalThemeController = staticCompositionLocalOf { ThemeController(ThemeMode.DARK) {} }

/**
 * [mode] elige entre el modo oscuro (neón lima sobre tinta) y el claro (papel crema y
 * tinta marrón); [onToggleMode] recibe el cambio desde el botón de la barra superior.
 */
@Composable
fun SmartTunerTheme(
    mode: ThemeMode = ThemeMode.DARK,
    onToggleMode: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = mode.colors
    val typography = TunerTypography()

    CompositionLocalProvider(
        LocalThemeController provides ThemeController(mode, onToggleMode),
        LocalTunerColors provides colors,
        LocalTunerSpacing provides TunerSpacing(),
        LocalTunerSizes provides TunerSizes(),
        LocalTunerShapes provides TunerShapes(),
        LocalTunerTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(mode.isDark),
            typography = typography.toMaterialTypography(),
            shapes = MaterialShapes,
            content = content,
        )
    }
}

private fun TunerColors.toMaterialColorScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
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
} else {
    lightColorScheme(
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
}

private val MaterialShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
