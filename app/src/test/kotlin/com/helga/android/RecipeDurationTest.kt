package com.helga.android

import com.helga.android.data.util.RecipeDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeDurationTest {

    @Test
    fun iso_minuten_und_stunden() {
        assertEquals(45, RecipeDuration.parseMinutes("PT45M"))
        assertEquals(90, RecipeDuration.parseMinutes("PT1H30M"))
        assertEquals(120, RecipeDuration.parseMinutes("PT2H"))
        assertEquals(75, RecipeDuration.parseMinutes("P0DT1H15M"))
    }

    @Test
    fun iso_sekunden_gerundet() {
        assertEquals(2, RecipeDuration.parseMinutes("PT90S"))
        assertNull(RecipeDuration.parseMinutes("PT20S"))
    }

    @Test
    fun freitext_minuten() {
        listOf("30 Min", "30 min.", "30 Minuten", "30").forEach { assertEquals(it, 30, RecipeDuration.parseMinutes(it)) }
    }

    @Test
    fun freitext_stunden_und_minuten() {
        listOf("1 Std 30 Min", "1 h 30 min", "1 Stunde 30 Minuten").forEach {
            assertEquals(it, 90, RecipeDuration.parseMinutes(it))
        }
    }

    @Test
    fun dezimalstunden() {
        assertEquals(90, RecipeDuration.parseMinutes("1,5 h"))
        assertEquals(90, RecipeDuration.parseMinutes("1.5 Std"))
        assertEquals(120, RecipeDuration.parseMinutes("2 Stunden"))
    }

    @Test
    fun bereich_nimmt_erste_zahl() {
        assertEquals(30, RecipeDuration.parseMinutes("30-40 Min"))
        assertEquals(20, RecipeDuration.parseMinutes("ca. 20 – 25 Minuten"))
    }

    @Test
    fun unbekannt_ist_null() {
        listOf("", "schnell", "PT", "abc 5x").forEach { assertNull(it, RecipeDuration.parseMinutes(it)) }
    }

    @Test
    fun toIso_wie_server() {
        assertEquals("", RecipeDuration.toIso(0))
        assertEquals("PT45M", RecipeDuration.toIso(45))
        assertEquals("PT1H", RecipeDuration.toIso(60))
        assertEquals("PT1H30M", RecipeDuration.toIso(90))
    }
}
