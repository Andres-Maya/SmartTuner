package com.andres.smarttuner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Temas de color que puede elegir la persona que usa la app. Cada uno cambia el acento
 * (botones, iconos de los instrumentos, visor e identificador) y tiñe ligeramente los fondos.
 *
 * Los colores de afinación (verde afinado, ámbar cerca y coral lejos) no cambian con el tema:
 * su significado es fijo y cambiarlos haría más difícil leer el afinador de un vistazo.
 */
enum class ThemePalette(
    val displayName: String,
    val accent: Color,
    val accentAlt: Color,
) {
    INDIGO("Índigo", Palette.Periwinkle400, Palette.Lavender400),
    CYAN("Cian", Color(0xFF38BDF8), Color(0xFF22D3EE)),
    VIOLET("Violeta", Color(0xFFA78BFA), Color(0xFFE879F9)),
    MAGENTA("Magenta", Color(0xFFF472B6), Color(0xFFFB7185)),
    AMBER("Ámbar", Color(0xFFFBBF24), Color(0xFFFB923C)),
    LIME("Lima", Color(0xFFA3E635), Color(0xFF4ADE80)),
    ;

    /** Paleta completa del tema, derivada del acento sobre la base oscura. */
    val colors: TunerColors by lazy {
        DarkTunerColors.copy(
            accent = accent,
            accentAlt = accentAlt,
            background = Palette.Ink900.tintedWith(accent, 0.06f),
            backgroundDeep = Palette.Ink950.tintedWith(accent, 0.05f),
            surface = Palette.Ink800.tintedWith(accent, 0.08f),
            surfaceHigh = Palette.Ink700.tintedWith(accent, 0.10f),
            textMuted = Palette.Mist400.tintedWith(accent, 0.18f),
            display = Palette.Ink950.tintedWith(accent, 0.04f),
            displayFrame = accent.copy(alpha = 0.14f),
        )
    }

    companion object {
        val Default = INDIGO

        fun fromName(name: String?): ThemePalette = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** Mezcla una pizca del acento en un color base para que todo el fondo pertenezca al tema. */
private fun Color.tintedWith(accent: Color, amount: Float): Color = lerp(this, accent, amount)
