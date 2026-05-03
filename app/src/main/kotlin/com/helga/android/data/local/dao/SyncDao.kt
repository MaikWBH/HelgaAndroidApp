package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Liefert die `updatedAt`-Werte aller Einträge einer Tabelle in einem Rutsch.
 * Wird vom SyncEngine zur LWW-Vergleichsbasis genutzt – pro Sync nur eine Query
 * pro Tabelle, kein N+1.
 */
@Dao
interface SyncDao {

    @Query("SELECT id, updatedAt FROM recipes")
    suspend fun recipeTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM recipe_ingredients")
    suspend fun ingredientTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM recipe_instructions")
    suspend fun instructionTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM recipe_tags")
    suspend fun tagTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM recipe_categories")
    suspend fun categoryTimestamps(): List<TimestampRow>
}

data class TimestampRow(val id: String, val updatedAt: Long)
