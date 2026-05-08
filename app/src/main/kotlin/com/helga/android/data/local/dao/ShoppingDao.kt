package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_lists WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND deleted = 0 ORDER BY aisle COLLATE NOCASE ASC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeItemsByList(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    suspend fun findListById(id: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun lists(): List<ShoppingListEntity>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE listId = :listId AND deleted = 0 AND checked = 0")
    suspend fun uncheckedItemCount(listId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: ShoppingListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLists(lists: List<ShoppingListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ShoppingItemEntity>)

    @Query("SELECT * FROM shopping_lists WHERE dirty = 1")
    suspend fun dirtyLists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_items WHERE dirty = 1")
    suspend fun dirtyItems(): List<ShoppingItemEntity>

    @Query("UPDATE shopping_lists SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearListDirty(ids: List<String>)

    @Query("UPDATE shopping_items SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearItemDirty(ids: List<String>)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND isChecked = 1 AND deleted = 0")
    suspend fun checkedItems(listId: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id LIMIT 1")
    suspend fun findItemById(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND name = :name COLLATE NOCASE AND unit = :unit COLLATE NOCASE AND isChecked = 0 AND deleted = 0 LIMIT 1")
    suspend fun findUncheckedItemByNameUnit(listId: String, name: String, unit: String): ShoppingItemEntity?
}
