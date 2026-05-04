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

    suspend fun addItem(
        listId: String,
        name: String,
        quantity: Double = 1.0,
        unit: String = "",
        aisle: String = "",
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

    suspend fun deleteCheckedItems(listId: String) {
        val now = System.currentTimeMillis()
        val checked = shoppingDao.checkedItems(listId)
        if (checked.isNotEmpty()) {
            shoppingDao.upsertItems(checked.map { it.copy(deleted = 1, updatedAt = now, dirty = 1) })
        }
    }
}
