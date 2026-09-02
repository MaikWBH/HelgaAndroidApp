package com.helga.android

import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.ui.weekplan.WeekBalance
import com.helga.android.ui.weekplan.computeWeekBalance
import com.helga.android.ui.weekplan.filterCandidateRecipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests für Constraint-Auswertung (`filterCandidateRecipes`) und `weekBalance`
 * (`computeWeekBalance`) — wochenplan A5.
 */
class WeekplanFiltersTest {

    private fun recipe(
        id: String,
        mealSlot: String = "dinner",
        seasonFit: String = "",
        nutritionKcal: Double = 0.0,
        proteinType: String = "",
    ) = RecipeEntity(
        id = id,
        mealSlot = mealSlot,
        seasonFit = seasonFit,
        nutritionKcal = nutritionKcal,
        proteinType = proteinType,
    )

    private fun weekplanRecipe(id: String, dayId: String, recipeId: String) =
        WeekplanRecipeEntity(id = id, weekplanDayId = dayId, recipeId = recipeId)

    // ── filterCandidateRecipes: mealSlot ────────────────────────────────────────

    @Test
    fun `keeps only lunch and dinner recipes`() {
        val candidates = listOf(
            recipe("breakfast1", mealSlot = "breakfast"),
            recipe("lunch1", mealSlot = "lunch"),
            recipe("dinner1", mealSlot = "dinner"),
            recipe("snack1", mealSlot = "snack"),
        )
        val result = filterCandidateRecipes(candidates, WeekplanConstraintsEntity(), emptyMap())
        assertEquals(setOf("lunch1", "dinner1"), result.map { it.id }.toSet())
    }

    @Test
    fun `falls back to all candidates and warns when no lunch or dinner recipes exist`() {
        val candidates = listOf(recipe("b1", mealSlot = "breakfast"), recipe("s1", mealSlot = "snack"))
        val warnings = mutableListOf<String>()
        val result = filterCandidateRecipes(candidates, WeekplanConstraintsEntity(), emptyMap(), warnings)
        assertEquals(2, result.size)
        assertTrue(warnings.any { it.contains("Hauptgericht") })
    }

    // ── filterCandidateRecipes: Allergene ───────────────────────────────────────

    @Test
    fun `excludes recipes whose ingredients contain an excluded allergen`() {
        val candidates = listOf(recipe("safe"), recipe("nutty"))
        val constraints = WeekplanConstraintsEntity(excludeAllergens = """["Nuss"]""")
        val ingredients = mapOf("nutty" to listOf("Erdnuss", "Zucker"), "safe" to listOf("Mehl"))
        val result = filterCandidateRecipes(candidates, constraints, ingredients)
        assertEquals(listOf("safe"), result.map { it.id })
    }

    @Test
    fun `falls back and warns when the allergen filter would remove every candidate`() {
        val candidates = listOf(recipe("nutty1"), recipe("nutty2"))
        val constraints = WeekplanConstraintsEntity(excludeAllergens = """["Nuss"]""")
        val ingredients = mapOf("nutty1" to listOf("Nuss"), "nutty2" to listOf("Nuss"))
        val warnings = mutableListOf<String>()
        val result = filterCandidateRecipes(candidates, constraints, ingredients, warnings)
        assertEquals(2, result.size)
        assertTrue(warnings.any { it.contains("Allergen") })
    }

    @Test
    fun `no allergen filter is applied when none are excluded`() {
        val candidates = listOf(recipe("nutty"))
        val ingredients = mapOf("nutty" to listOf("Nuss"))
        val result = filterCandidateRecipes(candidates, WeekplanConstraintsEntity(), ingredients)
        assertEquals(listOf("nutty"), result.map { it.id })
    }

    // ── filterCandidateRecipes: Kcal-Budget ─────────────────────────────────────

    @Test
    fun `excludes recipes over the kcal budget but keeps unrated recipes`() {
        val candidates = listOf(
            recipe("cheap", nutritionKcal = 400.0),
            recipe("expensive", nutritionKcal = 900.0),
            recipe("unrated", nutritionKcal = 0.0),
        )
        val constraints = WeekplanConstraintsEntity(maxKcalPerPortion = 700)
        val result = filterCandidateRecipes(candidates, constraints, emptyMap())
        assertEquals(setOf("cheap", "unrated"), result.map { it.id }.toSet())
    }

    // ── filterCandidateRecipes: Saison ──────────────────────────────────────────

    @Test
    fun `season filter keeps only current-season and year-round recipes`() {
        val summer = LocalDate.of(2026, 7, 15)
        val candidates = listOf(
            recipe("summerDish", seasonFit = "sommer"),
            recipe("winterDish", seasonFit = "winter"),
            recipe("anytime", seasonFit = "ganzjährig"),
            recipe("unrated", seasonFit = ""),
        )
        val result = filterCandidateRecipes(candidates, WeekplanConstraintsEntity(), emptyMap(), today = summer)
        assertEquals(setOf("summerDish", "anytime", "unrated"), result.map { it.id }.toSet())
    }

    @Test
    fun `falls back to the previous stage when the season filter would empty the pool`() {
        val summer = LocalDate.of(2026, 7, 15)
        val candidates = listOf(recipe("winterOnly", seasonFit = "winter"))
        val result = filterCandidateRecipes(candidates, WeekplanConstraintsEntity(), emptyMap(), today = summer)
        assertEquals(listOf("winterOnly"), result.map { it.id })
    }

    // ── computeWeekBalance ───────────────────────────────────────────────────────

    @Test
    fun `counts protein types across all days of the week`() {
        val recipes = mapOf(
            "meat1" to recipe("meat1", proteinType = "Fleisch"),
            "fish1" to recipe("fish1", proteinType = "Fisch"),
            "veg1" to recipe("veg1", proteinType = "Vegetarisch"),
            "other1" to recipe("other1", proteinType = ""),
        )
        val recipesByDay = mapOf(
            "mon" to listOf(weekplanRecipe("wr1", "mon", "meat1"), weekplanRecipe("wr2", "mon", "veg1")),
            "tue" to listOf(weekplanRecipe("wr3", "tue", "fish1"), weekplanRecipe("wr4", "tue", "other1")),
        )
        val result = computeWeekBalance(recipesByDay, recipes)
        assertEquals(WeekBalance(meat = 1, fish = 1, veg = 1, other = 1), result)
    }

    @Test
    fun `an entry pointing to a missing recipe counts as other`() {
        val recipesByDay = mapOf("mon" to listOf(weekplanRecipe("wr1", "mon", "ghost")))
        val result = computeWeekBalance(recipesByDay, emptyMap())
        assertEquals(WeekBalance(other = 1), result)
    }

    @Test
    fun `recognizes english and german protein-type synonyms case-insensitively`() {
        val recipes = mapOf(
            "r1" to recipe("r1", proteinType = "POULTRY"),
            "r2" to recipe("r2", proteinType = "seafood"),
            "r3" to recipe("r3", proteinType = "Vegan"),
        )
        val recipesByDay = mapOf(
            "mon" to listOf(
                weekplanRecipe("wr1", "mon", "r1"),
                weekplanRecipe("wr2", "mon", "r2"),
                weekplanRecipe("wr3", "mon", "r3"),
            )
        )
        val result = computeWeekBalance(recipesByDay, recipes)
        assertEquals(WeekBalance(meat = 1, fish = 1, veg = 1, other = 0), result)
    }

    @Test
    fun `empty week yields an all-zero balance`() {
        assertEquals(WeekBalance(), computeWeekBalance(emptyMap(), emptyMap()))
    }
}
