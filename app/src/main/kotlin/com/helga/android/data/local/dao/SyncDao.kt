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

    @Query("SELECT id, updatedAt FROM shopping_lists")
    suspend fun shoppingListTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM shopping_items")
    suspend fun shoppingItemTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM stores")
    suspend fun storeTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM store_aisles")
    suspend fun storeAisleTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM aisle_products")
    suspend fun aisleProductTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM shopping_list_staples")
    suspend fun stapleTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM quick_emojis")
    suspend fun quickEmojiTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM weekplan_days")
    suspend fun weekplanDayTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM weekplan_recipes")
    suspend fun weekplanRecipeTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM weekplan_extras")
    suspend fun weekplanExtraTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM weekplan_settings")
    suspend fun weekplanSettingsTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM weekplan_constraints")
    suspend fun weekplanConstraintsTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM receipts")
    suspend fun receiptTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM receipt_items")
    suspend fun receiptItemTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM monthly_budgets")
    suspend fun monthlyBudgetTimestamps(): List<TimestampRow>

    @Query("SELECT id, updatedAt FROM off_products")
    suspend fun offProductTimestamps(): List<TimestampRow>
}

data class TimestampRow(val id: String, val updatedAt: Long)
