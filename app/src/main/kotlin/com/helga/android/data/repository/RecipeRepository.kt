package com.helga.android.data.repository

import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.model.RecipeNutrition
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingRepository: ShoppingRepository,
    private val offProductDao: OffProductDao,
) {
    fun observeAll(): Flow<List<RecipeEntity>> = recipeDao.observeAll()
    fun observeById(id: String): Flow<RecipeEntity?> = recipeDao.observeById(id)
    fun observeIngredients(recipeId: String): Flow<List<IngredientEntity>> = recipeDao.observeIngredients(recipeId)
    fun observeInstructions(recipeId: String): Flow<List<InstructionEntity>> = recipeDao.observeInstructions(recipeId)
    fun observeTags(recipeId: String): Flow<List<TagEntity>> = recipeDao.observeTags(recipeId)
    fun observeAllTagNames(): Flow<List<String>> = recipeDao.observeAllTagNames()
    fun observeRecipeIdsByTag(tag: String): Flow<List<String>> = recipeDao.observeRecipeIdsByTag(tag)
    fun observeRecipeIdsByTags(tags: List<String>): Flow<List<String>> = recipeDao.observeRecipeIdsByTags(tags)

    suspend fun findById(id: String): RecipeEntity? = recipeDao.findById(id)
    suspend fun allRecipes(): List<RecipeEntity> = recipeDao.allActive()
    suspend fun ingredientsForRecipe(id: String): List<IngredientEntity> = recipeDao.ingredientsByRecipeId(id)
    suspend fun instructionsForRecipe(id: String): List<InstructionEntity> = recipeDao.instructionsByRecipeId(id)
    suspend fun tagsByRecipeId(id: String): List<TagEntity> = recipeDao.tagsByRecipeId(id)

    /** Schreibt einen Datensatz lokal mit `dirty = 1`, damit er beim nächsten Sync hochgeht. */
    suspend fun upsertLocal(recipe: RecipeEntity) {
        recipeDao.upsertRecipe(
            recipe.copy(updatedAt = System.currentTimeMillis(), dirty = 1)
        )
    }

    suspend fun updateRating(id: String, rating: Int) {
        recipeDao.updateRating(id, rating, System.currentTimeMillis())
    }

    suspend fun updatePersonalNotes(id: String, notes: String) {
        recipeDao.updatePersonalNotes(id, notes, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(recipe: RecipeEntity) {
        val newValue = if (recipe.isFavorite == 0) 1 else 0
        recipeDao.updateFavorite(recipe.id, newValue, System.currentTimeMillis())
    }

    suspend fun softDelete(recipe: RecipeEntity) {
        recipeDao.upsertRecipe(
            recipe.copy(deleted = 1, updatedAt = System.currentTimeMillis(), dirty = 1)
        )
    }

    /**
     * Speichert ein Rezept mit allen Kind-Entitäten. Items, die in der DB existieren
     * aber nicht in der neuen Liste sind, werden soft-deleted.
     */
    suspend fun saveRecipe(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        instructions: List<InstructionEntity>,
        tags: List<TagEntity>,
    ) {
        val now = System.currentTimeMillis()
        val id = recipe.id

        val newIngredientIds = ingredients.map { it.id }.toSet()
        val newInstructionIds = instructions.map { it.id }.toSet()
        val newTagIds = tags.map { it.id }.toSet()

        val removedIngredients = recipeDao.ingredientsByRecipeId(id)
            .filter { it.id !in newIngredientIds }
            .map { it.copy(deleted = 1, updatedAt = now, dirty = 1) }
        val removedInstructions = recipeDao.instructionsByRecipeId(id)
            .filter { it.id !in newInstructionIds }
            .map { it.copy(deleted = 1, updatedAt = now, dirty = 1) }
        val removedTags = recipeDao.tagsByRecipeId(id)
            .filter { it.id !in newTagIds }
            .map { it.copy(deleted = 1, updatedAt = now, dirty = 1) }

        if (removedIngredients.isNotEmpty()) recipeDao.upsertIngredients(removedIngredients)
        if (removedInstructions.isNotEmpty()) recipeDao.upsertInstructions(removedInstructions)
        if (removedTags.isNotEmpty()) recipeDao.upsertTags(removedTags)

        recipeDao.upsertRecipeBundle(
            recipe = recipe.copy(updatedAt = now, dirty = 1),
            ingredients = ingredients.map { it.copy(updatedAt = now, dirty = 1) },
            instructions = instructions.map { it.copy(updatedAt = now, dirty = 1) },
            tags = tags.map { it.copy(updatedAt = now, dirty = 1) },
            categories = emptyList<CategoryEntity>(),
        )
    }

    suspend fun exportToShoppingList(recipeId: String, listId: String) {
        val recipeName = recipeDao.findById(recipeId)?.name ?: ""
        val ingredients = recipeDao.ingredientsByRecipeId(recipeId).filter { it.deleted == 0 }
        ingredients.forEach { ingredient ->
            shoppingRepository.addOrMergeItem(
                listId = listId,
                name = ingredient.food,
                quantity = ingredient.quantity,
                unit = ingredient.unit,
                source = "recipe",
                recipeName = recipeName,
            )
        }
    }

    suspend fun getRecipeNutrition(recipeId: String): RecipeNutrition {
        val recipe = findById(recipeId)
        if (recipe == null) {
            return RecipeNutrition(0.0, 0.0, 0.0, 0.0, 0.0, "", 0, 0)
        }

        val ingredients = ingredientsForRecipe(recipeId).filter { it.deleted == 0 }
        if (ingredients.isEmpty()) {
            return RecipeNutrition(0.0, 0.0, 0.0, 0.0, 0.0, "", 0, 0)
        }

        var totalKcal = 0.0
        var totalProtein = 0.0
        var totalFat = 0.0
        var totalCarbs = 0.0
        var bestNutriScore = ""
        var matchedCount = 0

        ingredients.forEach { ingredient ->
            val product = if (ingredient.offBarcode.isNotBlank()) {
                offProductDao.getByBarcode(ingredient.offBarcode)
            } else {
                null
            }

            if (product != null) {
                matchedCount++
                val quantityGrams = convertToGrams(ingredient.quantity, ingredient.unit)
                if (quantityGrams > 0) {
                    totalKcal += (product.kcalPerUnit / 100.0) * quantityGrams
                    totalProtein += (product.proteins / 100.0) * quantityGrams
                    totalFat += (product.fats / 100.0) * quantityGrams
                    totalCarbs += (product.carbs / 100.0) * quantityGrams

                    if (product.nutriScore.isNotBlank()) {
                        if (bestNutriScore.isEmpty() || product.nutriScore < bestNutriScore) {
                            bestNutriScore = product.nutriScore
                        }
                    }
                }
            }
        }

        val portionCount = parsePortions(recipe.recipeYield)
        val kcalPerPortion = if (portionCount > 0) totalKcal / portionCount else totalKcal

        return RecipeNutrition(
            totalKcal = totalKcal,
            kcalPerPortion = kcalPerPortion,
            protein = totalProtein,
            fat = totalFat,
            carbs = totalCarbs,
            nutriScore = bestNutriScore,
            matchedIngredientsCount = matchedCount,
            totalIngredientsCount = ingredients.size,
        )
    }

    private fun convertToGrams(quantity: Double, unit: String): Double {
        return when (unit.lowercase()) {
            "g", "gramm" -> quantity
            "kg" -> quantity * 1000
            "ml", "l" -> quantity // Approximate: 1ml ≈ 1g for water-based
            "el", "esslöffel" -> quantity * 15
            "tl", "teelöffel" -> quantity * 5
            "tasse", "cup" -> quantity * 240
            "stück", "piece" -> quantity * 50 // Generic guess
            else -> quantity // Fallback
        }
    }

    private fun parsePortions(recipeYield: String): Int {
        return recipeYield.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
    }
}
