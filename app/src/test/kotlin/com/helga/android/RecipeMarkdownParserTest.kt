package com.helga.android

import com.helga.android.data.model.MealSlots
import com.helga.android.data.util.MarkdownErrorCode
import com.helga.android.data.util.MarkdownFatal
import com.helga.android.data.util.MarkdownWarningCode
import com.helga.android.data.util.ParsedRecipe
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

    private fun parseOne(head: String): ParsedRecipe =
        RecipeMarkdownParser.parse(recipe("Test", head)).recipes.single()

    @Test
    fun schluesselzeilen_in_drei_schreibweisen() {
        val parsed = parseOne("- Portionen: 4\n**Küche:** italienisch\n* Tags: A\nAufwand: schnell\n**Saison**: winter")
        assertEquals("4", parsed.recipeYield)
        assertEquals("italienisch", parsed.cuisine)
        assertEquals(listOf("A"), parsed.tags)
        assertEquals("schnell", parsed.effort)
        assertEquals("winter", parsed.seasonFit)
        assertEquals("", parsed.description)
        val unknown = parseOne("Schnell: und lecker\n- Farbe: rot")
        assertEquals("Schnell: und lecker\n- Farbe: rot", unknown.description)
    }

    @Test
    fun dauern_werden_iso() {
        val parsed = parseOne("- Vorbereitung: 15 Min\n- Kochzeit: 1,5 h\n- Gesamtzeit: PT2H")
        assertEquals("PT15M", parsed.prepTime)
        assertEquals("PT1H30M", parsed.cookTime)
        assertEquals("PT2H", parsed.totalTime)
        assertEquals("PT1H30M", parseOne("- Garzeit: 1 Std 30 Min").cookTime)
    }

    @Test
    fun ungueltige_dauer_ergibt_warnung() {
        val parsed = parseOne("- Vorbereitung: bald\n- Portionen: viele")
        assertEquals("", parsed.prepTime)
        assertEquals("", parsed.recipeYield)
        assertEquals(
            listOf(MarkdownWarningCode.INVALID_DURATION, MarkdownWarningCode.INVALID_SERVINGS),
            parsed.warnings.map { it.code },
        )
        assertEquals("bald", parsed.warnings[0].detail)
    }

    @Test
    fun kueche_ausserhalb_des_vokabulars_wird_sonstige() {
        val parsed = parseOne("- Küche: Italienisch\n")
        assertEquals("italienisch", parsed.cuisine)
        val other = parseOne("- Küche: Marsianisch")
        assertEquals("sonstige", other.cuisine)
        assertTrue(other.warnings.isEmpty())
    }

    @Test
    fun mahlzeit_deutsch_wird_slot() {
        val expected = mapOf(
            "Frühstück" to MealSlots.BREAKFAST, "Mittagessen" to MealSlots.LUNCH, "Abendessen" to MealSlots.DINNER,
            "Abendbrot" to MealSlots.DINNER, "Snack" to MealSlots.SNACK,
        )
        expected.forEach { (word, slot) -> assertEquals(word, slot, parseOne("- Mahlzeit: $word").mealSlot) }
        val unknown = parseOne("- Mahlzeit: Brunch")
        assertEquals(MealSlots.OTHER, unknown.mealSlot)
        assertEquals(MarkdownWarningCode.UNKNOWN_VALUE, unknown.warnings.single().code)
    }

    @Test
    fun aufwand_eiweiss_saison_vokabular_und_synonyme() {
        val parsed = parseOne("- Schwierigkeit: einfach\n- Proteinquelle: Fisch\n- Saison: ganzes Jahr")
        assertEquals("schnell", parsed.effort)
        assertEquals("fisch", parsed.proteinType)
        assertEquals("ganzjährig", parsed.seasonFit)
        assertEquals("aufwendig", parseOne("- Aufwand: aufwändig").effort)
        val bad = parseOne("- Aufwand: hart\n- Eiweiß: Tofu\n- Saison: Herbst")
        assertEquals("", bad.effort)
        assertEquals("", bad.proteinType)
        assertEquals("", bad.seasonFit)
        assertEquals(3, bad.warnings.count { it.code == MarkdownWarningCode.UNKNOWN_VALUE })
    }

    @Test
    fun tags_komma_semikolon_ohne_dubletten() {
        val parsed = parseOne("- Tags: #Pasta, schnell; Pasta ,, #Vegetarisch")
        assertEquals(listOf("Pasta", "schnell", "Vegetarisch"), parsed.tags)
        val many = parseOne("- Tags: " + (1..30).joinToString(",") { "t$it" })
        assertEquals(20, many.tags.size)
    }

    @Test
    fun naehrwerte_vollstaendig_und_unvollstaendig() {
        val full = parseOne("- Nährwerte pro Portion: 520 kcal, 15,5 g Eiweiß, 18 g Fett, 72 g Kohlenhydrate")
        val n = requireNotNull(full.nutrition)
        assertEquals(520.0, n.kcal, 0.0)
        assertEquals(15.5, n.protein, 0.0)
        assertEquals(18.0, n.fat, 0.0)
        assertEquals(72.0, n.carbs, 0.0)
        val partial = parseOne("- Nährwerte: 520 kcal, 15 g Eiweiß")
        assertNull(partial.nutrition)
        assertEquals(MarkdownWarningCode.NUTRITION_INCOMPLETE, partial.warnings.single().code)
    }
}
