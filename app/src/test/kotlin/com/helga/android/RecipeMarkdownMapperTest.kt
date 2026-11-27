package com.helga.android

import com.helga.android.data.model.MealSlots
import com.helga.android.data.util.NutritionPerPortion
import com.helga.android.data.util.ParsedIngredientLine
import com.helga.android.data.util.ParsedRecipe
import com.helga.android.data.util.RecipeMarkdownMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeMarkdownMapperTest {

    private fun counter(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private val base = ParsedRecipe(
        blockIndex = 1,
        name = "Suppe",
        description = "Warm",
        recipeYield = "4",
        prepTime = "PT15M",
        cookTime = "PT45M",
        totalTime = "PT1H",
        ingredients = listOf(
            ParsedIngredientLine(250.0, "g", "Linsen", ""),
            ParsedIngredientLine(0.0, "", "Salz", "nach Geschmack"),
        ),
        steps = listOf("Kochen.", "Servieren."),
        notes = "Mit Essig",
    )

    @Test
    fun ids_slug_positionen_zeitstempel() {
        val bundle = RecipeMarkdownMapper.toBundle(base, now = 1_000L, newId = counter())
        assertEquals("id-1", bundle.recipe.id)
        assertEquals("id-1", bundle.recipe.slug)
        assertEquals(1_000L, bundle.recipe.createdAt)
        assertEquals("", bundle.recipe.sourceUrl)
        assertEquals("Mit Essig", bundle.recipe.personalNotes)
        assertEquals("PT1H", bundle.recipe.totalTime)
        assertEquals(listOf(0, 1), bundle.ingredients.map { it.position })
        assertEquals(listOf(1, 2), bundle.instructions.map { it.position })
        assertEquals(listOf("id-1", "id-1"), bundle.ingredients.map { it.recipeId })
        assertEquals(listOf("id-1", "id-1"), bundle.instructions.map { it.recipeId })
        assertEquals("Linsen", bundle.ingredients[0].food)
        assertEquals("nach Geschmack", bundle.ingredients[1].note)
        assertEquals(listOf("Kochen.", "Servieren."), bundle.instructions.map { it.text })
        val ids = listOf(bundle.recipe.id) + bundle.ingredients.map { it.id } + bundle.instructions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun naehrwerte_mal_vier_mit_quelle_ai() {
        val parsed = base.copy(nutrition = NutritionPerPortion(380.0, 21.0, 6.5, 55.0))
        val recipe = RecipeMarkdownMapper.toBundle(parsed, 1L, counter()).recipe
        assertEquals(1520.0, recipe.nutritionKcal, 0.0)
        assertEquals(84.0, recipe.nutritionProtein, 0.0)
        assertEquals(26.0, recipe.nutritionFat, 0.0)
        assertEquals(220.0, recipe.nutritionCarbs, 0.0)
        assertEquals("ai", recipe.nutritionSource)
    }

    @Test
    fun ohne_naehrwerte_bleiben_felder_leer() {
        val recipe = RecipeMarkdownMapper.toBundle(base, 1L, counter()).recipe
        assertEquals(0.0, recipe.nutritionKcal, 0.0)
        assertEquals(0.0, recipe.nutritionProtein, 0.0)
        assertEquals(0.0, recipe.nutritionFat, 0.0)
        assertEquals(0.0, recipe.nutritionCarbs, 0.0)
        assertEquals("", recipe.nutritionSource)
    }

    @Test
    fun tags_und_mealslot_werden_uebernommen() {
        val parsed = base.copy(tags = listOf("Suppe", "Eintopf"), mealSlot = MealSlots.LUNCH, cuisine = "deutsch")
        val bundle = RecipeMarkdownMapper.toBundle(parsed, 1L, counter())
        assertEquals(listOf("Suppe", "Eintopf"), bundle.tags.map { it.name })
        assertEquals(listOf("id-1", "id-1"), bundle.tags.map { it.recipeId })
        assertEquals(MealSlots.LUNCH, bundle.recipe.mealSlot)
        assertEquals("deutsch", bundle.recipe.cuisine)
        val invalid = RecipeMarkdownMapper.toBundle(base.copy(mealSlot = "brunch"), 1L, counter()).recipe
        assertEquals(MealSlots.OTHER, invalid.mealSlot)
    }
}
