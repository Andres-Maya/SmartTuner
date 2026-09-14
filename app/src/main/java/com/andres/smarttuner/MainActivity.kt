package com.andres.smarttuner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.andres.smarttuner.tuner.TunerViewModel
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.tuner.TunerRoute

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            SmartTunerTheme {
                TunerRoute(viewModel)
            }
        }
    }
}
