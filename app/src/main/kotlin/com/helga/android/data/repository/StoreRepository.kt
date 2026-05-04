package com.helga.android.data.repository

import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepository @Inject constructor(
    private val storeDao: StoreDao,
) {

    fun observeStores(): Flow<List<StoreEntity>> = storeDao.observeStores()

    fun observeActiveStore(): Flow<StoreEntity?> = storeDao.observeActiveStore()

    fun observeAisles(storeId: String): Flow<List<StoreAisleEntity>> =
        storeDao.observeAisles(storeId)

    fun observeStaples(listId: String): Flow<List<ShoppingListStapleEntity>> =
        storeDao.observeStaples(listId)

    suspend fun createStore(name: String): String {
        val id = UUID.randomUUID().toString()
        storeDao.upsertStore(
            StoreEntity(id = id, name = name, isActive = 0, updatedAt = now(), dirty = 1)
        )
        return id
    }

    suspend fun setActiveStore(storeId: String) {
        val ts = now()
        storeDao.deactivateAllStores(ts)
        storeDao.activateStore(storeId, ts)
    }

    suspend fun deleteStore(store: StoreEntity) {
        storeDao.upsertStore(store.copy(deleted = 1, updatedAt = now(), dirty = 1))
    }

    suspend fun addAisle(storeId: String, aisleName: String): String {
        val existing = storeDao.aislesForStore(storeId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = UUID.randomUUID().toString()
        storeDao.upsertAisle(
            StoreAisleEntity(
                id = id,
                storeId = storeId,
                aisleName = aisleName.trim(),
                sortOrder = nextOrder,
                updatedAt = now(),
                dirty = 1,
            )
        )
        return id
    }

    suspend fun deleteAisle(aisle: StoreAisleEntity) {
        storeDao.upsertAisle(aisle.copy(deleted = 1, updatedAt = now(), dirty = 1))
    }

    suspend fun moveAisleUp(aisle: StoreAisleEntity) {
        val aisles = storeDao.aislesForStore(aisle.storeId)
        val idx = aisles.indexOfFirst { it.id == aisle.id }
        if (idx <= 0) return
        val prev = aisles[idx - 1]
        val ts = now()
        storeDao.upsertAisle(aisle.copy(sortOrder = prev.sortOrder, updatedAt = ts, dirty = 1))
        storeDao.upsertAisle(prev.copy(sortOrder = aisle.sortOrder, updatedAt = ts, dirty = 1))
    }

    suspend fun moveAisleDown(aisle: StoreAisleEntity) {
        val aisles = storeDao.aislesForStore(aisle.storeId)
        val idx = aisles.indexOfFirst { it.id == aisle.id }
        if (idx < 0 || idx >= aisles.lastIndex) return
        val next = aisles[idx + 1]
        val ts = now()
        storeDao.upsertAisle(aisle.copy(sortOrder = next.sortOrder, updatedAt = ts, dirty = 1))
        storeDao.upsertAisle(next.copy(sortOrder = aisle.sortOrder, updatedAt = ts, dirty = 1))
    }

    suspend fun saveAisleProduct(productName: String, aisleName: String, storeId: String) {
        val existing = storeDao.findAisleProductEntry(productName.lowercase(), storeId)
        val ts = now()
        if (existing != null) {
            storeDao.upsertAisleProduct(
                existing.copy(aisleName = aisleName, updatedAt = ts, dirty = 1)
            )
        } else {
            storeDao.upsertAisleProduct(
                AisleProductEntity(
                    id = UUID.randomUUID().toString(),
                    aisleName = aisleName,
                    productName = productName.lowercase(),
                    storeId = storeId,
                    updatedAt = ts,
                    dirty = 1,
                )
            )
        }
    }

    suspend fun findAisleForProduct(productName: String, storeId: String): String? =
        storeDao.findAisleForProduct(productName.lowercase(), storeId)

    suspend fun addStaple(listId: String, name: String): String {
        val existing = storeDao.staplesForList(listId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = UUID.randomUUID().toString()
        storeDao.upsertStaple(
            ShoppingListStapleEntity(
                id = id,
                listId = listId,
                name = name.trim(),
                sortOrder = nextOrder,
                updatedAt = now(),
                dirty = 1,
            )
        )
        return id
    }

    suspend fun deleteStaple(staple: ShoppingListStapleEntity) {
        storeDao.upsertStaple(staple.copy(deleted = 1, updatedAt = now(), dirty = 1))
    }

    suspend fun staplesForList(listId: String): List<ShoppingListStapleEntity> =
        storeDao.staplesForList(listId)

    private fun now() = System.currentTimeMillis()
}
