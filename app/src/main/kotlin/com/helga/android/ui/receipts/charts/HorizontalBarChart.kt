package com.helga.android.ui.receipts.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews

/** Eine Zeile des Querbalkendiagramms. [detail] ist eine optionale Zusatzzeile (z. B. „5× gekauft"). */
data class ChartRow(val key: String, val label: String, val value: Double, val detail: String = "")

/** Querbalken-Rangliste („Ausgaben je Kategorie", „Top-Artikel"). Mit [onRowClick] sind die Zeilen antippbar. */
@Composable
fun HorizontalBarChart(
    rows: List<ChartRow>,
    description: String,
    modifier: Modifier = Modifier,
    onRowClick: ((String) -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    val maxValue = rows.maxOf { it.value }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            val rowModifier = if (onRowClick != null) {
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onRowClick(row.key) }
            } else {
                Modifier.fillMaxWidth()
            }
            Column(modifier = rowModifier, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(formatEuro(row.value), style = MaterialTheme.typography.bodyMedium)
                }
                if (row.detail.isNotBlank()) {
                    Text(
                        row.detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp)) {
                    val radius = CornerRadius(5.dp.toPx())
                    drawRoundRect(track, size = size, cornerRadius = radius)
                    val width = size.width * ChartMath.fraction(row.value, maxValue)
                    if (width > 0f) drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun HorizontalBarChartPreview() = PreviewSurface {
    HorizontalBarChart(
        rows = listOf(
            ChartRow("a", "Milchprodukte & Eier", 82.4, "12× gekauft"),
            ChartRow("b", "Obst & Gemüse", 61.0, "9× gekauft"),
            ChartRow("c", "Getränke", 23.5),
        ),
        description = "Ausgaben je Kategorie", modifier = Modifier.padding(16.dp), onRowClick = {},
    )
}
