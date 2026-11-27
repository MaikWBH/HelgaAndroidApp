package com.helga.android

import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.WeekplanRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekplanHasRecipesTest {

    private val dao: WeekplanDao = mockk()
    private val repo = WeekplanRepository(
        weekplanDao = dao,
        weekplanSettingsDao = mockk(relaxed = true),
        weekplanConstraintsDao = mockk(relaxed = true),
        recipeDao = mockk(relaxed = true),
        recipeRepository = mockk<RecipeRepository>(relaxed = true),
        shoppingRepository = mockk<ShoppingRepository>(relaxed = true),
    )

    private fun day(id: String) = mockk<WeekplanDayEntity> { every { this@mockk.id } returns id }

    @Test
    fun keine_tage_ergibt_false() = runTest {
        every { dao.observeDaysBetween("a", "b") } returns flowOf(emptyList())

        assertEquals(false, repo.observeHasRecipesBetween("a", "b").first())
    }

    @Test
    fun tage_ohne_rezepte_ergeben_false() = runTest {
        every { dao.observeDaysBetween("a", "b") } returns flowOf(listOf(day("d1"), day("d2")))
        every { dao.observeRecipeCount("d1") } returns flowOf(0)
        every { dao.observeRecipeCount("d2") } returns flowOf(0)

        assertEquals(false, repo.observeHasRecipesBetween("a", "b").first())
    }

    @Test
    fun ein_tag_mit_rezept_ergibt_true() = runTest {
        every { dao.observeDaysBetween("a", "b") } returns flowOf(listOf(day("d1"), day("d2")))
        every { dao.observeRecipeCount("d1") } returns flowOf(0)
        every { dao.observeRecipeCount("d2") } returns flowOf(3)

        assertEquals(true, repo.observeHasRecipesBetween("a", "b").first())
    }
}
