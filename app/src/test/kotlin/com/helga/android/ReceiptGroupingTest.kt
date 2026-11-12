package com.helga.android

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.ui.receipts.ReceiptStatusKind
import com.helga.android.ui.receipts.groupReceiptsByMonth
import com.helga.android.ui.receipts.receiptMonthKey
import com.helga.android.ui.receipts.receiptStatusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class ReceiptGroupingTest {

    private val utc = ZoneId.of("UTC")

    private fun receipt(id: String, day: String = "", date: Long = 0L, status: String = "confirmed", parse: String = "done") =
        ReceiptEntity(id = id, purchaseDay = day, purchaseDate = date, status = status, parseStatus = parse)

    @Test
    fun `month key prefers the printed day and falls back to the timestamp`() {
        assertEquals("2026-11", receiptMonthKey(receipt("a", day = "2026-11-30"), utc))
        assertEquals("2026-11", receiptMonthKey(receipt("b", date = 1_794_268_800_000L), utc))
        assertEquals("", receiptMonthKey(receipt("c"), utc))
        // ungültiger Tag → Zeitstempel
        assertEquals("2026-11", receiptMonthKey(receipt("d", day = "kaputt", date = 1_794_268_800_000L), utc))
    }

    @Test
    fun `receipts are grouped by month keeping the order`() {
        val groups = groupReceiptsByMonth(
            listOf(
                receipt("a", day = "2026-11-10"), receipt("b", day = "2026-11-02"),
                receipt("c", day = "2026-10-30"), receipt("d"),
            ),
            utc,
        )
        assertEquals(listOf("2026-11", "2026-10", ""), groups.map { it.first })
        assertEquals(listOf("a", "b"), groups[0].second.map { it.id })
    }

    @Test
    fun `status kind has failed before provisional before review`() {
        assertEquals(ReceiptStatusKind.FAILED, receiptStatusKind(receipt("a", status = "provisional", parse = "failed")))
        assertEquals(ReceiptStatusKind.PROVISIONAL, receiptStatusKind(receipt("b", status = "provisional")))
        assertEquals(ReceiptStatusKind.REVIEW, receiptStatusKind(receipt("c", status = "review")))
        assertNull(receiptStatusKind(receipt("d", status = "confirmed")))
        assertNull(receiptStatusKind(receipt("e", status = "scanned")))
    }
}
