package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.MonthlyBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBudgetDao {
    @Query("SELECT * FROM monthly_budgets WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<MonthlyBudgetEntity?>

    @Query("SELECT * FROM monthly_budgets WHERE id = 'global' LIMIT 1")
    suspend fun get(): MonthlyBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: MonthlyBudgetEntity)

    @Query("SELECT * FROM monthly_budgets WHERE dirty = 1")
    suspend fun dirty(): List<MonthlyBudgetEntity>

    @Query("UPDATE monthly_budgets SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)
}
