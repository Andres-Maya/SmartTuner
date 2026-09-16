package com.andres.smarttuner.ui.theme

import androidx.compose.material3.Typography

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
