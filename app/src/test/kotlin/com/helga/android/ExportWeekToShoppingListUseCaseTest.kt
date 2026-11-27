package com.helga.android

import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.WeekplanRepository
import com.helga.android.data.usecase.ExportWeekToShoppingListUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExportWeekToShoppingListUseCaseTest {

    private val weekplan: WeekplanRepository = mockk()
    private val recipes: RecipeRepository = mockk()
    private val shopping: ShoppingRepository = mockk(relaxed = true)
    private val useCase = ExportWeekToShoppingListUseCase(weekplan, recipes, shopping)

    @Test
    fun exportiert_nur_aktive_zutaten_zweier_rezepte() = runTest {
        val day = mockk<WeekplanDayEntity> { every { id } returns "d1" }
        val entryPasta = mockk<WeekplanRecipeEntity> { every { recipeId } returns "r1" }
        val entrySalat = mockk<WeekplanRecipeEntity> { every { recipeId } returns "r2" }
        val pasta = mockk<RecipeEntity> { every { name } returns "Pasta" }
        val salat = mockk<RecipeEntity> { every { name } returns "Salat" }

        coEvery { weekplan.daysBetween("2026-11-09", "2026-11-15") } returns listOf(day)
        coEvery { weekplan.recipesForDay("d1") } returns listOf(entryPasta, entrySalat)
        coEvery { recipes.findById("r1") } returns pasta
        coEvery { recipes.findById("r2") } returns salat
        coEvery { recipes.ingredientsForRecipe("r1") } returns listOf(
            IngredientEntity(id = "i1", recipeId = "r1", food = "Nudeln", quantity = 500.0, unit = "g"),
            IngredientEntity(id = "i2", recipeId = "r1", food = "Salz", deleted = 1),
        )
        coEvery { recipes.ingredientsForRecipe("r2") } returns listOf(
            IngredientEntity(id = "i3", recipeId = "r2", food = "Tomate", quantity = 2.0, unit = "Stk"),
        )

        useCase("L", "2026-11-09", "2026-11-15")

        coVerify(exactly = 1) {
            shopping.addOrMergeItem(
                listId = "L", name = "Nudeln", quantity = 500.0, unit = "g",
                source = "weekplan", recipeName = "Pasta",
            )
        }
        coVerify(exactly = 1) {
            shopping.addOrMergeItem(
                listId = "L", name = "Tomate", quantity = 2.0, unit = "Stk",
                source = "weekplan", recipeName = "Salat",
            )
        }
        coVerify(exactly = 0) { shopping.addOrMergeItem(any(), "Salz", any(), any(), any(), any()) }
    }
}
