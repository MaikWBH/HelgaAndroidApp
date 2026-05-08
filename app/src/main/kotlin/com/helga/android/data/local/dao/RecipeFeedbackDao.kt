package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeFeedbackDao {

    @Upsert
    suspend fun upsert(entity: RecipeFeedbackEntity)

    @Upsert
    suspend fun upsertAll(entities: List<RecipeFeedbackEntity>)

    @Query("SELECT * FROM recipe_feedback WHERE deleted = 0 AND recipeId = :recipeId AND plannedDate = :date LIMIT 1")
    suspend fun findByRecipeAndDate(recipeId: String, date: String): RecipeFeedbackEntity?

    @Query("SELECT * FROM recipe_feedback WHERE deleted = 0 AND plannedDate = :date")
    fun observeForDate(date: String): Flow<List<RecipeFeedbackEntity>>

    @Query("SELECT * FROM recipe_feedback WHERE dirty = 1 AND deleted = 0")
    suspend fun getDirty(): List<RecipeFeedbackEntity>

    @Query("UPDATE recipe_feedback SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT * FROM recipe_feedback WHERE updatedAt > :since")
    suspend fun getModifiedSince(since: Long): List<RecipeFeedbackEntity>
}
