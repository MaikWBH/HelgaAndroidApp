package com.helga.android.data.repository

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanDayMarkerAssignmentEntity
import com.helga.android.data.local.entity.WeekplanDayMarkerEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.model.DayNutrition
import com.helga.android.data.model.WeekplanExportItem
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

    /** Mahlzeiten-Tag je Eintrag setzen/löschen (wochenplan A14) — leerer String = kein Slot. */
    suspend fun setMealSlot(entry: WeekplanRecipeEntity, mealSlot: String) {
        weekplanDao.upsertWeekplanRecipe(
            entry.copy(mealSlot = mealSlot, updatedAt = System.currentTimeMillis(), dirty = 1)
        )
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

    /**
     * Sammelt alle Export-Kandidaten (Rezeptzutaten + Extras) für die angegebenen Tage, ohne
     * etwas zu schreiben — Grundlage für die Export-Vorschau, in der einzelne Produkte vor der
     * Übernahme abgewählt werden können.
     */
    suspend fun collectExportItems(dayIds: List<String>, desiredServings: Int = 0): List<WeekplanExportItem> {
        val items = mutableListOf<WeekplanExportItem>()
        dayIds.forEach { dayId ->
            weekplanDao.recipesForDay(dayId).forEach { entry ->
                val recipe = recipeDao.findById(entry.recipeId)
                val baseServings = recipe?.recipeYield?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 0
                val scale = if (desiredServings > 0 && baseServings > 0) desiredServings.toDouble() / baseServings else 1.0
                val ingredients = recipeDao.ingredientsByRecipeId(entry.recipeId)
                ingredients.filter { it.deleted == 0 }.forEach { ingredient ->
                    items.add(
                        WeekplanExportItem(
                            key = ingredient.id,
                            name = ingredient.food,
                            quantity = ingredient.quantity * scale,
                            unit = ingredient.unit,
                            recipeName = recipe?.name ?: "",
                        )
                    )
                }
            }
            weekplanDao.extrasForDay(dayId).forEach { extra ->
                items.add(
                    WeekplanExportItem(
                        key = extra.id,
                        name = extra.itemText,
                        quantity = 0.0,
                        unit = "",
                        recipeName = "",
                    )
                )
            }
        }
        return items
    }

    /** Schreibt die (in der Vorschau bestätigten) Export-Positionen in die Einkaufsliste. */
    suspend fun applyExportItems(items: List<WeekplanExportItem>, shoppingListId: String) {
        items.forEach { item ->
            shoppingRepository.addOrMergeItem(
                listId = shoppingListId,
                name = item.name,
                quantity = item.quantity,
                unit = item.unit,
                source = "weekplan",
                recipeName = item.recipeName,
            )
        }
    }

    // ── Tagesmarker (wochenplan A11) ────────────────────────────────────────────

    fun observeMarkers(): Flow<List<WeekplanDayMarkerEntity>> = weekplanDao.observeMarkers()

    fun observeMarkerAssignmentsForDays(dayIds: List<String>): Flow<List<WeekplanDayMarkerAssignmentEntity>> =
        weekplanDao.observeMarkerAssignmentsForDays(dayIds)

    suspend fun createMarker(name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        weekplanDao.upsertMarker(
            WeekplanDayMarkerEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                color = color,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
        )
    }

    suspend fun updateMarker(marker: WeekplanDayMarkerEntity, name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        weekplanDao.upsertMarker(
            marker.copy(name = trimmed, color = color, updatedAt = System.currentTimeMillis(), dirty = 1)
        )
    }

    suspend fun deleteMarker(marker: WeekplanDayMarkerEntity) {
        weekplanDao.upsertMarker(marker.copy(deleted = 1, updatedAt = System.currentTimeMillis(), dirty = 1))
    }

    /** Ordnet einen Marker einem Tag zu oder entfernt ihn wieder, falls bereits zugeordnet. */
    suspend fun toggleMarkerOnDay(dayId: String, markerId: String) {
        val existing = weekplanDao.findMarkerAssignment(dayId, markerId)
        val ts = System.currentTimeMillis()
        if (existing != null) {
            weekplanDao.upsertMarkerAssignment(existing.copy(deleted = 1, updatedAt = ts, dirty = 1))
        } else {
            weekplanDao.upsertMarkerAssignment(
                WeekplanDayMarkerAssignmentEntity(
                    id = UUID.randomUUID().toString(),
                    weekplanDayId = dayId,
                    markerId = markerId,
                    updatedAt = ts,
                    dirty = 1,
                )
            )
        }
    }

    suspend fun getWeekplanNutrition(startDate: String, endDate: String): WeekplanNutrition {
        val days = weekplanDao.getDaysBetween(startDate, endDate)
        val dayNutritions = mutableListOf<DayNutrition>()
        var totalKcal = 0.0
        var totalRecipes = 0

        days.forEach { day ->
            val recipes = weekplanDao.recipesForDay(day.id)
            val dayRecipes = mutableListOf<String>()
            var dayTotalKcal = 0.0

            recipes.forEach { entry ->
                val recipe = recipeDao.findById(entry.recipeId)
                if (recipe != null && recipe.deleted == 0) {
                    dayRecipes.add(recipe.name)
                    val nutrition = recipeRepository.getRecipeNutrition(recipe.id)
                    dayTotalKcal += nutrition.kcalPerPortion
                    totalRecipes++
                }
            }

            val avgDayKcal = if (recipes.isNotEmpty()) dayTotalKcal / recipes.size else 0.0
            dayNutritions.add(
                DayNutrition(
                    date = day.planDate,
                    recipeNames = dayRecipes,
                    avgKcal = avgDayKcal,
                    totalRecipes = recipes.size,
                )
            )
            totalKcal += avgDayKcal
        }

        val weekAvgKcal = if (days.isNotEmpty()) totalKcal / days.size else 0.0

        return WeekplanNutrition(
            days = dayNutritions,
            weekAvgKcal = weekAvgKcal,
            totalRecipes = totalRecipes,
        )
    }
}
