package com.helga.android.data.util

import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.model.MealSlots
import com.helga.android.data.model.NUTRITION_BASELINE_PORTIONS
import java.util.UUID

/** Ein importiertes Rezept mit allen zugehörigen Zeilen, bereit für `RecipeRepository.saveRecipe`. */
data class ImportedRecipeBundle(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val instructions: List<InstructionEntity>,
    val tags: List<TagEntity>,
)

/** Übersetzt ein geparstes Markdown-Rezept in Room-Entities (Phase 7). */
object RecipeMarkdownMapper {

    fun toBundle(
        parsed: ParsedRecipe,
        now: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): ImportedRecipeBundle {
        val id = newId()
        val nutrition = parsed.nutrition
        val recipe = RecipeEntity(
            id = id,
            slug = id,
            name = parsed.name,
            description = parsed.description,
            recipeYield = parsed.recipeYield,
            prepTime = parsed.prepTime,
            cookTime = parsed.cookTime,
            totalTime = parsed.totalTime,
            sourceUrl = "",
            proteinType = parsed.proteinType,
            effort = parsed.effort,
            cuisine = parsed.cuisine,
            mealSlot = if (parsed.mealSlot in MealSlots.ALL) parsed.mealSlot else MealSlots.OTHER,
            seasonFit = parsed.seasonFit,
            createdAt = now,
            updatedAt = now,
            dirty = 1,
            personalNotes = parsed.notes,
            nutritionKcal = nutrition?.let { it.kcal * NUTRITION_BASELINE_PORTIONS } ?: 0.0,
            nutritionProtein = nutrition?.let { it.protein * NUTRITION_BASELINE_PORTIONS } ?: 0.0,
            nutritionFat = nutrition?.let { it.fat * NUTRITION_BASELINE_PORTIONS } ?: 0.0,
            nutritionCarbs = nutrition?.let { it.carbs * NUTRITION_BASELINE_PORTIONS } ?: 0.0,
            nutritionSource = if (nutrition != null) "ai" else "",
        )
        val ingredients = parsed.ingredients.mapIndexed { index, line ->
            IngredientEntity(
                id = newId(),
                recipeId = id,
                position = index,
                quantity = line.quantity,
                unit = line.unit,
                food = line.food,
                note = line.note,
                updatedAt = now,
                dirty = 1,
            )
        }
        val instructions = parsed.steps.mapIndexed { index, text ->
            InstructionEntity(id = newId(), recipeId = id, position = index + 1, text = text, updatedAt = now, dirty = 1)
        }
        val tags = parsed.tags.map { TagEntity(id = newId(), recipeId = id, name = it, updatedAt = now, dirty = 1) }
        return ImportedRecipeBundle(recipe, ingredients, instructions, tags)
    }
}
