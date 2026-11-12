package com.helga.android

import com.helga.android.data.model.KeyAmount
import com.helga.android.ui.receipts.charts.ChartMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    private fun item(key: String, amount: Double) = KeyAmount(key, key.uppercase(), amount, 0.0, 1)

    @Test
    fun `niceMax picks the smallest nice value`() {
        assertEquals(1.0, ChartMath.niceMax(0.0), 0.0)
        assertEquals(1.0, ChartMath.niceMax(-5.0), 0.0)
        assertEquals(200.0, ChartMath.niceMax(137.4), 1e-9)
        assertEquals(250.0, ChartMath.niceMax(250.0), 1e-9)
        assertEquals(100.0, ChartMath.niceMax(100.0), 1e-9)
        assertEquals(500.0, ChartMath.niceMax(251.0), 1e-9)
        assertEquals(1000.0, ChartMath.niceMax(501.0), 1e-9)
        assertEquals(2.5, ChartMath.niceMax(2.1), 1e-9)
        assertEquals(0.5, ChartMath.niceMax(0.4), 1e-9)
        assertEquals(10.0, ChartMath.niceMax(7.0), 1e-9)
    }

    @Test
    fun `fraction is clamped`() {
        assertEquals(0.5f, ChartMath.fraction(50.0, 100.0), 1e-6f)
        assertEquals(1f, ChartMath.fraction(150.0, 100.0), 0f)
        assertEquals(0f, ChartMath.fraction(-5.0, 100.0), 0f)
        assertEquals(0f, ChartMath.fraction(5.0, 0.0), 0f)
    }

    @Test
    fun `seven stores become five slices plus other and the angles add up to 360`() {
        val items = (1..7).map { item("m$it", it * 10.0) }
        val slices = ChartMath.shareSlices(items, maxSlices = 5, otherLabel = "Sonstige")
        assertEquals(6, slices.size)
        assertEquals(listOf("m7", "m6", "m5", "m4", "m3", ChartMath.OTHER_KEY), slices.map { it.key })
        assertEquals("Sonstige", slices.last().label)
        assertEquals(30.0, slices.last().amount, 1e-9) // 10 + 20
        assertEquals(360f, slices.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.01f)
        assertEquals(-90f, slices.first().startAngle, 0f)
        assertEquals(slices[0].startAngle + slices[0].sweepAngle, slices[1].startAngle, 0.001f)
    }

    @Test
    fun `slices ignore non positive amounts and empty input`() {
        assertTrue(ChartMath.shareSlices(emptyList(), otherLabel = "x").isEmpty())
        assertTrue(ChartMath.shareSlices(listOf(item("a", 0.0), item("b", -3.0)), otherLabel = "x").isEmpty())
        val single = ChartMath.shareSlices(listOf(item("a", 5.0)), otherLabel = "x")
        assertEquals(1, single.size)
        assertEquals(360f, single[0].sweepAngle, 0.001f)
    }

    @Test
    fun `line positions normalise to the min max range`() {
        val points = ChartMath.linePositions(listOf(1.0, 3.0, 2.0))
        assertEquals(listOf(0f, 0.5f, 1f), points.map { it.first })
        assertEquals(listOf(1f, 0f, 0.5f), points.map { it.second })
        assertEquals(listOf(0.5f to 0.5f), ChartMath.linePositions(listOf(4.0)))
        assertEquals(listOf(0.5f, 0.5f), ChartMath.linePositions(listOf(2.0, 2.0)).map { it.second })
        assertTrue(ChartMath.linePositions(emptyList()).isEmpty())
    }

    @Test
    fun `period labels`() {
        assertEquals("11/26", ChartMath.periodLabel("2026-11"))
        assertEquals("46", ChartMath.periodLabel("2026-W46"))
        assertEquals("1", ChartMath.periodLabel("2027-W01"))
        assertEquals("abc", ChartMath.periodLabel("abc"))
    }
}
