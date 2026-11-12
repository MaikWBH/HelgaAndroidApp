package com.helga.android.ui.receipts.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.helga.android.R
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews

/** Ein Punkt der Preisentwicklung; [label] steht nur für den ersten und letzten Punkt unter der Linie. */
data class ChartPoint(val label: String, val value: Double)

/**
 * Liniendiagramm (z. B. Preisentwicklung eines Artikels) im Canvas-Eigenbau. Mit weniger als zwei Punkten
 * erscheint ein Hinweistext statt der Zeichnung.
 */
@Composable
fun TrendLineChart(
    points: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Text(
            stringResource(R.string.chart_trend_too_few),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val positions = ChartMath.linePositions(points.map { it.value })

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatEuro(points.maxOf { it.value }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatEuro(points.minOf { it.value }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 4.dp).semantics { contentDescription = description },
        ) {
            val pad = 12.dp.toPx()
            val width = size.width - 2 * pad
            val height = size.height - 2 * pad
            drawLine(guide, Offset(0f, pad), Offset(size.width, pad), strokeWidth = 1.dp.toPx())
            drawLine(guide, Offset(0f, size.height - pad), Offset(size.width, size.height - pad), strokeWidth = 1.dp.toPx())
            val offsets = positions.map { (x, y) -> Offset(pad + x * width, pad + y * height) }
            val path = Path().apply {
                offsets.forEachIndexed { index, offset -> if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y) }
            }
            drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
            offsets.forEach { offset ->
                drawCircle(surface, radius = 6.dp.toPx(), center = offset)
                drawCircle(line, radius = 4.dp.toPx(), center = offset)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@ThemePreviews
@Composable
private fun TrendLineChartPreview() = PreviewSurface {
    TrendLineChart(
        points = listOf(
            ChartPoint("09/26", 1.79), ChartPoint("10/26", 1.89), ChartPoint("11/26", 1.69), ChartPoint("12/26", 1.99),
        ),
        description = "Preisverlauf", modifier = Modifier.padding(16.dp),
    )
}

@ThemePreviews
@Composable
private fun TrendLineChartTooFewPreview() = PreviewSurface {
    TrendLineChart(points = listOf(ChartPoint("11/26", 1.69)), description = "Preisverlauf", modifier = Modifier.padding(16.dp))
}
