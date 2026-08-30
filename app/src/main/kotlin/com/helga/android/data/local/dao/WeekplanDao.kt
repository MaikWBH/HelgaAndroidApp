package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeekplanDao {

    // ── Observe ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM weekplan_days WHERE deleted = 0 ORDER BY planDate ASC")
    fun observeDays(): Flow<List<WeekplanDayEntity>>

    @Query("SELECT * FROM weekplan_days WHERE deleted = 0 AND planDate >= :startDate AND planDate <= :endDate ORDER BY planDate ASC")
    fun observeDaysBetween(startDate: String, endDate: String): Flow<List<WeekplanDayEntity>>

    @Query("SELECT * FROM weekplan_recipes WHERE weekplanDayId = :dayId AND deleted = 0 ORDER BY position ASC")
    fun observeRecipesForDay(dayId: String): Flow<List<WeekplanRecipeEntity>>

    @Query("SELECT * FROM weekplan_recipes WHERE weekplanDayId IN (:dayIds) AND deleted = 0 ORDER BY position ASC")
    fun observeRecipesForDays(dayIds: List<String>): Flow<List<WeekplanRecipeEntity>>

    @Query("SELECT * FROM weekplan_extras WHERE weekplanDayId = :dayId AND deleted = 0 ORDER BY position ASC")
    fun observeExtrasForDay(dayId: String): Flow<List<WeekplanExtraEntity>>

    @Query("SELECT * FROM weekplan_extras WHERE weekplanDayId IN (:dayIds) AND deleted = 0 ORDER BY position ASC")
    fun observeExtrasForDays(dayIds: List<String>): Flow<List<WeekplanExtraEntity>>

    @Query("SELECT COUNT(*) FROM weekplan_recipes WHERE weekplanDayId = :dayId AND deleted = 0")
    fun observeRecipeCount(dayId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM weekplan_extras WHERE weekplanDayId = :dayId AND deleted = 0")
    fun observeExtraCount(dayId: String): Flow<Int>

    // ── One-shot reads ────────────────────────────────────────────────────────

    @Query("SELECT * FROM weekplan_days WHERE planDate = :planDate AND deleted = 0 LIMIT 1")
    suspend fun findDayByDate(planDate: String): WeekplanDayEntity?

    @Query("SELECT * FROM weekplan_days WHERE deleted = 0 AND planDate >= :startDate AND planDate <= :endDate ORDER BY planDate ASC")
    suspend fun getDaysBetween(startDate: String, endDate: String): List<WeekplanDayEntity>

    @Query("SELECT wr.recipeId FROM weekplan_days wd INNER JOIN weekplan_recipes wr ON wr.weekplanDayId = wd.id WHERE wd.planDate = :date AND wd.deleted = 0 AND wr.deleted = 0 ORDER BY wr.position ASC LIMIT 1")
    fun observeTodayRecipeId(date: String): Flow<String?>

    @Query("SELECT * FROM weekplan_recipes WHERE weekplanDayId = :dayId AND deleted = 0 ORDER BY position ASC")
    suspend fun recipesForDay(dayId: String): List<WeekplanRecipeEntity>

    @Query("SELECT r.name FROM recipes r WHERE r.id = :recipeId AND r.deleted = 0 LIMIT 1")
    suspend fun recipeName(recipeId: String): String?

    @Query("SELECT * FROM weekplan_extras WHERE weekplanDayId = :dayId AND deleted = 0 ORDER BY position ASC")
    suspend fun extrasForDay(dayId: String): List<WeekplanExtraEntity>

    @Query("SELECT MAX(position) FROM weekplan_recipes WHERE weekplanDayId = :dayId AND deleted = 0")
    suspend fun maxRecipePosition(dayId: String): Int?

    @Query("SELECT MAX(position) FROM weekplan_extras WHERE weekplanDayId = :dayId AND deleted = 0")
    suspend fun maxExtraPosition(dayId: String): Int?

    // ── Upserts ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: WeekplanDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDays(days: List<WeekplanDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekplanRecipe(entry: WeekplanRecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekplanRecipes(entries: List<WeekplanRecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtra(extra: WeekplanExtraEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtras(extras: List<WeekplanExtraEntity>)

    // ── Soft-delete ───────────────────────────────────────────────────────────

    @Query("UPDATE weekplan_recipes SET deleted = 1, updatedAt = :ts, dirty = 1 WHERE id = :id")
    suspend fun softDeleteWeekplanRecipe(id: String, ts: Long)

    @Query("UPDATE weekplan_extras SET deleted = 1, updatedAt = :ts, dirty = 1 WHERE id = :id")
    suspend fun softDeleteExtra(id: String, ts: Long)

    @Query("UPDATE weekplan_days SET deleted = 1, updatedAt = :ts, dirty = 1 WHERE id = :id")
    suspend fun softDeleteDay(id: String, ts: Long)

    // ── Note update ───────────────────────────────────────────────────────────

    @Query("UPDATE weekplan_days SET note = :note, updatedAt = :ts, dirty = 1 WHERE id = :id")
    suspend fun updateNote(id: String, note: String, ts: Long)

    @Query("UPDATE weekplan_days SET isSkipped = :skipped, updatedAt = :ts, dirty = 1 WHERE id = :id")
    suspend fun setSkipped(id: String, skipped: Int, ts: Long)

    // ── Dirty / Sync ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM weekplan_days WHERE dirty = 1")
    suspend fun dirtyDays(): List<WeekplanDayEntity>

    @Query("SELECT * FROM weekplan_recipes WHERE dirty = 1")
    suspend fun dirtyWeekplanRecipes(): List<WeekplanRecipeEntity>

    @Query("SELECT * FROM weekplan_extras WHERE dirty = 1")
    suspend fun dirtyExtras(): List<WeekplanExtraEntity>

    @Query("UPDATE weekplan_days SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDayDirty(ids: List<String>)

    @Query("UPDATE weekplan_recipes SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearWeekplanRecipeDirty(ids: List<String>)

    @Query("UPDATE weekplan_extras SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearExtraDirty(ids: List<String>)
}
