package com.helga.android

import com.helga.android.data.model.MealSlots
import com.helga.android.data.util.RecipeMarkdownParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hält die KI-Vorlage (`res/raw/recipe_md_prompt.txt`) und den Parser synchron (Phase 7). */
class RecipePromptSyncTest {

    @Test
    fun beispiel_der_vorlage_parst_fehlerfrei() {
        val text = File("src/main/res/raw/recipe_md_prompt.txt").readText(Charsets.UTF_8)
        val example = text.substringAfter("=== BEISPIEL ANFANG ===").substringBefore("=== BEISPIEL ENDE ===")
        val result = RecipeMarkdownParser.parse(example)
        assertNull(result.fatal)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(2, result.recipes.size)
        result.recipes.forEach { recipe ->
            assertTrue(recipe.name, recipe.warnings.isEmpty())
            assertNotNull(recipe.name, recipe.nutrition)
            assertTrue(recipe.name, recipe.ingredients.size >= 3)
            assertTrue(recipe.name, recipe.steps.size >= 3)
        }
        assertEquals(listOf(MealSlots.DINNER, MealSlots.LUNCH), result.recipes.map { it.mealSlot })
        assertEquals("Mein Wunsch:", text.lines().last { it.isNotBlank() }.trim())
    }
}
