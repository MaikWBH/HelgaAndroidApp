package com.helga.android.data.repository

import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.ReceiptArticleLinkDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.ReceiptArticleLinkEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.model.RecipeNutrition
import com.helga.android.data.util.IngredientNormalizer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class IngredientMapping(val ingredientName: String, val productName: String)

data class RecipeNutritionWithMappings(
    val nutrition: RecipeNutrition,
    val ingredientMappings: List<IngredientMapping>,
    val unmappedIngredients: List<String>,
)

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingRepository: ShoppingRepository,
    private val receiptArticleLinkDao: ReceiptArticleLinkDao,
    private val offProductDao: OffProductDao,
    private val receiptRepository: ReceiptRepository,
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
     * Aggregiert die Nährwerte eines Rezepts aus den in Phase 1 bestätigten
     * Bon-Artikel-Verknüpfungen ([ReceiptArticleLinkEntity]) und den dazugehörigen
     * OFF-Produkten. Zutaten ohne auflösbare Verknüpfung bleiben ungezählt.
     */
    suspend fun getRecipeNutrition(recipeId: String): RecipeNutrition =
        computeRecipeNutrition(recipeId).nutrition

    /**
     * Wie [getRecipeNutrition], liefert zusätzlich die konkreten
     * Zutat→Produkt-Zuordnungen und die unverknüpften Zutaten für die UI.
     */
    suspend fun getRecipeNutritionWithTopProducts(recipeId: String): RecipeNutritionWithMappings =
        computeRecipeNutrition(recipeId)

    /**
     * Kern-Berechnung: matched jede Zutat per [IngredientNormalizer] gegen die
     * bestätigten Verknüpfungen (erst exakt, dann Contains-Kaskade mit
     * Kaufhäufigkeit als Tie-Break), skaliert die OFF-Werte (pro 100 g) nur bei
     * erkennbaren Gewichts-/Volumeneinheiten auf die Zutatenmenge und sammelt
     * die Zuordnungen für die UI. Andere Einheiten (EL, Stück …) gelten als
     * "gematcht", tragen aber bewusst nichts zur Gramm-Summe bei (kein Raten).
     */
    private suspend fun computeRecipeNutrition(recipeId: String): RecipeNutritionWithMappings {
        val recipe = recipeDao.findById(recipeId)
        val portions = recipe?.recipeYield?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 1
        val ingredients = recipeDao.ingredientsByRecipeId(recipeId).filter { it.deleted == 0 }

        val confirmedLinks = receiptArticleLinkDao.allActive()
            .filter { it.confirmed == 1 && it.normalizedName.isNotBlank() }
        val productsById = offProductDao.allActive().associateBy { it.id }
        // Kaufhäufigkeit pro normalisiertem Bon-Schlüssel (Phase-1-Logik wiederverwendet).
        val buyCounts = receiptRepository.productSummaries().associate { it.normalizedKey to it.buyCount }

        var totalKcal = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0
        var matched = 0
        var bestNutriScore = ""
        val mappings = mutableListOf<IngredientMapping>()
        val unmapped = mutableListOf<String>()

        for (ingredient in ingredients) {
            val foodName = ingredient.food
            val key = IngredientNormalizer.normalize(foodName)
            val link = if (key.isBlank()) null else findLinkFor(key, confirmedLinks, buyCounts)
            val product = link?.let { productsById[it.offProductId] }

            if (product == null) {
                if (foodName.isNotBlank()) unmapped += foodName
                continue
            }

            matched++
            mappings += IngredientMapping(
                ingredientName = foodName,
                productName = product.name.ifBlank { link.displayName.ifBlank { foodName } },
            )
            bestNutriScore = betterNutriScore(bestNutriScore, product.nutriScore)

            val grams = unitToGrams(ingredient.unit)?.let { it * ingredient.quantity }
            if (grams != null && grams > 0.0) {
                val factor = grams / 100.0
                totalKcal += factor * product.kcalPerUnit
                protein += factor * product.proteins
                fat += factor * product.fats
                carbs += factor * product.carbs
            }
        }

        val nutrition = RecipeNutrition(
            totalKcal = totalKcal,
            kcalPerPortion = if (portions > 0) totalKcal / portions else totalKcal,
            protein = protein,
            fat = fat,
            carbs = carbs,
            nutriScore = bestNutriScore,
            matchedIngredientsCount = matched,
            totalIngredientsCount = ingredients.size,
        )
        return RecipeNutritionWithMappings(
            nutrition = nutrition,
            ingredientMappings = mappings,
            unmappedIngredients = unmapped,
        )
    }

    /**
     * Matching-Kaskade: zuerst exakte Übereinstimmung, sonst Contains-Match
     * (Zutatenname im Artikelnamen oder umgekehrt). Bei mehreren Kandidaten
     * gewinnt der mit der höchsten Kaufhäufigkeit.
     */
    private fun findLinkFor(
        key: String,
        links: List<ReceiptArticleLinkEntity>,
        buyCounts: Map<String, Int>,
    ): ReceiptArticleLinkEntity? {
        links.firstOrNull { it.normalizedName == key }?.let { return it }
        return links
            .filter { it.normalizedName.contains(key) || key.contains(it.normalizedName) }
            .maxByOrNull { buyCounts[it.normalizedName] ?: 0 }
    }

    /** Behält den besseren (= alphabetisch kleineren) Nutri-Score "a".."e". */
    private fun betterNutriScore(current: String, candidate: String): String {
        val c = candidate.trim().lowercase().takeIf { it.length == 1 && it[0] in 'a'..'e' } ?: return current
        return if (current.isBlank() || c < current) c else current
    }

    /**
     * Umrechnung erkennbarer Gewichts-/Volumeneinheiten auf Gramm (Flüssigkeiten
     * mit Dichte 1). Spiegelt die Allowlist des Server-Packungsgrößen-Parsers.
     * `null` für unbekannte/Stück-Einheiten → kein Beitrag zur Gramm-Summe.
     */
    private fun unitToGrams(unit: String): Double? = when (unit.trim().lowercase()) {
        "g", "gr", "gramm", "gram" -> 1.0
        "kg", "kilogramm" -> 1000.0
        "ml" -> 1.0
        "cl" -> 10.0
        "l", "liter" -> 1000.0
        else -> null
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
