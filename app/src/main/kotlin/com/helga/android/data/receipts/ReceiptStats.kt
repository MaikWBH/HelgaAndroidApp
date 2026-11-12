package com.helga.android.data.receipts

import com.helga.android.data.local.entity.ReceiptAggregateEntity
import com.helga.android.data.model.KeyAmount
import com.helga.android.data.model.PeriodAmount
import com.helga.android.data.model.StatsRange
import com.helga.android.data.model.TrendPoint
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields

/**
 * Auswertung auf dem lokalen Abzug der Server-Aggregate (`receipt_aggregates`). Reines Kotlin, ohne Android:
 * die Diagramme lesen ausschließlich hieraus, daher funktionieren sie offline für die gesamte Zeit.
 */
object ReceiptStats {

    private const val TOTAL = "total"
    private const val WEEK = "week"
    private const val STORE = "store"
    private const val CATEGORY = "category"
    private const val PRODUCT = "product"

    /** Die letzten [n] Kalendermonate als `YYYY-MM`, älteste zuerst (inklusive des aktuellen). */
    fun monthPeriods(today: LocalDate, n: Int): List<String> {
        val current = YearMonth.from(today)
        return (n - 1 downTo 0).map { current.minusMonths(it.toLong()).toString() }
    }

    /** Die letzten [n] ISO-Wochen als `YYYY-Www`, älteste zuerst (inklusive der aktuellen). */
    fun weekPeriods(today: LocalDate, n: Int): List<String> =
        (n - 1 downTo 0).map {
            val date = today.minusWeeks(it.toLong())
            "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        }

    fun monthSeries(rows: List<ReceiptAggregateEntity>, today: LocalDate, n: Int = 12): List<PeriodAmount> =
        series(rows, TOTAL, monthPeriods(today, n))

    fun weekSeries(rows: List<ReceiptAggregateEntity>, today: LocalDate, n: Int = 12): List<PeriodAmount> =
        series(rows, WEEK, weekPeriods(today, n))

    private fun series(rows: List<ReceiptAggregateEntity>, dimension: String, periods: List<String>): List<PeriodAmount> {
        val byPeriod = rows.filter { it.dimension == dimension }.associateBy { it.period }
        return periods.map { period ->
            byPeriod[period]?.let { PeriodAmount(period, it.amount, it.count) } ?: PeriodAmount(period, 0.0, 0)
        }
    }

    /** Monate des Zeitraums; `ALL` → `null` (keine Einschränkung). */
    fun rangeMonths(range: StatsRange, today: LocalDate): Set<String>? = when (range) {
        StatsRange.THIS_MONTH -> monthPeriods(today, 1).toSet()
        StatsRange.THREE_MONTHS -> monthPeriods(today, 3).toSet()
        StatsRange.TWELVE_MONTHS -> monthPeriods(today, 12).toSet()
        StatsRange.ALL -> null
    }

    fun storeTotals(rows: List<ReceiptAggregateEntity>, range: StatsRange, today: LocalDate): List<KeyAmount> =
        totals(rows, STORE, range, today)

    fun categoryTotals(rows: List<ReceiptAggregateEntity>, range: StatsRange, today: LocalDate): List<KeyAmount> =
        totals(rows, CATEGORY, range, today)

    fun topProducts(rows: List<ReceiptAggregateEntity>, range: StatsRange, today: LocalDate, limit: Int = 10): List<KeyAmount> =
        totals(rows, PRODUCT, range, today).take(limit)

    /** Summiert je `key` über den Zeitraum; absteigend nach Betrag, bei Gleichstand stabil nach `key`. */
    private fun totals(rows: List<ReceiptAggregateEntity>, dimension: String, range: StatsRange, today: LocalDate): List<KeyAmount> {
        val months = rangeMonths(range, today)
        return rows.filter { it.dimension == dimension && (months == null || it.period in months) }
            .groupBy { it.key }
            .map { (key, group) ->
                KeyAmount(
                    key = key,
                    label = group.maxByOrNull { it.period }?.label.orEmpty(), // Label der jüngsten Periode
                    amount = group.sumOf { it.amount },
                    quantity = group.sumOf { it.quantity },
                    count = group.sumOf { it.count },
                )
            }
            .sortedWith(compareByDescending<KeyAmount> { it.amount }.thenBy { it.key })
    }

    /** Verlauf des durchschnittlichen Stückpreises eines Artikels, älteste Periode zuerst; ohne Preis 0 weggelassen. */
    fun productTrend(rows: List<ReceiptAggregateEntity>, key: String): List<TrendPoint> =
        rows.filter { it.dimension == PRODUCT && it.key == key && it.avgUnitPrice > 0.0 }
            .sortedBy { it.period }
            .map { TrendPoint(it.period, it.avgUnitPrice) }

    /** Ausgaben des laufenden Kalendermonats (für den Budgetvergleich). */
    fun currentMonthTotal(rows: List<ReceiptAggregateEntity>, today: LocalDate): Double =
        rows.firstOrNull { it.dimension == TOTAL && it.period == YearMonth.from(today).toString() }?.amount ?: 0.0

    /** Summe aller Rabatte (Bon- und Positionsrabatte) im Zeitraum. */
    fun discountTotal(rows: List<ReceiptAggregateEntity>, range: StatsRange, today: LocalDate): Double {
        val months = rangeMonths(range, today)
        return rows.filter { it.dimension == TOTAL && (months == null || it.period in months) }.sumOf { it.discount }
    }

    /** Mittelwert über die Perioden mit Ausgaben (> 0); leer → 0. */
    fun monthlyAverage(series: List<PeriodAmount>): Double {
        val active = series.filter { it.amount > 0.0 }
        return if (active.isEmpty()) 0.0 else active.sumOf { it.amount } / active.size
    }
}
