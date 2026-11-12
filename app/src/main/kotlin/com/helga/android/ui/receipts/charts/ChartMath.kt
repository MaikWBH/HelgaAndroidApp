package com.helga.android.ui.receipts.charts

import com.helga.android.data.model.KeyAmount
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Eine Scheibe des Ringdiagramms. Winkel in Grad, Start bei −90° (12 Uhr). */
data class ShareSlice(
    val key: String,
    val label: String,
    val amount: Double,
    val fraction: Float,
    val startAngle: Float,
    val sweepAngle: Float,
)

/** Rechenkern der selbst gezeichneten Diagramme (reines Kotlin, kein Compose, voll testbar). */
object ChartMath {

    const val OTHER_KEY = "__other"

    /** Kleinster „schöner" Wert aus {1, 2, 2.5, 5, 10} × 10ⁿ, der ≥ [value] ist; `value <= 0` → 1.0. */
    fun niceMax(value: Double): Double {
        if (value <= 0.0) return 1.0
        val exponent = floor(log10(value)).toInt()
        val base = 10.0.pow(exponent)
        for (step in listOf(1.0, 2.0, 2.5, 5.0, 10.0)) {
            val candidate = step * base
            if (candidate >= value - value * 1e-12) return candidate
        }
        return 10.0 * base
    }

    /** Anteil von [value] an [max], auf 0–1 begrenzt; `max <= 0` → 0. */
    fun fraction(value: Double, max: Double): Float =
        if (max <= 0.0) 0f else (value / max).coerceIn(0.0, 1.0).toFloat()

    /**
     * Teilt die Beträge in Ringscheiben: nur `amount > 0`, absteigend; ab dem `maxSlices + 1`-ten Eintrag zu
     * „Sonstige" ([OTHER_KEY], [otherLabel]) zusammengefasst. Start bei −90°, die Winkel ergeben 360°.
     */
    fun shareSlices(items: List<KeyAmount>, maxSlices: Int = 5, otherLabel: String): List<ShareSlice> {
        val positive = items.filter { it.amount > 0.0 }.sortedByDescending { it.amount }
        if (positive.isEmpty()) return emptyList()
        val head = positive.take(maxSlices)
        val rest = positive.drop(maxSlices)
        val entries = head.map { Triple(it.key, it.label, it.amount) } +
            if (rest.isEmpty()) emptyList() else listOf(Triple(OTHER_KEY, otherLabel, rest.sumOf { it.amount }))
        val total = entries.sumOf { it.third }
        var start = -90f
        return entries.map { (key, label, amount) ->
            val fraction = (amount / total).toFloat()
            val sweep = fraction * 360f
            ShareSlice(key, label, amount, fraction, start, sweep).also { start += sweep }
        }
    }

    /**
     * Positionen der Linienpunkte als (x, y) in 0–1: x gleichmäßig verteilt, y = 1 − auf Min/Max normiert
     * (oben = größter Wert). Ein Wert oder lauter gleiche Werte → y = 0.5.
     */
    fun linePositions(values: List<Double>): List<Pair<Float, Float>> {
        if (values.isEmpty()) return emptyList()
        val min = values.min()
        val max = values.max()
        val range = max - min
        return values.mapIndexed { index, value ->
            val x = if (values.size == 1) 0.5f else index.toFloat() / (values.size - 1)
            val y = if (values.size == 1 || range <= 0.0) 0.5f else (1.0 - (value - min) / range).toFloat()
            x to y
        }
    }

    /** Kurzes Achsenlabel: `2026-11` → `11/26`, `2026-W46` → `46`; sonst unverändert. */
    fun periodLabel(period: String): String = when {
        Regex("""\d{4}-\d{2}""").matches(period) -> "${period.substring(5, 7)}/${period.substring(2, 4)}"
        Regex("""\d{4}-W\d{2}""").matches(period) -> period.substring(period.indexOf('W') + 1).trimStart('0').ifEmpty { "0" }
        else -> period
    }
}
