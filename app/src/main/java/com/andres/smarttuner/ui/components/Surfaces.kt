package com.andres.smarttuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.andres.smarttuner.ui.theme.TunerTheme

/** Contenedor de pantalla completa: fondo, márgenes del sistema y columna centrada. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = TunerTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(TunerTheme.colors.screenBrush)
            .systemBarsPadding()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.screenVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Tarjeta sobre el fondo, para agrupar contenido relacionado. */
@Composable
fun TunerCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = TunerTheme.spacing.lg,
        vertical = TunerTheme.spacing.md,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(TunerTheme.shapes.card)
            .background(TunerTheme.colors.surface)
            .padding(contentPadding),
        content = content,
    )
}

/** Título de sección en mayúsculas pequeñas. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TunerTheme.colors.textMuted,
) {
    Text(text = text, color = color, style = TunerTheme.typography.overline, modifier = modifier)
}

/** Título y descripción centrados, para estados vacíos, errores o permisos. */
@Composable
fun MessageBlock(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = TunerTheme.colors.textPrimary,
            style = TunerTheme.typography.title,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            color = TunerTheme.colors.textMuted,
            style = TunerTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TunerTheme.spacing.sm),
        )
    }
}

/**
 * Fila seleccionable con borde y botón de radio al final. El contenido va a la izquierda.
 * Úsala dentro de una columna con `Modifier.selectableGroup()`.
 */
@Composable
fun SelectableOption(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = TunerTheme.colors
    val shape = TunerTheme.shapes.option
    val border by animateColorAsState(if (selected) colors.accent else Color.Transparent, label = "optionBorder")
    val background by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = 0.12f) else colors.surfaceHigh.copy(alpha = 0.5f),
        label = "optionBackground",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(TunerTheme.sizes.borderStrong, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(
                start = TunerTheme.spacing.md,
                end = TunerTheme.spacing.sm,
                top = TunerTheme.spacing.sm,
                bottom = TunerTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        // onClick = null: la fila completa es la que se selecciona.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textMuted),
            modifier = Modifier.padding(start = TunerTheme.spacing.sm),
        )
    }
}
