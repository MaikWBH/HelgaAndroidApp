package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    @Query("SELECT * FROM stores WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE isActive = 1 AND deleted = 0 LIMIT 1")
    fun observeActiveStore(): Flow<StoreEntity?>

    @Query("SELECT * FROM store_aisles WHERE storeId = :storeId AND deleted = 0 ORDER BY sortOrder ASC")
    fun observeAisles(storeId: String): Flow<List<StoreAisleEntity>>

    @Query("SELECT * FROM store_aisles WHERE storeId = :storeId AND deleted = 0 ORDER BY sortOrder ASC")
    suspend fun aislesForStore(storeId: String): List<StoreAisleEntity>

    @Query("SELECT aisleName FROM aisle_products WHERE productName = :productName AND storeId = :storeId AND deleted = 0 LIMIT 1")
    suspend fun findAisleForProduct(productName: String, storeId: String): String?

    @Query("SELECT * FROM aisle_products WHERE productName = :productName AND storeId = :storeId AND deleted = 0 LIMIT 1")
    suspend fun findAisleProductEntry(productName: String, storeId: String): AisleProductEntity?

    @Query("SELECT * FROM shopping_list_staples WHERE listId = :listId AND deleted = 0 ORDER BY sortOrder ASC")
    fun observeStaples(listId: String): Flow<List<ShoppingListStapleEntity>>

    @Query("SELECT * FROM shopping_list_staples WHERE listId = :listId AND deleted = 0 ORDER BY sortOrder ASC")
    suspend fun staplesForList(listId: String): List<ShoppingListStapleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStore(store: StoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStores(stores: List<StoreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAisle(aisle: StoreAisleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAisles(aisles: List<StoreAisleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAisleProduct(ap: AisleProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAisleProducts(aps: List<AisleProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaple(staple: ShoppingListStapleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaples(staples: List<ShoppingListStapleEntity>)

    @Query("UPDATE stores SET isActive = 0, updatedAt = :ts, dirty = 1 WHERE isActive = 1 AND deleted = 0")
    suspend fun deactivateAllStores(ts: Long)

    @Query("UPDATE stores SET isActive = 1, updatedAt = :ts, dirty = 1 WHERE id = :id")
    suspend fun activateStore(id: String, ts: Long)

    @Query("SELECT * FROM stores WHERE dirty = 1")
    suspend fun dirtyStores(): List<StoreEntity>

    @Query("SELECT * FROM store_aisles WHERE dirty = 1")
    suspend fun dirtyAisles(): List<StoreAisleEntity>

    @Query("SELECT * FROM aisle_products WHERE dirty = 1")
    suspend fun dirtyAisleProducts(): List<AisleProductEntity>

    @Query("SELECT * FROM shopping_list_staples WHERE dirty = 1")
    suspend fun dirtyStaples(): List<ShoppingListStapleEntity>

    @Query("UPDATE stores SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearStoreDirty(ids: List<String>)

    @Query("UPDATE store_aisles SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearAisleDirty(ids: List<String>)

    @Query("UPDATE aisle_products SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearAisleProductDirty(ids: List<String>)

    @Query("UPDATE shopping_list_staples SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearStapleDirty(ids: List<String>)
}
