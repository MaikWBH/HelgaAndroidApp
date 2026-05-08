package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.RecipeHistoryEntity

@Dao
interface RecipeHistoryDao {

    @Query("SELECT * FROM recipe_history WHERE dirty = 1")
    suspend fun dirtyHistory(): List<RecipeHistoryEntity>

    @Query("UPDATE recipe_history SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<RecipeHistoryEntity>)
}
