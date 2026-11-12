package com.helga.android.ui.receipts.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.helga.android.R
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews
import com.helga.android.ui.theme.chartOtherColor
import com.helga.android.ui.theme.chartSeriesColors
import kotlin.math.roundToInt

/**
 * Ringdiagramm „Anteil je Markt" mit Legende (Farbpunkt, Name, Betrag, Anteil). [centerText] steht in der Mitte;
 * leere [slices] zeichnen nichts — den Leertext liefert der Aufrufer.
 */
@Composable
fun ShareDonutChart(
    slices: List<ShareSlice>,
    centerText: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return
    val series = chartSeriesColors()
    val other = chartOtherColor()
    fun colorOf(index: Int, slice: ShareSlice) = if (slice.key == ChartMath.OTHER_KEY) other else series[index % series.size]

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            val colors = slices.mapIndexed { index, slice -> colorOf(index, slice) }
            Canvas(modifier = Modifier.size(160.dp).semantics { contentDescription = description }) {
                val stroke = 28.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val gap = if (slices.size > 1) 1.5f else 0f
                slices.forEachIndexed { index, slice ->
                    val sweep = (slice.sweepAngle - gap).coerceAtLeast(0.5f)
                    drawArc(
                        color = colors[index],
                        startAngle = slice.startAngle + gap / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
            }
            Text(centerText, style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { index, slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(colorOf(index, slice), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(formatEuro(slice.amount), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.cost_chart_percent, (slice.fraction * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ShareDonutChartPreview() = PreviewSurface {
    val items = listOf(
        com.helga.android.data.model.KeyAmount("a", "REWE", 120.0, 0.0, 4),
        com.helga.android.data.model.KeyAmount("b", "Aldi", 80.0, 0.0, 3),
        com.helga.android.data.model.KeyAmount("c", "Lidl", 60.0, 0.0, 2),
        com.helga.android.data.model.KeyAmount("d", "dm", 40.0, 0.0, 2),
        com.helga.android.data.model.KeyAmount("e", "Edeka", 30.0, 0.0, 1),
        com.helga.android.data.model.KeyAmount("f", "Netto", 20.0, 0.0, 1),
        com.helga.android.data.model.KeyAmount("g", "Penny", 10.0, 0.0, 1),
    )
    ShareDonutChart(
        slices = ChartMath.shareSlices(items, otherLabel = "Sonstige"), centerText = formatEuro(360.0),
        description = "Anteil je Markt", modifier = Modifier.padding(16.dp),
    )
}
