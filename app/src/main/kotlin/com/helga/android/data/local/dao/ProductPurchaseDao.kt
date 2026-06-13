package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.ProductPurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPurchaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(purchase: ProductPurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(purchases: List<ProductPurchaseEntity>)

    @Query("SELECT * FROM product_purchases WHERE dirty = 1")
    suspend fun dirty(): List<ProductPurchaseEntity>

    @Query("UPDATE product_purchases SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT * FROM product_purchases WHERE deleted = 0 ORDER BY purchase_date DESC")
    fun observeAll(): Flow<List<ProductPurchaseEntity>>

    @Query("SELECT id, updated_at FROM product_purchases WHERE deleted = 0")
    suspend fun timestamps(): List<TimestampRow>
}
