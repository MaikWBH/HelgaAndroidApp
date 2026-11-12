package com.helga.android

import com.helga.android.data.model.ProductCategory
import com.helga.android.data.util.AisleKeywords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüfzahlen der Stichwortliste laut `aisle-keywords.md` (Phase 3b). */
class AisleKeywordsTest {

    private val entries = AisleKeywords.entries

    @Test
    fun `Gesamtzahl ist 605`() {
        assertEquals(605, entries.size)
    }

    @Test
    fun `Anzahl je Kategorie stimmt`() {
        val expected = mapOf(
            "alkohol" to 24, "brot_backwaren" to 33, "drogerie" to 38, "feinkost" to 34, "fisch" to 24,
            "fleisch_wurst" to 49, "fruehstueck" to 29, "getraenke" to 27, "haushalt" to 41, "milch_eier" to 51,
            "obst_gemuese" to 96, "suesses_snacks" to 38, "tiefkuehl" to 23, "trockenware" to 44,
            "wuerzen_backen" to 54,
        )
        assertEquals(expected, entries.groupingBy { it.category }.eachCount())
    }

    @Test
    fun `fuenf Vorrang-Eintraege und 26 Phrasen`() {
        assertEquals(
            listOf("tk", "tiefkühl", "tiefgekühlt", "konserve", "dose"),
            entries.filter { it.priority }.map { it.word },
        )
        assertEquals(26, entries.count { ' ' in it.word })
    }

    @Test
    fun `keine Dublette nach Normalisierung`() {
        val duplicates = entries.groupBy { it.word }.filterValues { it.size > 1 }.keys
        assertTrue("Dubletten: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `jede Kategorie ist ein Gang-Schluessel`() {
        val aisleKeys = ProductCategory.aisleCategories.map { it.key }.toSet()
        assertTrue(entries.all { it.category in aisleKeys })
    }

    @Test
    fun `normalize folgt den Regeln`() {
        assertEquals("tomaten", AisleKeywords.normalize("  TOMATEN (klein) "))
        assertEquals("h milch", AisleKeywords.normalize("H-Milch"))
        assertEquals("", AisleKeywords.normalize("(nur Klammer)"))
        assertEquals("crème fraîche 20", AisleKeywords.normalize("Crème  Fraîche, 20%"))
    }
}
