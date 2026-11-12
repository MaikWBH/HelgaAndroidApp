package com.helga.android

import com.helga.android.data.receipts.ReceiptDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptDatesTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `dayToEpochMs is midnight utc and invalid input gives zero`() {
        assertEquals(1_794_268_800_000L, ReceiptDates.dayToEpochMs("2026-11-10"))
        assertEquals(0L, ReceiptDates.dayToEpochMs(""))
        assertEquals(0L, ReceiptDates.dayToEpochMs("10.11.2026"))
        assertEquals(0L, ReceiptDates.dayToEpochMs("2026-13-40"))
    }

    @Test
    fun `epochMsToDay uses the zone and ignores non positive values`() {
        // 2026-11-09 23:30 UTC ist in Berlin (UTC+1) bereits der 10.
        assertEquals("2026-11-10", ReceiptDates.epochMsToDay(1_794_268_800_000L - 30 * 60_000, berlin))
        assertEquals("2026-11-09", ReceiptDates.epochMsToDay(1_794_268_800_000L - 30 * 60_000, ZoneId.of("UTC")))
        assertEquals("", ReceiptDates.epochMsToDay(0L, berlin))
    }

    @Test
    fun `day and time validation`() {
        assertTrue(ReceiptDates.isValidDay("2026-11-10"))
        assertFalse(ReceiptDates.isValidDay("2026-02-30"))
        assertFalse(ReceiptDates.isValidDay(""))
        assertTrue(ReceiptDates.isValidTime("07:05"))
        assertFalse(ReceiptDates.isValidTime("7:05"))
        assertFalse(ReceiptDates.isValidTime("24:00"))
        assertFalse(ReceiptDates.isValidTime("12:60"))
    }

    @Test
    fun `displayDate prefers the printed day`() {
        assertEquals("10.11.2026", ReceiptDates.displayDate("2026-11-10", 0L, berlin))
        assertEquals("10.11.2026", ReceiptDates.displayDate("", 1_794_268_800_000L, berlin))
        assertEquals("", ReceiptDates.displayDate("", 0L, berlin))
    }

    @Test
    fun `cutoffDay is null for zero months and counts back otherwise`() {
        val today = LocalDate.of(2026, 11, 10)
        assertNull(ReceiptDates.cutoffDay(today, 0))
        assertEquals("2026-08-10", ReceiptDates.cutoffDay(today, 3))
        assertEquals("2025-11-10", ReceiptDates.cutoffDay(today, 12))
    }

    @Test
    fun `parseDisplayDay converts german input and rejects invalid dates`() {
        assertEquals("2026-11-10", ReceiptDates.parseDisplayDay("10.11.2026"))
        assertEquals("2026-01-05", ReceiptDates.parseDisplayDay(" 5.1.2026 "))
        assertEquals("", ReceiptDates.parseDisplayDay("  "))
        assertNull(ReceiptDates.parseDisplayDay("31.02.2026"))
        assertNull(ReceiptDates.parseDisplayDay("2026-11-10"))
        assertNull(ReceiptDates.parseDisplayDay("10.11.26"))
    }
}
