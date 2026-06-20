package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.RecipeHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeHistoryDao {

    @Query("SELECT * FROM recipe_history WHERE dirty = 1")
    suspend fun dirtyHistory(): List<RecipeHistoryEntity>

    @Query("UPDATE recipe_history SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<RecipeHistoryEntity>)

    @Query("SELECT * FROM recipe_history WHERE deleted = 0 AND cooked = 1 AND plannedDate >= :since")
    fun observeSince(since: String): Flow<List<RecipeHistoryEntity>>

    @Query("SELECT DISTINCT recipeId FROM recipe_history WHERE deleted = 0 AND cooked = 1 AND plannedDate >= :since")
    suspend fun getRecentRecipeIds(since: String): List<String>

    @Query("SELECT DISTINCT recipeId FROM recipe_history WHERE deleted = 0 AND cooked = 1 AND plannedDate < :before")
    suspend fun getRecipeIdsBefore(before: String): List<String>

    @Query("""
        UPDATE recipe_history SET cooked = 1, updatedAt = :updatedAt, dirty = 1
        WHERE recipeId = :recipeId AND plannedDate = :date AND deleted = 0
    """)
    suspend fun markCooked(recipeId: String, date: String, updatedAt: Long): Int
}
