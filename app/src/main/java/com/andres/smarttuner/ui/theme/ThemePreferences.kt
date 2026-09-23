package com.andres.smarttuner.ui.theme

import android.content.Context

/** Recuerda el aspecto elegido entre sesiones. */
class ThemePreferences(context: Context) {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** `null` mientras nadie lo haya tocado: entonces se sigue al sistema. */
    var mode: ThemeMode?
        get() = ThemeMode.fromName(preferences.getString(KEY_MODE, null))
        set(value) = preferences.edit().putString(KEY_MODE, value?.name).apply()

    private companion object {
        const val FILE = "smarttuner.appearance"
        const val KEY_MODE = "mode"
    }
}
