package com.andres.smarttuner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.andres.smarttuner.R
import com.andres.smarttuner.ui.theme.TunerTheme

private const val LOGO_ASSET = "logo.svg"

/**
 * Logo de la app: el clavijero de bajo. En el modo oscuro es el neón lima recortado en
 * círculo, y en el claro el mismo dibujo a trazo, teñido de marrón, que es lo que pega
 * sobre papel. Al tocarlo da una vuelta rápida.
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
        val context = LocalContext.current
        val request = remember(context) {
            ImageRequest.Builder(context)
                .data("file:///android_asset/$LOGO_ASSET")
                .decoderFactory(SvgDecoder.Factory())
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(TunerTheme.colors.accent),
            modifier = modifier.spinOnTap(contentDescription),
        )
    }
}
