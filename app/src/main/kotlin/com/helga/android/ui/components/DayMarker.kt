package com.helga.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Feste Farbpalette für die Tagesmarker-Bibliothek (wochenplan A11) — dezente Tonfarben zur
 * Auswahl beim Anlegen. Farbe dient nur als Wiedererkennungshilfe: [DayMarkerChip] zeigt immer
 * auch den Namen als Text (ux-accessibility Regel 7 — nie Farbe als alleiniger Informationsträger).
 */
val dayMarkerColors: List<String> = listOf(
    "#2E7D32", "#1565C0", "#6A1B9A", "#E65100", "#B71C1C", "#00695C", "#5D4037", "#455A64",
)

fun parseMarkerColor(hex: String): Color = runCatching {
    Color(AndroidColor.parseColor(hex))
}.getOrDefault(Color.Gray)

/** Kompakter Chip für einen zugewiesenen Tagesmarker — Farbe + Name. */
@Composable
fun DayMarkerChip(name: String, color: String, modifier: Modifier = Modifier) {
    Surface(
        color = parseMarkerColor(color).copy(alpha = 0.25f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun DayMarkerChipPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayMarkerChip(name = "Urlaub", color = "#2E7D32")
            DayMarkerChip(name = "Gäste", color = "#E65100")
            DayMarkerChip(name = "Feiertag", color = "#455A64")
        }
    }
}
