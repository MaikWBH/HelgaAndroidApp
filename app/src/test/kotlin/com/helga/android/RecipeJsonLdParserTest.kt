package com.helga.android

import com.helga.android.ui.ai.RecipeJsonLdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für RecipeJsonLdParser (ki A1) — deckt auch den Parser-Teil ab, der ursprünglich
 * doppelt unter rezepte A3 stand (Timer-Erkennung, siehe TimerDetectionTest, ist getrennt).
 */
class RecipeJsonLdParserTest {

    private fun html(jsonLd: String): String = """
        <html><head>
        <script type="application/ld+json">$jsonLd</script>
        </head><body></body></html>
    """.trimIndent()

    @Test
    fun `parses a full recipe JSON-LD block`() {
        val result = RecipeJsonLdParser.parse(
            html(
                """
                {
                    "name": "Spaghetti Carbonara",
                    "description": "Klassiker aus Rom",
                    "recipeYield": "4 Portionen",
                    "prepTime": "PT15M",
                    "cookTime": "PT10M",
                    "totalTime": "PT25M",
                    "cuisine": "Italienisch",
                    "recipeIngredient": ["400g Spaghetti", "200g Guanciale", "4 Eier"],
                    "recipeInstructions": [
                        {"text": "Wasser aufkochen"},
                        {"text": "Guanciale anbraten"}
                    ],
                    "keywords": "schnell, italienisch, pasta"
                }
                """.trimIndent()
            )
        )

        requireNotNull(result)
        assertEquals("Spaghetti Carbonara", result.name)
        assertEquals("Klassiker aus Rom", result.description)
        assertEquals("4 Portionen", result.recipeYield)
        assertEquals("PT15M", result.prepTime)
        assertEquals("Italienisch", result.cuisine)
        assertEquals(listOf("400g Spaghetti", "200g Guanciale", "4 Eier"), result.ingredients)
        assertEquals(listOf("Wasser aufkochen", "Guanciale anbraten"), result.instructions)
        assertEquals(listOf("schnell", "italienisch", "pasta"), result.tags)
    }

    @Test
    fun `recipeInstructions as plain strings instead of HowToStep objects`() {
        val result = RecipeJsonLdParser.parse(
            html("""{"name": "Test", "recipeInstructions": ["Schritt 1", "Schritt 2"]}""")
        )

        requireNotNull(result)
        assertEquals(listOf("Schritt 1", "Schritt 2"), result.instructions)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val result = RecipeJsonLdParser.parse(html("""{"name": "Minimal"}"""))

        requireNotNull(result)
        assertEquals("Minimal", result.name)
        assertEquals("", result.description)
        assertEquals("other", result.mealSlot)
        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.instructions.isEmpty())
        assertTrue(result.tags.isEmpty())
    }

    @Test
    fun `blank ingredient and instruction entries are filtered out`() {
        val result = RecipeJsonLdParser.parse(
            html("""{"name": "Test", "recipeIngredient": ["Mehl", "", "  "], "recipeInstructions": ["", "Rühren"]}""")
        )

        requireNotNull(result)
        assertEquals(listOf("Mehl"), result.ingredients)
        assertEquals(listOf("Rühren"), result.instructions)
    }

    @Test
    fun `cuisine falls back to rocks_cuisine when cuisine is absent`() {
        val result = RecipeJsonLdParser.parse(
            html("""{"name": "Test", "rocks_cuisine": "Asiatisch"}""")
        )

        requireNotNull(result)
        assertEquals("Asiatisch", result.cuisine)
    }

    @Test
    fun `html without a ld+json script returns null`() {
        val result = RecipeJsonLdParser.parse("<html><body>Kein Rezept hier</body></html>")
        assertNull(result)
    }

    @Test
    fun `malformed json inside the script tag returns null instead of throwing`() {
        // org.json ist tolerant gegenüber Kleinigkeiten wie einem trailing comma — hier bewusst
        // ein unbalancierter, gänzlich unparsbarer Block, um wirklich die catch-Klausel zu testen.
        val result = RecipeJsonLdParser.parse(html("""{"name": "kaputt" """))
        assertNull(result)
    }

    @Test
    fun `keywords with surrounding whitespace are trimmed into tags`() {
        val result = RecipeJsonLdParser.parse(
            html("""{"name": "Test", "keywords": " vegan , glutenfrei ,,"}""")
        )

        requireNotNull(result)
        assertEquals(listOf("vegan", "glutenfrei"), result.tags)
    }

    @Test
    fun `script tag attributes in any order are still matched`() {
        val htmlWithReorderedAttrs = """
            <script data-foo="bar" type="application/ld+json" id="x">{"name": "Reordered"}</script>
        """.trimIndent()
        val result = RecipeJsonLdParser.parse(htmlWithReorderedAttrs)

        requireNotNull(result)
        assertEquals("Reordered", result.name)
    }
}
