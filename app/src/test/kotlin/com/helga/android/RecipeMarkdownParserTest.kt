package com.helga.android

import com.helga.android.data.util.MarkdownErrorCode
import com.helga.android.data.util.MarkdownFatal
import com.helga.android.data.util.RecipeMarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMarkdownParserTest {

    private fun recipe(title: String, extra: String = "") =
        "# $title\n$extra\n## Zutaten\n- 1 Prise Salz\n\n## Zubereitung\n1. Würzen."

    @Test
    fun leere_eingabe_ist_fatal() {
        assertEquals(MarkdownFatal.EMPTY_INPUT, RecipeMarkdownParser.parse("").fatal)
        assertEquals(MarkdownFatal.EMPTY_INPUT, RecipeMarkdownParser.parse("  \n\t\n").fatal)
        assertEquals(MarkdownFatal.EMPTY_INPUT, RecipeMarkdownParser.parse("```markdown\n```").fatal)
    }

    @Test
    fun zu_langer_text_ist_fatal() {
        val result = RecipeMarkdownParser.parse("x".repeat(RecipeMarkdownParser.MAX_CHARS + 1))
        assertEquals(MarkdownFatal.TOO_LONG, result.fatal)
        assertTrue(result.recipes.isEmpty())
    }

    @Test
    fun mehr_als_50_rezepte_ist_fatal() {
        val text = (1..51).joinToString("\n---\n") { recipe("Rezept $it") }
        val result = RecipeMarkdownParser.parse(text)
        assertEquals(MarkdownFatal.TOO_MANY_RECIPES, result.fatal)
        assertTrue(result.recipes.isEmpty())
        val ok = RecipeMarkdownParser.parse((1..50).joinToString("\n---\n") { recipe("Rezept $it") })
        assertNull(ok.fatal)
        assertEquals(50, ok.recipes.size)
    }

    @Test
    fun code_zaun_bom_und_crlf_werden_entfernt() {
        val text = "﻿```markdown\r\n# Linsensuppe\r\n\r\nHerzhaft.\r\n```"
        val result = RecipeMarkdownParser.parse(text)
        assertNull(result.fatal)
        assertEquals(1, result.recipes.size)
        assertEquals("Linsensuppe", result.recipes[0].name)
        assertEquals("Herzhaft.", result.recipes[0].description)
    }

    @Test
    fun trennlinien_und_neue_h1_teilen_rezepte() {
        val text = listOf(recipe("A"), "---", recipe("B"), "***", recipe("C"), "___", recipe("D"))
            .joinToString("\n")
        assertEquals(listOf("A", "B", "C", "D"), RecipeMarkdownParser.parse(text).recipes.map { it.name })
        val h1Only = recipe("E") + "\n" + recipe("F")
        val result = RecipeMarkdownParser.parse(h1Only)
        assertEquals(listOf("E", "F"), result.recipes.map { it.name })
        assertEquals(listOf(1, 2), result.recipes.map { it.blockIndex })
    }

    @Test
    fun fehlender_titel_ist_blockfehler_andere_bloecke_bleiben() {
        val text = "Nur Text ohne Überschrift\n\n## Zutaten\n- 1 Ei\n---\n" + recipe("Gut")
        val result = RecipeMarkdownParser.parse(text)
        assertEquals(1, result.errors.size)
        assertEquals(1, result.errors[0].blockIndex)
        assertEquals("", result.errors[0].title)
        assertEquals(MarkdownErrorCode.MISSING_TITLE, result.errors[0].code)
        assertEquals(listOf("Gut"), result.recipes.map { it.name })
        assertEquals(2, result.recipes[0].blockIndex)
    }

    @Test
    fun titel_und_beschreibung_aus_fliesstext_und_zitat() {
        val text = "Vorspann wird ignoriert\n# **Spaghetti aglio e olio** #\n\n> Schnell und lecker.\nMit Knoblauch.\n\n" +
            "## Zutaten\n- 1 Prise Salz\n## Zubereitung\n1. Würzen."
        val parsed = RecipeMarkdownParser.parse(text).recipes.single()
        assertEquals("Spaghetti aglio e olio", parsed.name)
        assertEquals("Schnell und lecker.\nMit Knoblauch.", parsed.description)
        val fallback = RecipeMarkdownParser.parse("## Zutaten\n- Ei\n\n### Pfannkuchen\nText").recipes.single()
        assertEquals("Pfannkuchen", fallback.name)
        val long = RecipeMarkdownParser.parse("# " + "a".repeat(300)).recipes.single()
        assertEquals(200, long.name.length)
    }
}
