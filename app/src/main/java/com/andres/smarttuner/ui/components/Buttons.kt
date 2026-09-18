package com.andres.smarttuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.smarttuner.ui.theme.TunerTheme

/**
 * Acción principal: píldora con degradado. El texto se reduce solo si no cabe.
 * [leading] es un símbolo opcional antes del texto (p. ej. "✦").
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    height: Dp = TunerTheme.sizes.button,
) {
    val colors = TunerTheme.colors
    val style = TunerTheme.typography.button
    Row(
        modifier
            .height(height)
            .clip(TunerTheme.shapes.pill)
            .background(colors.accentBrush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TunerTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            Text(leading, color = colors.onAccent, style = TunerTheme.typography.buttonSmall)
            Spacer(Modifier.width(TunerTheme.spacing.sm))
        }
        BasicText(
            text = text,
            style = style.copy(color = colors.onAccent),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
        )
    }
}

/** Acción secundaria: píldora con borde del color de acento. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = TunerTheme.sizes.button,
) {
    val colors = TunerTheme.colors
    val pill = TunerTheme.shapes.pill
    Box(
        modifier
            .height(height)
            .clip(pill)
            .border(TunerTheme.sizes.border, colors.accent.copy(alpha = 0.5f), pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TunerTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.accent, style = TunerTheme.typography.button, maxLines = 1)
    }
}

/** Acción discreta, sin fondo: cancelar, abrir ajustes… */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TunerTheme.colors.textMuted,
) {
    Box(
        modifier
            .clip(TunerTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TunerTheme.spacing.lg, vertical = TunerTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, style = TunerTheme.typography.buttonSmall)
    }
}

/** Botón circular para íconos o símbolos (volver, +, −). */
@Composable
fun IconCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = TunerTheme.sizes.touchTarget,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val background = if (filled) TunerTheme.colors.surfaceHigh else Color.Transparent
    Box(
        modifier
            .size(size)
            .clip(TunerTheme.shapes.pill)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Símbolo tipográfico grande con el color de acento (♯, ♭, +, −). */
@Composable
fun SymbolText(symbol: String, color: Color = TunerTheme.colors.accent) {
    Text(symbol, color = color, style = TunerTheme.typography.symbol)
}

/** Flecha "‹" dibujada, para no depender de una librería de íconos. */
@Composable
fun BackChevron(color: Color = TunerTheme.colors.textPrimary, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val chevron = Path().apply {
            moveTo(this@Canvas.size.width * 0.68f, this@Canvas.size.height * 0.12f)
            lineTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.68f, this@Canvas.size.height * 0.88f)
        }
        drawPath(
            path = chevron,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Paleta de colores dibujada, para abrir el menu de apariencia. */
@Composable
fun PaletteIcon(color: Color = TunerTheme.colors.accent, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val radius = this.size.minDimension / 2f - 1.dp.toPx()
        val dot = radius * 0.24f
        drawCircle(color, radius, style = Stroke(width = 1.8.dp.toPx()))
        drawCircle(color, dot, Offset(center.x - radius * 0.42f, center.y - radius * 0.18f))
        drawCircle(color.copy(alpha = 0.75f), dot, Offset(center.x + radius * 0.02f, center.y - radius * 0.48f))
        drawCircle(color.copy(alpha = 0.5f), dot, Offset(center.x + radius * 0.45f, center.y - radius * 0.02f))
        // El hueco del pulgar de la paleta.
        drawCircle(color.copy(alpha = 0.3f), dot * 1.3f, Offset(center.x + radius * 0.12f, center.y + radius * 0.46f))
    }
}

/** Selector de opciones excluyentes en forma de píldora (p. ej. ♯ / ♭). */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val sizes = TunerTheme.sizes
    Row(
        modifier
            .clip(TunerTheme.shapes.pill)
            .background(colors.surfaceHigh)
            .semantics { this.contentDescription = contentDescription }
            .selectableGroup()
            .padding(TunerTheme.spacing.xs),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val background by animateColorAsState(if (isSelected) colors.accent else Color.Transparent, label = "segmentBg")
            val foreground by animateColorAsState(if (isSelected) colors.onAccent else colors.textMuted, label = "segmentFg")
            Box(
                Modifier
                    .size(width = sizes.segmentWidth, height = sizes.segment)
                    .clip(TunerTheme.shapes.pill)
                    .background(background)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = foreground, style = TunerTheme.typography.symbol)
            }
        }
    }
}

/** Control "− valor +" para ajustar un número. */
@Composable
fun Stepper(
    label: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementDescription: String,
    incrementDescription: String,
    modifier: Modifier = Modifier,
) {
    val sizes = TunerTheme.sizes
    Row(
        modifier
            .clip(TunerTheme.shapes.pill)
            .background(TunerTheme.colors.surfaceHigh)
            .padding(TunerTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(onDecrement, decrementDescription, size = sizes.iconButton, filled = false) {
            SymbolText("−")
        }
        Text(
            text = label,
            color = TunerTheme.colors.textPrimary,
            style = TunerTheme.typography.buttonSmall,
            modifier = Modifier.padding(horizontal = TunerTheme.spacing.sm),
        )
        IconCircleButton(onIncrement, incrementDescription, size = sizes.iconButton, filled = false) {
            SymbolText("+")
        }
    }
}
