package com.helga.android.ui.receipts.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.helga.android.R
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews

/** Ein Balken: [label] unter dem Balken, [value] in Euro, [highlighted] = laufende Periode. */
data class ChartBar(val label: String, val value: Double, val highlighted: Boolean = false)

/**
 * Balkendiagramm „Ausgaben je Monat/Woche" im Canvas-Eigenbau. Optional mit gestrichelter Budgetlinie.
 * [description] ist die Beschreibung für Screenreader; die Werte stehen zusätzlich als Text im Screen.
 */
@Composable
fun SpendingBarChart(
    bars: List<ChartBar>,
    budget: Double?,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return
    val showBudget = budget != null && budget > 0.0
    val maxValue = ChartMath.niceMax(maxOf(bars.maxOf { it.value }, if (showBudget) budget!! else 0.0))
    val barColor = MaterialTheme.colorScheme.primary
    val dimmedColor = barColor.copy(alpha = 0.45f)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val budgetColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            formatEuro(maxValue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = description },
        ) {
            val slot = size.width / bars.size
            val barWidth = slot * 0.6f
            val corner = 4.dp.toPx()
            bars.forEachIndexed { index, bar ->
                val barHeight = size.height * ChartMath.fraction(bar.value, maxValue)
                if (barHeight > 0f) {
                    val left = index * slot + (slot - barWidth) / 2f
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = size.height - barHeight, right = left + barWidth, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(corner), topRightCornerRadius = CornerRadius(corner),
                            )
                        )
                    }
                    drawPath(path, color = if (bar.highlighted) barColor else dimmedColor)
                }
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (showBudget) {
                val y = size.height * (1f - ChartMath.fraction(budget!!, maxValue))
                drawLine(
                    budgetColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val step = if (bars.size > 8) 2 else 1
            bars.forEachIndexed { index, bar ->
                Text(
                    if (index % step == 0) bar.label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        if (showBudget) {
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.width(16.dp).height(2.dp)) {
                    drawLine(budgetColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.cost_chart_budget_legend, formatEuro(budget!!)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun sampleMonths(): List<ChartBar> = listOf(
    120.0, 90.0, 150.0, 0.0, 80.0, 110.0, 95.0, 130.0, 70.0, 160.0, 140.0, 100.0,
).mapIndexed { i, v -> ChartBar(label = "%02d/26".format(i % 12 + 1), value = v, highlighted = i == 11) }

@ThemePreviews
@Composable
private fun SpendingBarChartMonthsPreview() = PreviewSurface {
    SpendingBarChart(bars = sampleMonths(), budget = 140.0, description = "Ausgaben der letzten 12 Monate", modifier = Modifier.padding(16.dp))
}

@ThemePreviews
@Composable
private fun SpendingBarChartWeeksPreview() = PreviewSurface {
    SpendingBarChart(
        bars = sampleMonths().map { it.copy(label = it.label.take(2)) }, budget = null,
        description = "Ausgaben der letzten 12 Wochen", modifier = Modifier.padding(16.dp),
    )
}
