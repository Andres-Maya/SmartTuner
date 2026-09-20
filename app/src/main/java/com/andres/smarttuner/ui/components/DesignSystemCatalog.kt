package com.andres.smarttuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andres.smarttuner.ui.theme.SmartTunerTheme
import com.andres.smarttuner.ui.theme.TunerTheme
import com.andres.smarttuner.ui.tuner.LedTuningArc

/**
 * Catálogo del sistema de diseño. Ábrelo en la vista previa de Android Studio para ver
 * todos los tokens y componentes juntos antes de usarlos en una pantalla.
 */
@Preview(name = "Sistema de diseño", showBackground = true, backgroundColor = 0xFF090D1C, widthDp = 360, heightDp = 1800)
@Composable
private fun DesignSystemCatalog() {
    SmartTunerTheme {
        val colors = TunerTheme.colors
        val spacing = TunerTheme.spacing
        val typography = TunerTheme.typography

        Column(
            Modifier
                .background(colors.screenBrush)
                .verticalScroll(rememberScrollState())
                .padding(spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        ) {
            CatalogSection("Colores") {
                listOf(
                    "background" to colors.background,
                    "surface" to colors.surface,
                    "surfaceHigh" to colors.surfaceHigh,
                    "accent" to colors.accent,
                    "accentAlt" to colors.accentAlt,
                    "textPrimary" to colors.textPrimary,
                    "textMuted" to colors.textMuted,
                    "inTune" to colors.inTune,
                    "nearlyInTune" to colors.nearlyInTune,
                    "outOfTune" to colors.outOfTune,
                ).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                    }
                }
            }

            CatalogSection("Tipografía") {
                listOf(
                    "appTitle" to typography.appTitle,
                    "screenTitle" to typography.screenTitle,
                    "title" to typography.title,
                    "body" to typography.body,
                    "caption" to typography.caption,
                    "footnote" to typography.footnote,
                    "OVERLINE" to typography.overline,
                    "button" to typography.button,
                    "status" to typography.status,
                ).forEach { (name, style) -> TypeSample(name, style) }
            }

            CatalogSection("Visor") {
                LedTuningArc(
                    cents = -18f,
                    hasSignal = true,
                    inTune = false,
                    color = colors.nearlyInTune,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TunerTheme.sizes.ledArc),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SegmentText(
                        text = "A",
                        color = colors.inTune,
                        modifier = Modifier
                            .width(TunerTheme.sizes.displayLetter * 0.58f)
                            .height(TunerTheme.sizes.displayLetter),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        DisplayCell(Modifier.size(TunerTheme.sizes.displayCell)) {
                            SegmentAccidental(sharp = true, lit = true, color = colors.accent, modifier = Modifier.fillMaxSize())
                        }
                        DisplayCell(Modifier.size(TunerTheme.sizes.displayCell)) {
                            SegmentText("4", colors.accent, Modifier.fillMaxSize())
                        }
                    }
                }
            }

            CatalogSection("Botones") {
                PrimaryButton("Identificar instrumento", onClick = {}, leading = "✦", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    SecondaryButton("Reintentar", onClick = {}, modifier = Modifier.weight(1f))
                    PrimaryButton("Aceptar", onClick = {}, modifier = Modifier.weight(1f))
                }
                GhostButton("Cancelar", onClick = {})
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    IconCircleButton(onClick = {}, contentDescription = "Volver") { BackChevron() }
                    var style by remember { mutableStateOf("♯") }
                    SegmentedToggle(
                        options = listOf("♯" to "♯", "♭" to "♭"),
                        selected = style,
                        onSelect = { style = it },
                        contentDescription = "Alteraciones",
                    )
                }
                Stepper("La 440", onDecrement = {}, onIncrement = {}, decrementDescription = "−", incrementDescription = "+")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    IconCircleButton(onClick = {}, contentDescription = "Apariencia") { PaletteIcon() }
                }
            }

            CatalogSection("Indicadores") {
                StatusText("¡Afinado!", colors.inTune)
                StatusText("Esperando sonido…", colors.textMuted, idle = true, pulsing = true)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Badge("82% · confianza alta", colors.inTune)
                    Badge("35%", colors.outOfTune)
                }
                StatRow(listOf("FRECUENCIA" to "440.0 Hz", "DESVIACIÓN" to "+3 ¢", "OBJETIVO" to "440.0 Hz"))
                ProbabilityBar(0.72f, Modifier.fillMaxWidth())
            }

            CatalogSection("Superficies") {
                TunerCard {
                    SectionLabel("PENTAGRAMA")
                    Spacer(Modifier.height(spacing.sm))
                    Text("Contenido de la tarjeta", color = colors.textPrimary, style = typography.body)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    var selected by remember { mutableStateOf(0) }
                    listOf("Violonchelo" to 0.58f, "Viola" to 0.28f).forEachIndexed { index, (name, probability) ->
                        SelectableOption(selected = selected == index, onSelect = { selected = index }) {
                            Text(name, color = colors.textPrimary, style = typography.bodySmall, modifier = Modifier.width(96.dp))
                            ProbabilityBar(probability, Modifier.weight(1f))
                        }
                    }
                }
                MessageBlock("No reconocí un instrumento", "Acércate al micrófono y toca varias notas claras.")
            }
        }
    }
}

@Composable
private fun CatalogSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TunerTheme.spacing.md)) {
        Text(title, color = TunerTheme.colors.accent, style = TunerTheme.typography.title)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(TunerTheme.shapes.chip)
            .background(TunerTheme.colors.surface)
            .padding(TunerTheme.spacing.sm),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(TunerTheme.shapes.pill)
                .background(color),
        )
        Spacer(Modifier.width(TunerTheme.spacing.sm))
        Text(name, color = TunerTheme.colors.textPrimary, style = TunerTheme.typography.footnote)
    }
}

@Composable
private fun TypeSample(name: String, style: TextStyle) {
    Text("$name · ${style.fontSize}", color = TunerTheme.colors.textPrimary, style = style)
}
