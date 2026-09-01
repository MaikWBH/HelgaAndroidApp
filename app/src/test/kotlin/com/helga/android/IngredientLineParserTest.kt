package com.helga.android

import com.helga.android.data.util.IngredientLineParser
import com.helga.android.data.util.ParsedIngredientLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für IngredientLineParser (einkaufsliste A3) — Freitext-Zutatenzeilen. */
class IngredientLineParserTest {

    @Test
    fun `quantity directly followed by unit, no space`() {
        assertEquals(
            ParsedIngredientLine(200.0, "g", "Mehl", ""),
            IngredientLineParser.parse("200g Mehl"),
        )
    }

    @Test
    fun `quantity, unit and a trailing note in parentheses`() {
        assertEquals(
            ParsedIngredientLine(2.0, "EL", "Zucker", "gehäuft"),
            IngredientLineParser.parse("2 EL Zucker (gehäuft)"),
        )
    }

    @Test
    fun `comma as decimal separator`() {
        assertEquals(
            ParsedIngredientLine(1.5, "l", "Milch", ""),
            IngredientLineParser.parse("1,5 l Milch"),
        )
    }

    @Test
    fun `unicode fraction alone`() {
        assertEquals(
            ParsedIngredientLine(0.5, "TL", "Salz", ""),
            IngredientLineParser.parse("½ TL Salz"),
        )
    }

    @Test
    fun `whole number combined with a unicode fraction`() {
        assertEquals(
            ParsedIngredientLine(1.5, "EL", "Öl", ""),
            IngredientLineParser.parse("1½ EL Öl"),
        )
    }

    @Test
    fun `ascii fraction is divided`() {
        assertEquals(
            ParsedIngredientLine(0.5, "TL", "Salz", ""),
            IngredientLineParser.parse("1/2 TL Salz"),
        )
    }

    @Test
    fun `range takes the first number`() {
        val result = IngredientLineParser.parse("2-3 Zwiebeln")
        assertEquals(2.0, result.quantity, 0.0001)
        assertEquals("Zwiebeln", result.food)
    }

    @Test
    fun `line without a recognizable quantity or unit keeps everything as food`() {
        assertEquals(
            ParsedIngredientLine(0.0, "", "Salz", ""),
            IngredientLineParser.parse("Salz"),
        )
    }

    @Test
    fun `blank input returns all defaults`() {
        assertEquals(ParsedIngredientLine(), IngredientLineParser.parse(""))
        assertEquals(ParsedIngredientLine(), IngredientLineParser.parse("   "))
    }

    @Test
    fun `unit abbreviation with trailing period is recognized`() {
        val result = IngredientLineParser.parse("2 EL. Zucker")
        assertEquals("EL", result.unit)
        assertEquals("Zucker", result.food)
    }

    @Test
    fun `unrecognized unit word is not split off, stays part of food`() {
        val result = IngredientLineParser.parse("3 Karotten")
        assertEquals(3.0, result.quantity, 0.0001)
        assertEquals("", result.unit)
        assertEquals("Karotten", result.food)
    }

    @Test
    fun `markdown header line is detected`() {
        assertTrue(IngredientLineParser.isHeaderLine("Für den Teig:"))
        assertTrue(IngredientLineParser.isHeaderLine("**Sauce**"))
        assertTrue(IngredientLineParser.isHeaderLine("**Sauce**:"))
    }

    @Test
    fun `a real ingredient line is not mistaken for a header`() {
        assertFalse(IngredientLineParser.isHeaderLine("200g Mehl"))
        assertFalse(IngredientLineParser.isHeaderLine("Zutaten"))
        assertFalse(IngredientLineParser.isHeaderLine(""))
    }
}
