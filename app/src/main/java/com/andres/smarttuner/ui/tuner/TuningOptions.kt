package com.andres.smarttuner.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.andres.smarttuner.music.AccidentalStyle
import com.andres.smarttuner.music.Instrument
import com.andres.smarttuner.music.Tuning
import com.andres.smarttuner.ui.components.SelectableOption
import com.andres.smarttuner.ui.theme.TunerTheme

/**
 * Lista de variantes de un instrumento: guitarra de 6 o 7 cuerdas, bajo de 4, 5 o 6,
 * ukelele barítono… Cada opción muestra sus cuerdas al aire.
 */
@Composable
fun TuningOptions(
    instrument: Instrument,
    selected: Tuning,
    onSelect: (Tuning) -> Unit,
    style: AccidentalStyle,
    modifier: Modifier = Modifier,
) {
    val colors = TunerTheme.colors
    val typography = TunerTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(TunerTheme.spacing.sm),
    ) {
        instrument.tunings.forEach { tuning ->
            val isSelected = tuning == selected
            SelectableOption(selected = isSelected, onSelect = { onSelect(tuning) }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tuning.name,
                        color = if (isSelected) colors.textPrimary else colors.textMuted,
                        style = typography.bodySmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    Text(
                        text = tuning.stringLabels(style),
                        color = colors.textMuted,
                        style = typography.footnote,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(TunerTheme.spacing.sm))
            }
        }
    }
}

/** Ej: "E2 A2 D3 G3 B3 E4", de la cuerda más grave a la más aguda. */
internal fun Tuning.stringLabels(style: AccidentalStyle): String =
    strings.sortedBy { it.midi }.joinToString(" ") { it.note(style).label }
