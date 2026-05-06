package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeekplanConstraintsDao {
    @Query("SELECT * FROM weekplan_constraints WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<WeekplanConstraintsEntity?>

    @Query("SELECT * FROM weekplan_constraints WHERE id = 'global' LIMIT 1")
    suspend fun get(): WeekplanConstraintsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(constraints: WeekplanConstraintsEntity)

    @Query("SELECT * FROM weekplan_constraints WHERE dirty = 1")
    suspend fun dirty(): List<WeekplanConstraintsEntity>

    @Query("UPDATE weekplan_constraints SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)
}
