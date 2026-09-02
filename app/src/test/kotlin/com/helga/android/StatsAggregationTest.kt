package com.helga.android

import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeHistoryEntity
import com.helga.android.ui.stats.MonthStats
import com.helga.android.ui.stats.aggregateMonthStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für die Aggregationslogik hinter dem Statistik-Screen (statistik A1). */
class StatsAggregationTest {

    private fun historyEntry(id: String, recipeId: String) =
        RecipeHistoryEntity(id = id, recipeId = recipeId, plannedDate = "2026-08-01", cooked = 1)

    private fun recipe(id: String, name: String, proteinType: String = "") =
        RecipeEntity(id = id, name = name, proteinType = proteinType)

    @Test
    fun `totalCooked counts every history entry, including repeats`() {
        val history = listOf(historyEntry("h1", "r1"), historyEntry("h2", "r1"), historyEntry("h3", "r2"))
        val result = aggregateMonthStats(history, emptyMap(), emptySet())
        assertEquals(3, result.totalCooked)
    }

    @Test
    fun `top recipes are sorted by frequency descending`() {
        val history = listOf(
            historyEntry("h1", "r1"), historyEntry("h2", "r1"), historyEntry("h3", "r1"),
            historyEntry("h4", "r2"), historyEntry("h5", "r2"),
            historyEntry("h6", "r3"),
        )
        val recipes = mapOf(
            "r1" to recipe("r1", "Lasagne"),
            "r2" to recipe("r2", "Curry"),
            "r3" to recipe("r3", "Salat"),
        )
        val result = aggregateMonthStats(history, recipes, emptySet())
        assertEquals(listOf("Lasagne" to 3, "Curry" to 2, "Salat" to 1), result.topRecipes)
    }

    @Test
    fun `top recipes are capped at five entries`() {
        val history = (1..6).map { historyEntry("h$it", "r$it") }
        val recipes = (1..6).associate { "r$it" to recipe("r$it", "Rezept $it") }
        val result = aggregateMonthStats(history, recipes, emptySet())
        assertEquals(5, result.topRecipes.size)
    }

    @Test
    fun `history entries pointing to a deleted recipe are skipped in topRecipes`() {
        val history = listOf(historyEntry("h1", "ghost"))
        val result = aggregateMonthStats(history, emptyMap(), emptySet())
        assertTrue(result.topRecipes.isEmpty())
    }

    @Test
    fun `protein types are counted by their exact German label`() {
        val history = listOf(
            historyEntry("h1", "meat"), historyEntry("h2", "fish"),
            historyEntry("h3", "veg1"), historyEntry("h4", "veg2"), historyEntry("h5", "other"),
        )
        val recipes = mapOf(
            "meat" to recipe("meat", "Braten", proteinType = "Fleisch"),
            "fish" to recipe("fish", "Lachs", proteinType = "fisch"),
            "veg1" to recipe("veg1", "Salat", proteinType = "vegetarisch"),
            "veg2" to recipe("veg2", "Tofu", proteinType = "vegan"),
            "other" to recipe("other", "Pancakes", proteinType = "süß"),
        )
        val result = aggregateMonthStats(history, recipes, emptySet())
        assertEquals(1, result.meatCount)
        assertEquals(1, result.fishCount)
        assertEquals(2, result.vegCount)
        assertEquals(1, result.otherCount)
    }

    @Test
    fun `english protein-type labels are not recognized here and count as other`() {
        // Anders als computeWeekBalance im Wochenplan (dort auch "meat"/"poultry"/... erkannt) —
        // hier nur die exakten deutschen Labels, siehe StatsViewModel.aggregateMonthStats.
        val history = listOf(historyEntry("h1", "r1"))
        val recipes = mapOf("r1" to recipe("r1", "Steak", proteinType = "meat"))
        val result = aggregateMonthStats(history, recipes, emptySet())
        assertEquals(0, result.meatCount)
        assertEquals(1, result.otherCount)
    }

    @Test
    fun `first-timers are recipes cooked in this period but never before`() {
        val history = listOf(historyEntry("h1", "new1"), historyEntry("h2", "returning"))
        val recipes = mapOf("new1" to recipe("new1", "Neu"), "returning" to recipe("returning", "Bekannt"))
        val result = aggregateMonthStats(history, recipes, previousRecipeIds = setOf("returning"))
        assertEquals(listOf("Neu"), result.firstTimers)
    }

    @Test
    fun `a recipe cooked multiple times this period appears only once in first-timers`() {
        val history = listOf(historyEntry("h1", "new1"), historyEntry("h2", "new1"))
        val recipes = mapOf("new1" to recipe("new1", "Neu"))
        val result = aggregateMonthStats(history, recipes, emptySet())
        assertEquals(listOf("Neu"), result.firstTimers)
    }

    @Test
    fun `first-timers are capped at five`() {
        val history = (1..6).map { historyEntry("h$it", "r$it") }
        val recipes = (1..6).associate { "r$it" to recipe("r$it", "Rezept $it") }
        val result = aggregateMonthStats(history, recipes, emptySet())
        assertEquals(5, result.firstTimers.size)
    }

    @Test
    fun `empty history yields an all-zero MonthStats`() {
        assertEquals(MonthStats(), aggregateMonthStats(emptyList(), emptyMap(), emptySet()))
    }
}
