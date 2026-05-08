package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.helga.android.data.local.entity.PantryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    @Query("SELECT * FROM pantry_items WHERE deleted = 0 ORDER BY category COLLATE NOCASE ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun all(): List<PantryItemEntity>

    @Query("SELECT * FROM pantry_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PantryItemEntity?

    @Query("SELECT * FROM pantry_items WHERE LOWER(name) = LOWER(:name) AND deleted = 0 LIMIT 1")
    suspend fun findByName(name: String): PantryItemEntity?

    @Upsert
    suspend fun upsert(item: PantryItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<PantryItemEntity>)

    @Query("SELECT * FROM pantry_items WHERE dirty = 1")
    suspend fun dirtyItems(): List<PantryItemEntity>

    @Query("UPDATE pantry_items SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT COUNT(*) FROM pantry_items WHERE deleted = 0")
    suspend fun count(): Int
}
