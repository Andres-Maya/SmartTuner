package com.andres.smarttuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.andres.smarttuner.R

/**
 * Fuente de display del modo claro: Abril Fatface, una serif de cartel que da a la nota
 * el aire impreso del visor de papel (licencia OFL, ver licenses/AbrilFatface-OFL.txt).
 */
val DisplayFamily = FontFamily(Font(R.font.abril_fatface))

/** Tipografía de Material derivada de los estilos propios, para que sus componentes combinen. */
internal fun TunerTypography.toMaterialTypography() = Typography(
    headlineMedium = headline,
    titleLarge = title,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = footnote,
    labelLarge = buttonSmall,
    labelMedium = caption,
    labelSmall = overline,
)
