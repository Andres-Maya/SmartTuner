package com.andres.smarttuner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.andres.smarttuner.tuner.TunerViewModel
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.ThemePreferences
import com.andres.smarttuner.ui.tuner.TunerRoute

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sale de la pantalla de carga y deja el tema normal antes de dibujar.
        setTheme(R.style.Theme_SmartTuner)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val appearance = ThemePreferences(this)
        setContent {
            // El tema elegido se recuerda entre sesiones y se aplica a toda la app.
            var palette by remember { mutableStateOf(appearance.palette) }
            SmartTunerTheme(
                palette = palette,
                onSelectPalette = {
                    palette = it
                    appearance.palette = it
                },
            ) {
                TunerRoute(viewModel)
            }
        }
    }
}
