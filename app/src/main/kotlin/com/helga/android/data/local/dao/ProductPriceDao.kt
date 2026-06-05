package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.ProductPriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: ProductPriceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<ProductPriceEntity>)

    @Query("SELECT * FROM product_prices WHERE offProductId = :offProductId AND deleted = 0 ORDER BY price ASC")
    suspend fun getPricesByProductId(offProductId: String): List<ProductPriceEntity>

    @Query("SELECT * FROM product_prices WHERE offProductId = :offProductId AND storeName = :storeName AND deleted = 0 LIMIT 1")
    suspend fun getPriceForStore(offProductId: String, storeName: String): ProductPriceEntity?

    @Query("SELECT * FROM product_prices WHERE deleted = 0 ORDER BY updated_at DESC LIMIT :limit")
    suspend fun getLatestPrices(limit: Int = 100): List<ProductPriceEntity>

    @Query("SELECT DISTINCT storeName FROM product_prices WHERE deleted = 0 ORDER BY storeName")
    suspend fun getAllStores(): List<String>

    @Query("SELECT * FROM product_prices WHERE dirty = 1 AND deleted = 0")
    suspend fun dirtyPrices(): List<ProductPriceEntity>

    @Query("UPDATE product_prices SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearPricesDirty(ids: List<String>)

    @Query("UPDATE product_prices SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM product_prices WHERE offProductId = :offProductId AND deleted = 0")
    fun observePricesForProduct(offProductId: String): Flow<List<ProductPriceEntity>>
}
