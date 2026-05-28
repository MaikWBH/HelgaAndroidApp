package com.helga.android.data.repository

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeekplanRepository @Inject constructor(
    private val weekplanDao: WeekplanDao,
    private val recipeDao: RecipeDao,
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

    suspend fun deleteDay(dayId: String) {
        val ts = System.currentTimeMillis()
        weekplanDao.recipesForDay(dayId).forEach { weekplanDao.softDeleteWeekplanRecipe(it.id, ts) }
        weekplanDao.extrasForDay(dayId).forEach { weekplanDao.softDeleteExtra(it.id, ts) }
        weekplanDao.softDeleteDay(dayId, ts)
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
                    )
                }
            }
        }
    }
}
