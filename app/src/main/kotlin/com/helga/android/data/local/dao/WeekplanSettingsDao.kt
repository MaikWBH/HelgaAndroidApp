package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.WeekplanSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeekplanSettingsDao {
    @Query("SELECT * FROM weekplan_settings WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<WeekplanSettingsEntity?>

    @Query("SELECT * FROM weekplan_settings WHERE id = 'global' LIMIT 1")
    suspend fun get(): WeekplanSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: WeekplanSettingsEntity)

    @Query("SELECT * FROM weekplan_settings WHERE dirty = 1")
    suspend fun dirty(): List<WeekplanSettingsEntity>

    @Query("UPDATE weekplan_settings SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)
}
