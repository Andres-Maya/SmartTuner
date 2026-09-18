package com.andres.smarttuner.ui.theme

import android.content.Context

/** Recuerda el tema elegido entre sesiones. */
class ThemePreferences(context: Context) {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var palette: ThemePalette
        get() = ThemePalette.fromName(preferences.getString(KEY_PALETTE, null))
        set(value) = preferences.edit().putString(KEY_PALETTE, value.name).apply()

    private companion object {
        const val FILE = "smarttuner.appearance"
        const val KEY_PALETTE = "palette"
    }
}
