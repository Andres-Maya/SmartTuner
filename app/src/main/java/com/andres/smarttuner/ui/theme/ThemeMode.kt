package com.andres.smarttuner.ui.theme

/**
 * Los dos aspectos de la app. El oscuro es el de siempre, con el neón lima del logo; el
 * claro es papel crema y tinta marrón, sin brillos.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    ;

    val colors: TunerColors get() = if (this == DARK) DarkTunerColors else LightTunerColors

    val isDark: Boolean get() = this == DARK

    /** El otro, para el botón que alterna entre los dos. */
    val other: ThemeMode get() = if (this == DARK) LIGHT else DARK

    companion object {
        fun of(dark: Boolean): ThemeMode = if (dark) DARK else LIGHT

        fun fromName(name: String?): ThemeMode? = entries.firstOrNull { it.name == name }
    }
}
