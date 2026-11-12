package com.helga.android

import com.helga.android.data.local.entity.ReceiptAggregateEntity
import com.helga.android.data.model.PeriodAmount
import com.helga.android.data.model.StatsRange
import com.helga.android.data.receipts.ReceiptStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReceiptStatsTest {

    private val today = LocalDate.of(2026, 11, 11)

    private fun row(
        dimension: String, period: String, key: String = "", label: String = "", amount: Double = 0.0,
        quantity: Double = 0.0, count: Int = 0, avg: Double = 0.0, discount: Double = 0.0,
    ) = ReceiptAggregateEntity(
        id = "$dimension|$period|$key", period = period, dimension = dimension, key = key, label = label,
        amount = amount, quantity = quantity, count = count, avgUnitPrice = avg, discount = discount,
    )

    // 15 Zeilen
    private val rows = listOf(
        row("total", "2026-11", amount = 100.0, count = 3, discount = 5.0),
        row("total", "2026-10", amount = 80.0, count = 2, discount = 2.0),
        row("total", "2026-09", amount = 60.0, count = 2),
        row("total", "2026-01", amount = 50.0, count = 1, discount = 1.0),
        row("week", "2026-W46", amount = 40.0, count = 1),
        row("week", "2026-W45", amount = 30.0, count = 1),
        row("store", "2026-11", "name:rewe", "REWE", amount = 70.0, count = 2),
        row("store", "2026-11", "name:aldi", "Aldi", amount = 30.0, count = 1),
        row("store", "2026-10", "name:rewe", "REWE Markt", amount = 50.0, count = 1),
        row("store", "2026-01", "name:rewe", "REWE alt", amount = 50.0, count = 1),
        row("category", "2026-11", "milch_eier", amount = 20.0, count = 4),
        row("category", "2026-10", "milch_eier", amount = 10.0, count = 2),
        row("product", "2026-11", "milch", "Milch", amount = 10.0, quantity = 5.0, count = 5, avg = 2.0),
        row("product", "2026-10", "milch", "Vollmilch", amount = 8.0, quantity = 4.0, count = 4, avg = 1.9),
        row("product", "2026-11", "brot", "Brot", amount = 6.0, quantity = 3.0, count = 3, avg = 0.0),
    ) + row("product", "2026-11", "apfel", "Äpfel", amount = 6.0, quantity = 2.0, count = 2, avg = 3.0)

    @Test
    fun `month and week periods are oldest first`() {
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), ReceiptStats.monthPeriods(today, 3))
        assertEquals(listOf("2026-W44", "2026-W45", "2026-W46"), ReceiptStats.weekPeriods(today, 3))
        assertEquals(12, ReceiptStats.monthPeriods(today, 12).size)
        assertEquals("2025-12", ReceiptStats.monthPeriods(today, 12).first())
    }

    @Test
    fun `iso week across the year boundary`() {
        // 03.01.2027 ist ein Sonntag und gehört zu 2026-W53
        assertEquals("2026-W53", ReceiptStats.weekPeriods(LocalDate.of(2027, 1, 3), 1).single())
        assertEquals("2027-W01", ReceiptStats.weekPeriods(LocalDate.of(2027, 1, 4), 1).single())
        assertEquals(listOf("2026-12", "2027-01"), ReceiptStats.monthPeriods(LocalDate.of(2027, 1, 15), 2))
    }

    @Test
    fun `series fills missing periods with zero`() {
        val series = ReceiptStats.monthSeries(rows, today, 4)
        assertEquals(
            listOf(PeriodAmount("2026-08", 0.0, 0), PeriodAmount("2026-09", 60.0, 2),
                PeriodAmount("2026-10", 80.0, 2), PeriodAmount("2026-11", 100.0, 3)),
            series,
        )
        val weeks = ReceiptStats.weekSeries(rows, today, 3)
        assertEquals(listOf(0.0, 30.0, 40.0), weeks.map { it.amount })
    }

    @Test
    fun `range months`() {
        assertEquals(setOf("2026-11"), ReceiptStats.rangeMonths(StatsRange.THIS_MONTH, today))
        assertEquals(3, ReceiptStats.rangeMonths(StatsRange.THREE_MONTHS, today)!!.size)
        assertEquals(12, ReceiptStats.rangeMonths(StatsRange.TWELVE_MONTHS, today)!!.size)
        assertEquals(null, ReceiptStats.rangeMonths(StatsRange.ALL, today))
    }

    @Test
    fun `store totals sum per key use the newest label and sort descending`() {
        val month = ReceiptStats.storeTotals(rows, StatsRange.THIS_MONTH, today)
        assertEquals(listOf("name:rewe" to 70.0, "name:aldi" to 30.0), month.map { it.key to it.amount })
        val three = ReceiptStats.storeTotals(rows, StatsRange.THREE_MONTHS, today)
        assertEquals(120.0, three.first { it.key == "name:rewe" }.amount, 1e-9)
        assertEquals("REWE", three.first { it.key == "name:rewe" }.label) // jüngste Periode
        val all = ReceiptStats.storeTotals(rows, StatsRange.ALL, today)
        assertEquals(170.0, all.first().amount, 1e-9)
        assertEquals(4, all.first().count) // 2 + 1 + 1 Bons
    }

    @Test
    fun `category totals`() {
        val three = ReceiptStats.categoryTotals(rows, StatsRange.THREE_MONTHS, today)
        assertEquals(1, three.size)
        assertEquals(30.0, three[0].amount, 1e-9)
        assertEquals(6, three[0].count)
    }

    @Test
    fun `top products sum quantity use the newest label and break ties by key`() {
        val top = ReceiptStats.topProducts(rows, StatsRange.THREE_MONTHS, today, limit = 3)
        assertEquals(listOf("milch", "apfel", "brot"), top.map { it.key }) // apfel/brot gleich (6,0): nach key
        assertEquals(18.0, top[0].amount, 1e-9)
        assertEquals(9.0, top[0].quantity, 1e-9)
        assertEquals("Milch", top[0].label)
        assertEquals(1, ReceiptStats.topProducts(rows, StatsRange.THREE_MONTHS, today, limit = 1).size)
    }

    @Test
    fun `product trend is ascending and skips periods without a price`() {
        assertEquals(listOf("2026-10" to 1.9, "2026-11" to 2.0), ReceiptStats.productTrend(rows, "milch").map { it.period to it.avgUnitPrice })
        assertEquals(emptyList<Any>(), ReceiptStats.productTrend(rows, "brot")) // avg 0 → weggelassen
        assertEquals(emptyList<Any>(), ReceiptStats.productTrend(rows, "unbekannt"))
    }

    @Test
    fun `current month total and discount total`() {
        assertEquals(100.0, ReceiptStats.currentMonthTotal(rows, today), 1e-9)
        assertEquals(0.0, ReceiptStats.currentMonthTotal(rows, LocalDate.of(2026, 12, 1)), 1e-9)
        assertEquals(5.0, ReceiptStats.discountTotal(rows, StatsRange.THIS_MONTH, today), 1e-9)
        assertEquals(7.0, ReceiptStats.discountTotal(rows, StatsRange.THREE_MONTHS, today), 1e-9)
        assertEquals(8.0, ReceiptStats.discountTotal(rows, StatsRange.ALL, today), 1e-9)
    }

    @Test
    fun `monthly average ignores empty periods`() {
        val series = ReceiptStats.monthSeries(rows, today, 4) // 0, 60, 80, 100
        assertEquals(80.0, ReceiptStats.monthlyAverage(series), 1e-9)
        assertEquals(0.0, ReceiptStats.monthlyAverage(emptyList()), 1e-9)
    }

    @Test
    fun `empty input gives empty results`() {
        assertEquals(emptyList<Any>(), ReceiptStats.storeTotals(emptyList(), StatsRange.ALL, today))
        assertEquals(emptyList<Any>(), ReceiptStats.topProducts(emptyList(), StatsRange.ALL, today))
        assertEquals(0.0, ReceiptStats.currentMonthTotal(emptyList(), today), 1e-9)
        assertEquals(12, ReceiptStats.monthSeries(emptyList(), today).size)
    }
}
