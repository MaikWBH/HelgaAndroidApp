package com.helga.android

import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.weekplan.candidatePool
import com.helga.android.data.weekplan.repeatCutoffDate
import com.helga.android.data.weekplan.selectAnchorDays
import com.helga.android.data.weekplan.sumFeedbackScores
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekplanGenerationCharacterizationTest {

    private fun day(id: String, locked: Int) = WeekplanDayEntity(id = id, planDate = "2026-11-1$id", isLocked = locked)
    private fun entry(dayId: String, recipeId: String) =
        WeekplanRecipeEntity(id = "$dayId-$recipeId", weekplanDayId = dayId, recipeId = recipeId)
    private fun recipe(id: String) = RecipeEntity(id = id, slug = id, name = id)
    private fun feedback(recipeId: String, liked: Int) =
        RecipeFeedbackEntity(id = "$recipeId-$liked", recipeId = recipeId, liked = liked)

    @Test
    fun anker_nur_gesperrte_tage_mit_vorhandenem_rezept() {
        val days = listOf(day("1", locked = 1), day("2", locked = 1), day("3", locked = 0), day("4", locked = 0))
        val existing = mapOf(
            "1" to listOf(entry("1", "r1")),
            "2" to emptyList(),
            "3" to listOf(entry("3", "r3")),
            "4" to emptyList(),
        )

        val anchors = selectAnchorDays(days, existing)

        assertEquals(setOf("1"), anchors.keys)
        assertEquals(listOf("r1"), anchors.getValue("1").map { it.recipeId })
    }

    @Test
    fun wiederholungssperre_stichtag_ist_heute_minus_max_tage() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals("2026-11-11", repeatCutoffDate(today, 0))
        assertEquals("2026-11-04", repeatCutoffDate(today, 7))
        assertEquals("2026-10-28", repeatCutoffDate(today, 14))
    }

    @Test
    fun feedback_summe_je_rezept() {
        val scores = sumFeedbackScores(
            listOf(
                feedback("a", 1), feedback("a", 1), feedback("a", -1),
                feedback("b", -1),
                feedback("c", 0),
            )
        )

        assertEquals(mapOf("a" to 1, "b" to -1, "c" to 0), scores)
    }

    @Test
    fun feedback_leer_ergibt_leere_map() {
        assertEquals(emptyMap<String, Int>(), sumFeedbackScores(emptyList()))
    }

    @Test
    fun kandidaten_ohne_kuerzlich_geplante() {
        val all = listOf(recipe("a"), recipe("b"), recipe("c"))

        assertEquals(listOf("b"), candidatePool(all, setOf("a", "c")).map { it.id })
        assertEquals(listOf("a", "b", "c"), candidatePool(all, emptySet()).map { it.id })
    }

    @Test
    fun kandidaten_fallback_auf_alle_wenn_alle_kuerzlich_geplant() {
        val all = listOf(recipe("a"), recipe("b"))

        assertEquals(listOf("a", "b"), candidatePool(all, setOf("a", "b")).map { it.id })
    }
}
