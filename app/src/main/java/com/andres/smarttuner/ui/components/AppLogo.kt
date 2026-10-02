package com.andres.smarttuner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.andres.smarttuner.R
import com.andres.smarttuner.ui.theme.TunerTheme

/**
 * Logo de la app: el clavijero de bajo que por un lado es circuito. En el modo oscuro es el
 * neón lima recortado en círculo, y en el claro el mismo dibujo a trazo, teñido de marrón,
 * que es lo que pega sobre papel. Al tocarlo da una vuelta rápida.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (TunerTheme.appearance.mode.isDark) {
        Image(
            painter = painterResource(R.drawable.logo_lime),
            contentDescription = contentDescription,
            // El PNG trae fondo negro: recortado en círculo pasa por una chapa.
            modifier = modifier
                .clip(CircleShape)
                .spinOnTap(contentDescription),
        )
    } else {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = contentDescription,
            // Solo el trazo, en blanco sobre transparente: el color lo pone el tema.
            colorFilter = ColorFilter.tint(TunerTheme.colors.accent),
            modifier = modifier.spinOnTap(contentDescription),
        )
    }
}
