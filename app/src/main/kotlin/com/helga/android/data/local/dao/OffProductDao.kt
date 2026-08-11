package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.OffProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OffProductDao {

    @Query("SELECT * FROM off_products WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun findById(id: String): OffProductEntity?

    @Query("SELECT * FROM off_products WHERE barcode = :barcode AND deleted = 0 LIMIT 1")
    suspend fun findByBarcode(barcode: String): OffProductEntity?

    @Query("SELECT * FROM off_products WHERE deleted = 0")
    suspend fun allActive(): List<OffProductEntity>

    @Query("SELECT * FROM off_products WHERE isFavorite = 1 AND deleted = 0")
    fun observeFavorites(): Flow<List<OffProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: OffProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<OffProductEntity>)

    @Query("SELECT * FROM off_products WHERE dirty = 1")
    suspend fun dirtyProducts(): List<OffProductEntity>

    @Query("UPDATE off_products SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)
}
