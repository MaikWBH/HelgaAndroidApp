package com.helga.android.data.repository

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.model.RecipeNutrition
import kotlinx.coroutines.flow.Flow

data class IngredientMapping(val ingredientName: String, val productName: String)

data class RecipeNutritionWithMappings(
    val nutrition: RecipeNutrition,
    val ingredientMappings: List<IngredientMapping>,
    val unmappedIngredients: List<String>,
)
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingRepository: ShoppingRepository,
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

    /**
     * Returns nutritional data for a recipe aggregated from OFF product mappings.
     * Returns zero values when no OFF product data is available locally.
     */
    suspend fun getRecipeNutrition(recipeId: String): RecipeNutrition {
        val recipe = recipeDao.findById(recipeId)
        val portions = recipe?.recipeYield?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 1
        val ingredients = recipeDao.ingredientsByRecipeId(recipeId).filter { it.deleted == 0 }
        return RecipeNutrition(
            totalKcal = 0.0,
            kcalPerPortion = 0.0,
            protein = 0.0,
            fat = 0.0,
            carbs = 0.0,
            nutriScore = "",
            matchedIngredientsCount = 0,
            totalIngredientsCount = ingredients.size,
        )
    }

    /**
     * Returns nutrition data with ingredient→product mappings.
     * Returns zero nutrition and all ingredients as unmapped until an
     * OffProduct DAO is wired up.
     */
    suspend fun getRecipeNutritionWithTopProducts(recipeId: String): RecipeNutritionWithMappings {
        val nutrition = getRecipeNutrition(recipeId)
        val ingredients = recipeDao.ingredientsByRecipeId(recipeId).filter { it.deleted == 0 }
        return RecipeNutritionWithMappings(
            nutrition = nutrition,
            ingredientMappings = emptyList(),
            unmappedIngredients = ingredients.map { it.food }.filter { it.isNotBlank() },
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
}
