package com.andres.smarttuner.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.R
import com.andres.smarttuner.ui.components.GhostButton

/**
 * Menú de apariencia: elige el color principal de la app. El cambio se aplica al instante
 * sobre la pantalla que queda detrás, así se ve el resultado antes de cerrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(onDismiss: () -> Unit) {
    val colors = TunerTheme.colors
    val spacing = TunerTheme.spacing
    val typography = TunerTheme.typography
    val palettes = TunerTheme.palettes

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xxl)
                .padding(bottom = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.appearance_title), color = colors.textPrimary, style = typography.title)
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = stringResource(R.string.appearance_body),
                color = colors.textMuted,
                style = typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xl))
            Column(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                ThemePalette.entries.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        row.forEach { palette ->
                            PaletteOption(
                                palette = palette,
                                selected = palette == palettes.palette,
                                onSelect = { palettes.select(palette) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Mantiene el mismo ancho en la última fila si quedara incompleta.
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(spacing.md))
            GhostButton(stringResource(R.string.appearance_done), onClick = onDismiss, color = colors.accent)
        }
    }
}

private const val COLUMNS = 3

@Composable
private fun PaletteOption(
    palette: ThemePalette,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val shape = TunerTheme.shapes.tile
    val border by animateColorAsState(
        targetValue = if (selected) palette.accent else Color.Transparent,
        label = "paletteBorder",
    )
    Column(
        modifier
            .clip(shape)
            .background(colors.surfaceHigh.copy(alpha = 0.55f))
            .border(TunerTheme.sizes.borderStrong, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = TunerTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(TunerTheme.sizes.iconMedium)
                .clip(TunerTheme.shapes.pill)
                .background(Brush.linearGradient(listOf(palette.accent, palette.accentAlt))),
        )
        Spacer(Modifier.height(TunerTheme.spacing.sm))
        Text(
            text = palette.displayName,
            color = if (selected) colors.textPrimary else colors.textMuted,
            style = TunerTheme.typography.footnote.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
