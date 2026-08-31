package com.helga.android.data.repository

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.model.NUTRITION_BASELINE_PORTIONS
import com.helga.android.data.model.RecipeNutrition
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeFeedbackDao: RecipeFeedbackDao,
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
    fun observeRecipeIdsByTagOrIngredientSearch(query: String): Flow<List<String>> =
        recipeDao.observeRecipeIdsByTagOrIngredientSearch(query)

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

    /**
     * Leitet die Sternebewertung aus dem Kochfeedback ab (rezepte A6) statt sie manuell zu
     * setzen — "Sterne am Rezept" und "Daumen je Kochtermin" waren zwei getrennte
     * Bewertungswege ohne erkennbare Rollenteilung. Nach jedem [RecipeFeedbackEntity]-Eintrag
     * aufrufen (egal ob aus der Kochansicht oder vom Wochenplan-Tageskärtchen). Gibt es noch
     * kein Feedback, bleibt ein eventuell vorhandener alter manueller Wert unangetastet — kein
     * rückwirkendes Zurücksetzen bereits bewerteter Rezepte auf "unbewertet".
     */
    suspend fun recalculateRating(recipeId: String) {
        val feedback = recipeFeedbackDao.feedbackForRecipe(recipeId).filter { it.liked != 0 }
        if (feedback.isEmpty()) return
        val avg = feedback.map { it.liked }.average()
        val rating = (3 + avg * 2).roundToInt().coerceIn(1, 5)
        recipeDao.updateRating(recipeId, rating, System.currentTimeMillis())
    }

    suspend fun updatePersonalNotes(id: String, notes: String) {
        recipeDao.updatePersonalNotes(id, notes, System.currentTimeMillis())
    }

    suspend fun updateLastServings(id: String, servings: Int) {
        recipeDao.updateLastServings(id, servings, System.currentTimeMillis())
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
     * Liest die am Rezept hinterlegten Nährwerte (manuell oder per KI ermittelt,
     * siehe [RecipeEntity.nutritionSource]). Werte gelten immer für
     * [NUTRITION_BASELINE_PORTIONS] Portionen, unabhängig von der im UI
     * angezeigten Portionenanzahl.
     */
    suspend fun getRecipeNutrition(recipeId: String): RecipeNutrition {
        val recipe = recipeDao.findById(recipeId)
        val totalKcal = recipe?.nutritionKcal ?: 0.0
        val protein = recipe?.nutritionProtein ?: 0.0
        val fat = recipe?.nutritionFat ?: 0.0
        val carbs = recipe?.nutritionCarbs ?: 0.0
        return RecipeNutrition(
            totalKcal = totalKcal,
            kcalPerPortion = totalKcal / NUTRITION_BASELINE_PORTIONS,
            protein = protein,
            fat = fat,
            carbs = carbs,
            proteinPerPortion = protein / NUTRITION_BASELINE_PORTIONS,
            fatPerPortion = fat / NUTRITION_BASELINE_PORTIONS,
            carbsPerPortion = carbs / NUTRITION_BASELINE_PORTIONS,
            source = recipe?.nutritionSource ?: "",
        )
    }

    /** Speichert Nährwerte für [NUTRITION_BASELINE_PORTIONS] Portionen, manuell oder per KI ermittelt. */
    suspend fun saveNutrition(
        recipeId: String,
        kcal: Double,
        protein: Double,
        fat: Double,
        carbs: Double,
        source: String,
    ) {
        val recipe = recipeDao.findById(recipeId) ?: return
        recipeDao.upsertRecipe(
            recipe.copy(
                nutritionKcal = kcal,
                nutritionProtein = protein,
                nutritionFat = fat,
                nutritionCarbs = carbs,
                nutritionSource = source,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
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
