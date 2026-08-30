package com.helga.android.data.repository

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.model.DayNutrition
import com.helga.android.data.model.WeekplanNutrition
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeekplanRepository @Inject constructor(
    private val weekplanDao: WeekplanDao,
    private val recipeDao: RecipeDao,
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
) {

    fun observeDays(): Flow<List<WeekplanDayEntity>> = weekplanDao.observeDays()

    fun observeDaysBetween(startDate: String, endDate: String): Flow<List<WeekplanDayEntity>> =
        weekplanDao.observeDaysBetween(startDate, endDate)

    fun observeRecipesForDay(dayId: String): Flow<List<WeekplanRecipeEntity>> =
        weekplanDao.observeRecipesForDay(dayId)

    fun observeExtrasForDay(dayId: String): Flow<List<WeekplanExtraEntity>> =
        weekplanDao.observeExtrasForDay(dayId)

    suspend fun getOrCreateDay(planDate: String): WeekplanDayEntity {
        val existing = weekplanDao.findDayByDate(planDate)
        if (existing != null) return existing
        val day = WeekplanDayEntity(
            id = UUID.randomUUID().toString(),
            planDate = planDate,
            updatedAt = System.currentTimeMillis(),
            dirty = 1,
        )
        weekplanDao.upsertDay(day)
        return day
    }

    suspend fun updateNote(dayId: String, note: String) {
        weekplanDao.updateNote(dayId, note, System.currentTimeMillis())
    }

    suspend fun addRecipe(dayId: String, recipeId: String) {
        val maxPos = weekplanDao.maxRecipePosition(dayId) ?: -1
        val entry = WeekplanRecipeEntity(
            id = UUID.randomUUID().toString(),
            weekplanDayId = dayId,
            recipeId = recipeId,
            position = maxPos + 1,
            updatedAt = System.currentTimeMillis(),
            dirty = 1,
        )
        weekplanDao.upsertWeekplanRecipe(entry)
    }

    suspend fun removeRecipe(entry: WeekplanRecipeEntity) {
        weekplanDao.softDeleteWeekplanRecipe(entry.id, System.currentTimeMillis())
    }

    suspend fun addExtra(dayId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val maxPos = weekplanDao.maxExtraPosition(dayId) ?: -1
        val extra = WeekplanExtraEntity(
            id = UUID.randomUUID().toString(),
            weekplanDayId = dayId,
            itemText = trimmed,
            position = maxPos + 1,
            updatedAt = System.currentTimeMillis(),
            dirty = 1,
        )
        weekplanDao.upsertExtra(extra)
    }

    suspend fun removeExtra(extra: WeekplanExtraEntity) {
        weekplanDao.softDeleteExtra(extra.id, System.currentTimeMillis())
    }

    /** Entfernt alle Rezepte und Extras eines Tages, lässt den Tag selbst aber bestehen. */
    suspend fun clearDay(dayId: String) {
        val ts = System.currentTimeMillis()
        weekplanDao.recipesForDay(dayId).forEach { weekplanDao.softDeleteWeekplanRecipe(it.id, ts) }
        weekplanDao.extrasForDay(dayId).forEach { weekplanDao.softDeleteExtra(it.id, ts) }
    }

    suspend fun deleteDay(dayId: String) {
        clearDay(dayId)
        weekplanDao.softDeleteDay(dayId, System.currentTimeMillis())
    }

    /**
     * Markiert einen Tag als übersprungen (kein Kochen nötig) oder hebt das wieder auf.
     * Beim Aktivieren werden vorhandene Rezepte/Extras entfernt — der Aufrufer muss vorher
     * warnen, falls der Tag bereits belegt war.
     */
    suspend fun setSkipped(dayId: String, skipped: Boolean) {
        if (skipped) clearDay(dayId)
        weekplanDao.setSkipped(dayId, if (skipped) 1 else 0, System.currentTimeMillis())
    }

    suspend fun exportToShoppingList(dayIds: List<String>, shoppingListId: String, desiredServings: Int = 0) {
        dayIds.forEach { dayId ->
            weekplanDao.recipesForDay(dayId).forEach { entry ->
                val recipe = recipeDao.findById(entry.recipeId)
                val baseServings = recipe?.recipeYield?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 0
                val scale = if (desiredServings > 0 && baseServings > 0) desiredServings.toDouble() / baseServings else 1.0
                val ingredients = recipeDao.ingredientsByRecipeId(entry.recipeId)
                ingredients.filter { it.deleted == 0 }.forEach { ingredient ->
                    shoppingRepository.addOrMergeItem(
                        listId = shoppingListId,
                        name = ingredient.food,
                        quantity = ingredient.quantity * scale,
                        unit = ingredient.unit,
                        source = "weekplan",
                        recipeName = recipe?.name ?: "",
                    )
                }
            }
        }
    }

    suspend fun getWeekplanNutrition(startDate: String, endDate: String): WeekplanNutrition {
        val days = weekplanDao.getDaysBetween(startDate, endDate)
        val dayNutritions = mutableListOf<DayNutrition>()
        var totalKcal = 0.0
        var bestNutriScore = ""
        var totalRecipes = 0

        days.forEach { day ->
            val recipes = weekplanDao.recipesForDay(day.id)
            val dayRecipes = mutableListOf<String>()
            var dayTotalKcal = 0.0
            var dayBestScore = ""

            recipes.forEach { entry ->
                val recipe = recipeDao.findById(entry.recipeId)
                if (recipe != null && recipe.deleted == 0) {
                    dayRecipes.add(recipe.name)
                    val nutrition = recipeRepository.getRecipeNutrition(recipe.id)
                    dayTotalKcal += nutrition.kcalPerPortion
                    if (nutrition.nutriScore.isNotBlank()) {
                        if (dayBestScore.isEmpty() || nutrition.nutriScore < dayBestScore) {
                            dayBestScore = nutrition.nutriScore
                        }
                    }
                    totalRecipes++
                }
            }

            val avgDayKcal = if (recipes.isNotEmpty()) dayTotalKcal / recipes.size else 0.0
            dayNutritions.add(
                DayNutrition(
                    date = day.planDate,
                    recipeNames = dayRecipes,
                    avgKcal = avgDayKcal,
                    avgNutriScore = dayBestScore,
                    totalRecipes = recipes.size,
                )
            )
            totalKcal += avgDayKcal
        }

        val weekAvgKcal = if (days.isNotEmpty()) totalKcal / days.size else 0.0
        var weekBestScore = ""
        dayNutritions.forEach { day ->
            if (day.avgNutriScore.isNotBlank()) {
                if (weekBestScore.isEmpty() || day.avgNutriScore < weekBestScore) {
                    weekBestScore = day.avgNutriScore
                }
            }
        }

        return WeekplanNutrition(
            days = dayNutritions,
            weekAvgKcal = weekAvgKcal,
            weekAvgNutriScore = weekBestScore,
            totalRecipes = totalRecipes,
        )
    }
}
