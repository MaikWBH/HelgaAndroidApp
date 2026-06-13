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
    suspend fun getById(id: String): OffProductEntity?

    @Query("SELECT * FROM off_products WHERE barcode = :barcode AND deleted = 0 LIMIT 1")
    suspend fun getByBarcode(barcode: String): OffProductEntity?

    @Query("SELECT * FROM off_products WHERE name LIKE '%' || :query || '%' AND deleted = 0 LIMIT :limit")
    suspend fun search(query: String, limit: Int = 5): List<OffProductEntity>

    @Query("SELECT * FROM off_products WHERE id = :id")
    fun observeById(id: String): Flow<OffProductEntity?>

    // ── Persönlicher Katalog "Meine Produkte" ────────────────────────────────

    @Query("SELECT * FROM off_products WHERE isFavorite = 1 AND deleted = 0 ORDER BY name")
    fun observeFavorites(): Flow<List<OffProductEntity>>

    @Query(
        "SELECT * FROM off_products WHERE isFavorite = 1 AND deleted = 0 " +
            "AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%') " +
            "ORDER BY name LIMIT :limit"
    )
    suspend fun searchFavorites(query: String, limit: Int = 10): List<OffProductEntity>

    @Query("UPDATE off_products SET isFavorite = :value, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, value: Int, updatedAt: Long)

    @Query("SELECT * FROM off_products WHERE barcode IN (:barcodes) AND deleted = 0")
    suspend fun getByBarcodes(barcodes: List<String>): List<OffProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: OffProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<OffProductEntity>)

    @Query("SELECT * FROM off_products WHERE dirty = 1")
    suspend fun dirtyProducts(): List<OffProductEntity>

    @Query("UPDATE off_products SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearProductsDirty(ids: List<String>)

    @Query("DELETE FROM off_products WHERE id = :id")
    suspend fun delete(id: String)
}
