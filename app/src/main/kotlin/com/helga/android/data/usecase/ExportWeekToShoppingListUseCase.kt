package com.helga.android.data.usecase

import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.WeekplanRepository
import javax.inject.Inject

/**
 * Übernimmt die aktiven Zutaten aller Wochenplan-Rezepte im Zeitraum in eine Einkaufsliste
 * (Merge pro Artikel, gelöschte Zutaten werden übersprungen).
 */
class ExportWeekToShoppingListUseCase @Inject constructor(
    private val weekplanRepository: WeekplanRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
) {
    /** [weekStart] und [weekEnd] im Format `yyyy-MM-dd`, beide inklusiv. */
    suspend operator fun invoke(listId: String, weekStart: String, weekEnd: String) {
        weekplanRepository.daysBetween(weekStart, weekEnd).forEach { day ->
            weekplanRepository.recipesForDay(day.id).forEach { entry ->
                val recipeName = recipeRepository.findById(entry.recipeId)?.name ?: ""
                recipeRepository.ingredientsForRecipe(entry.recipeId)
                    .filter { it.deleted == 0 }
                    .forEach { ingredient ->
                        shoppingRepository.addOrMergeItem(
                            listId = listId,
                            name = ingredient.food,
                            quantity = ingredient.quantity,
                            unit = ingredient.unit,
                            source = "weekplan",
                            recipeName = recipeName,
                        )
                    }
            }
        }
    }
}
