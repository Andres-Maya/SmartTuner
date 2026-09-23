package com.andres.smarttuner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.andres.smarttuner.tuner.TunerViewModel
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.ThemeMode
import com.andres.smarttuner.ui.theme.ThemePreferences
import com.andres.smarttuner.ui.tuner.TunerRoute

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sale de la pantalla de carga y deja el tema normal antes de dibujar.
        setTheme(R.style.Theme_SmartTuner)
        super.onCreate(savedInstanceState)
        val appearance = ThemePreferences(this)
        setContent {
            // La primera vez se sigue al sistema; a partir de ahí manda lo que se elija aquí.
            val systemDark = isSystemInDarkTheme()
            var mode by remember { mutableStateOf(appearance.mode ?: ThemeMode.of(systemDark)) }

            // Los iconos de las barras del sistema tienen que contrastar con el fondo de la app.
            LaunchedEffect(mode) {
                val style = if (mode.isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            SmartTunerTheme(
                mode = mode,
                onToggleMode = {
                    mode = mode.other
                    appearance.mode = mode
                },
            ) {
                TunerRoute(viewModel)
            }
        }
    }
}
