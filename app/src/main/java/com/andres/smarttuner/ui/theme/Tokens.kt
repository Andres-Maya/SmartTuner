package com.andres.smarttuner.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colores por función. Cambiar un rol aquí actualiza toda la app. */
@Immutable
data class TunerColors(
    val background: Color,
    val backgroundDeep: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val inTune: Color,
    val nearlyInTune: Color,
    val outOfTune: Color,
    val track: Color,
    val tick: Color,
    val staffLine: Color,
    /** Fondo del visor de notas y luces, como el cristal de un display. */
    val display: Color,
    /** Bisel del visor. */
    val displayFrame: Color,
    /**
     * Cuánto brillan los elementos encendidos (visor, luces, halos): 1 en el modo oscuro,
     * 0 en el claro, donde un halo sobre papel solo ensucia el dibujo.
     */
    val glow: Float,
) {
    /** Fondo de pantalla completa. */
    val screenBrush: Brush = Brush.verticalGradient(listOf(surface, background, backgroundDeep))

    /** Relleno de las acciones principales. */
    val accentBrush: Brush = Brush.horizontalGradient(listOf(accent, accentAlt))
}

/** Neón lima sobre tinta, a juego con el logo y el icono de la app. */
val DarkTunerColors = TunerColors(
    background = Palette.Ink900,
    backgroundDeep = Palette.Ink950,
    surface = Palette.Ink800,
    surfaceHigh = Palette.Ink700,
    textPrimary = Palette.Mist50,
    textMuted = Palette.Mist400,
    accent = Palette.Lime400,
    accentAlt = Palette.Spring400,
    onAccent = Palette.Ink950,
    inTune = Palette.Mint400,
    nearlyInTune = Palette.Amber300,
    outOfTune = Palette.Coral400,
    track = Palette.White.copy(alpha = 0.10f),
    tick = Palette.White.copy(alpha = 0.30f),
    staffLine = Palette.White.copy(alpha = 0.40f),
    display = Palette.Ink950,
    displayFrame = Palette.White.copy(alpha = 0.07f),
    glow = 1f,
)

/** Papel crema y tinta marrón: sin brillos, todo al trazo. */
val LightTunerColors = TunerColors(
    background = Palette.Sand100,
    backgroundDeep = Palette.Sand200,
    surface = Palette.Sand50,
    surfaceHigh = Palette.Sand200,
    textPrimary = Palette.Bark900,
    textMuted = Palette.Clay400,
    accent = Palette.Bark600,
    accentAlt = Palette.Bark400,
    onAccent = Palette.Sand50,
    inTune = Palette.Moss600,
    nearlyInTune = Palette.Honey600,
    outOfTune = Palette.Brick600,
    track = Palette.Bark900.copy(alpha = 0.10f),
    tick = Palette.Bark900.copy(alpha = 0.26f),
    staffLine = Palette.Bark900.copy(alpha = 0.35f),
    // El "cristal" del visor va un punto más oscuro que el papel, como un LCD apagado.
    display = Palette.Sand300,
    displayFrame = Palette.Bark900.copy(alpha = 0.14f),
    glow = 0f,
)

/** Escala de espacios en múltiplos de 4 dp. */
@Immutable
data class TunerSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    /** Margen lateral de las pantallas. */
    val screenHorizontal: Dp = 20.dp,
    val screenVertical: Dp = 12.dp,
)

/** Tamaños fijos de controles e íconos. */
@Immutable
data class TunerSizes(
    val iconButton: Dp = 36.dp,
    val touchTarget: Dp = 44.dp,
    val button: Dp = 48.dp,
    val buttonLarge: Dp = 52.dp,
    val segment: Dp = 34.dp,
    val segmentWidth: Dp = 44.dp,
    val iconSmall: Dp = 24.dp,
    val iconMedium: Dp = 34.dp,
    val iconLarge: Dp = 84.dp,
    /** Logo junto al nombre de la app. */
    val logo: Dp = 38.dp,
    /** Aro que rodea la nota escrita en el modo claro. */
    val noteCircle: Dp = 168.dp,
    /** Alto de la regleta con la aguja. */
    val ruler: Dp = 96.dp,
    val avatar: Dp = 56.dp,
    val statusDot: Dp = 8.dp,
    val progressBar: Dp = 6.dp,
    /** Alto del arco de luces del afinador cuando se muestra suelto. */
    val ledArc: Dp = 132.dp,
    /** Alto del dígito grande del visor. */
    val displayLetter: Dp = 112.dp,
    /** Lado de los recuadros de alteración y octava. */
    val displayCell: Dp = 50.dp,
    val border: Dp = 1.dp,
    val borderStrong: Dp = 1.5.dp,
)

/** Formas con nombre según el tipo de elemento. */
@Immutable
data class TunerShapes(
    val pill: Shape = CircleShape,
    val card: Shape = RoundedCornerShape(24.dp),
    val panel: Shape = RoundedCornerShape(20.dp),
    val tile: Shape = RoundedCornerShape(16.dp),
    val option: Shape = RoundedCornerShape(14.dp),
    val chip: Shape = RoundedCornerShape(12.dp),
)

/** Estilos de texto por uso. El color lo pone cada componente según el rol. */
@Immutable
data class TunerTypography(
    val appTitle: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    val screenTitle: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    val headline: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    val title: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    val body: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    val bodySmall: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    val footnote: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    val tiny: TextStyle = TextStyle(fontSize = 10.sp),
    val overline: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    val statLabel: TextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    val statValue: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val buttonSmall: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    val status: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    val symbol: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    val progress: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    val noteLetter: TextStyle = TextStyle(fontSize = 104.sp, lineHeight = 104.sp, fontWeight = FontWeight.Bold),
    val noteAccidental: TextStyle = TextStyle(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    val noteOctave: TextStyle = TextStyle(fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    val notePlaceholder: TextStyle = TextStyle(fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.Light),
    /** La nota escrita del modo claro: letra grande y, al lado, alteración y octava. */
    val noteGlyph: TextStyle = TextStyle(fontFamily = DisplayFamily, fontSize = 86.sp, lineHeight = 90.sp),
    val noteGlyphSmall: TextStyle = TextStyle(fontFamily = DisplayFamily, fontSize = 30.sp, lineHeight = 34.sp),
    /** Los hercios que viajan bajo la aguja de la regleta. */
    val rulerValue: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    val noteCaption: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    val noteLabel: TextStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    val gaugeLabel: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    val stripNote: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    val staffNote: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val staffMark: TextStyle = TextStyle(fontSize = 12.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
)

internal val LocalTunerColors = staticCompositionLocalOf { DarkTunerColors }
internal val LocalTunerSpacing = staticCompositionLocalOf { TunerSpacing() }
internal val LocalTunerSizes = staticCompositionLocalOf { TunerSizes() }
internal val LocalTunerShapes = staticCompositionLocalOf { TunerShapes() }
internal val LocalTunerTypography = staticCompositionLocalOf { TunerTypography() }
