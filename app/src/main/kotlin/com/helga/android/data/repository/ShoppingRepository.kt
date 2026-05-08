package com.helga.android.data.repository

import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingDao: ShoppingDao,
) {

    fun observeLists(): Flow<List<ShoppingListEntity>> = shoppingDao.observeLists()

    fun observeItemsByList(listId: String): Flow<List<ShoppingItemEntity>> =
        shoppingDao.observeItemsByList(listId)

    suspend fun createList(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        shoppingDao.upsertList(
            ShoppingListEntity(id = id, name = name, updatedAt = now, dirty = 1)
        )
        return id
    }

    suspend fun setDefaultList(listId: String) {
        val now = System.currentTimeMillis()
        val lists = shoppingDao.lists()
        val updated = lists.map { list ->
            val isTarget = if (list.id == listId) 1 else 0
            if (list.isDefaultRecipe == isTarget && list.isActive == isTarget) list
            else list.copy(
                isDefaultRecipe = isTarget,
                isActive = isTarget,
                updatedAt = now,
                dirty = 1,
            )
        }
        if (updated.isNotEmpty()) shoppingDao.upsertLists(updated)
    }

    suspend fun deleteList(list: ShoppingListEntity) {
        val now = System.currentTimeMillis()
        shoppingDao.upsertList(list.copy(deleted = 1, updatedAt = now, dirty = 1, isActive = 0))
    }

    suspend fun addItem(
        listId: String,
        name: String,
        quantity: Double = 1.0,
        unit: String = "",
        aisle: String = "",
        source: String = "manual",
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        shoppingDao.upsertItem(
            ShoppingItemEntity(
                id = id,
                listId = listId,
                name = name,
                quantity = quantity,
                unit = unit,
                aisle = aisle,
                source = source,
                updatedAt = now,
                dirty = 1,
            )
        )
        return id
    }

    suspend fun toggleChecked(item: ShoppingItemEntity) {
        val now = System.currentTimeMillis()
        shoppingDao.upsertItem(
            item.copy(
                isChecked = if (item.isChecked == 0) 1 else 0,
                updatedAt = now,
                dirty = 1,
            )
        )
    }

    suspend fun softDeleteItem(item: ShoppingItemEntity) {
        val now = System.currentTimeMillis()
        shoppingDao.upsertItem(item.copy(deleted = 1, updatedAt = now, dirty = 1))
    }

    suspend fun updateItemAisle(item: ShoppingItemEntity, aisle: String) {
        shoppingDao.upsertItem(item.copy(aisle = aisle, updatedAt = System.currentTimeMillis(), dirty = 1))
    }

    suspend fun updateItem(id: String, quantity: Double, unit: String, name: String) {
        val existing = shoppingDao.findItemById(id) ?: return
        shoppingDao.upsertItem(
            existing.copy(
                quantity = quantity,
                unit = unit.trim(),
                name = name.trim(),
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
        )
    }

    suspend fun deleteCheckedItems(listId: String) {
        val now = System.currentTimeMillis()
        val checked = shoppingDao.checkedItems(listId)
        if (checked.isNotEmpty()) {
            shoppingDao.upsertItems(checked.map { it.copy(deleted = 1, updatedAt = now, dirty = 1) })
        }
    }

    /**
     * Adds an ingredient to the list. If an unchecked item with the same name+unit already exists,
     * its quantity is summed instead of adding a duplicate entry.
     */
    suspend fun addOrMergeItem(
        listId: String,
        name: String,
        quantity: Double,
        unit: String,
        aisle: String = "",
        source: String = "recipe",
    ) {
        val norm = name.trim()
        if (norm.isBlank()) return
        val existing = shoppingDao.findUncheckedItemByNameUnit(listId, norm, unit.trim())
        val now = System.currentTimeMillis()
        if (existing != null) {
            shoppingDao.upsertItem(existing.copy(quantity = existing.quantity + quantity, updatedAt = now, dirty = 1))
        } else {
            shoppingDao.upsertItem(
                ShoppingItemEntity(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    name = norm,
                    quantity = quantity,
                    unit = unit.trim(),
                    aisle = aisle,
                    source = source,
                    updatedAt = now,
                    dirty = 1,
                )
            )
        }
    }
}
