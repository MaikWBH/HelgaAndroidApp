package com.helga.android

import com.helga.android.ui.recipes.DetectedTimer
import com.helga.android.ui.recipes.extractTimers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für die Timer-Erkennung in Kochschritt-Text (rezepte A3). */
class TimerDetectionTest {

    @Test
    fun `detects minutes in an instruction`() {
        val result = extractTimers("Backen für 20 Minuten bei 180 Grad")
        assertEquals(listOf(DetectedTimer("20 Minuten", 1200)), result)
    }

    @Test
    fun `detects hours and converts to seconds`() {
        val result = extractTimers("Kochen für 1 Stunde")
        assertEquals(listOf(DetectedTimer("1 Stunde", 3600)), result)
    }

    @Test
    fun `detects abbreviated seconds with a trailing period`() {
        val result = extractTimers("30 Sek. anbraten")
        assertEquals(listOf(DetectedTimer("30 Sek.", 30)), result)
    }

    @Test
    fun `detects the Min abbreviation without a period`() {
        val result = extractTimers("15 Min Ruhezeit")
        assertEquals(listOf(DetectedTimer("15 Min", 900)), result)
    }

    @Test
    fun `detects the Std abbreviation without a period`() {
        val result = extractTimers("2 Std Backzeit")
        assertEquals(listOf(DetectedTimer("2 Std", 7200)), result)
    }

    @Test
    fun `matching is case-insensitive`() {
        val result = extractTimers("10 MINUTEN ruhen lassen")
        assertEquals(listOf(DetectedTimer("10 MINUTEN", 600)), result)
    }

    @Test
    fun `duplicate durations with the same total seconds are deduplicated`() {
        val result = extractTimers("5 Minuten oder 300 Sekunden warten")
        assertEquals(listOf(DetectedTimer("5 Minuten", 300)), result)
    }

    @Test
    fun `a zero duration is not returned as a timer`() {
        val result = extractTimers("0 Minuten warten")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `text without any duration yields no timers`() {
        val result = extractTimers("Prise Salz hinzufügen und umrühren")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple distinct durations in one step are all detected in order`() {
        val result = extractTimers("Erst 5 Minuten anbraten, dann 30 Minuten köcheln lassen")
        assertEquals(
            listOf(DetectedTimer("5 Minuten", 300), DetectedTimer("30 Minuten", 1800)),
            result,
        )
    }
}
