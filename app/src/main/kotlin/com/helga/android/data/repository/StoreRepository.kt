package com.helga.android.data.repository

import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.model.ProductCategory
import com.helga.android.data.util.AisleProductKey
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Vorbefüllung für neu angelegte Märkte (maerkte A3) — spart das manuelle Eintippen jedes Gangs. */
sealed interface StorePrefill {
    data object None : StorePrefill
    data object DefaultTemplate : StorePrefill
    data class CopyFrom(val storeId: String) : StorePrefill
}

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

    suspend fun createStore(name: String, prefill: StorePrefill = StorePrefill.None): String {
        val id = UUID.randomUUID().toString()
        val ts = now()
        storeDao.upsertStore(
            StoreEntity(id = id, name = name, isActive = 0, updatedAt = ts, dirty = 1)
        )
        // Paare aus Gangname und Kategorie-Schlüssel (Vorlage: gemeinsamer Katalog, Kopie: Quellmarkt)
        val aisles: List<Pair<String, String>> = when (prefill) {
            StorePrefill.None -> emptyList()
            StorePrefill.DefaultTemplate ->
                ProductCategory.aisleCategories.map { it.defaultAisleName to it.key }
            is StorePrefill.CopyFrom ->
                storeDao.aislesForStore(prefill.storeId).map { it.aisleName to it.categoryKey }
        }
        if (aisles.isNotEmpty()) {
            storeDao.upsertAisles(
                aisles.mapIndexed { index, (aisleName, categoryKey) ->
                    StoreAisleEntity(
                        id = UUID.randomUUID().toString(),
                        storeId = id,
                        aisleName = aisleName,
                        sortOrder = index,
                        categoryKey = categoryKey,
                        updatedAt = ts,
                        dirty = 1,
                    )
                }
            )
        }
        return id
    }

    suspend fun setActiveStore(storeId: String) {
        val ts = now()
        storeDao.deactivateAllStores(ts)
        storeDao.activateStore(storeId, ts)
    }

    suspend fun deactivateAll() {
        storeDao.deactivateAllStores(now())
    }

    suspend fun deleteStore(store: StoreEntity) {
        storeDao.upsertStore(store.copy(deleted = 1, updatedAt = now(), dirty = 1))
    }

    /**
     * Legt einen Gang am Ende an. Ohne Kategorie-Schlüssel und mit einem Namen, der (ohne Groß-/Kleinschreibung)
     * einem Standard-Gangnamen entspricht, wird dessen Kategorie automatisch gesetzt.
     */
    suspend fun addAisle(storeId: String, aisleName: String, categoryKey: String = ""): String {
        val existing = storeDao.aislesForStore(storeId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = UUID.randomUUID().toString()
        val name = aisleName.trim()
        val key = categoryKey.ifEmpty {
            ProductCategory.aisleCategories
                .firstOrNull { it.defaultAisleName.equals(name, ignoreCase = true) }?.key.orEmpty()
        }
        storeDao.upsertAisle(
            StoreAisleEntity(
                id = id,
                storeId = storeId,
                aisleName = name,
                sortOrder = nextOrder,
                categoryKey = key,
                updatedAt = now(),
                dirty = 1,
            )
        )
        return id
    }

    /** Ordnet einem Gang eine Kategorie zu (`""` = keine). */
    suspend fun setAisleCategory(aisle: StoreAisleEntity, categoryKey: String) {
        storeDao.upsertAisle(aisle.copy(categoryKey = categoryKey, updatedAt = now(), dirty = 1))
    }

    /**
     * Hängt für jede Gang-Kategorie, die der Markt noch nicht hat, einen Standardgang ans Ende an.
     * @return Anzahl der ergänzten Gänge
     */
    suspend fun addMissingDefaultAisles(storeId: String): Int {
        val existing = storeDao.aislesForStore(storeId)
        val present = existing.map { it.categoryKey }.toSet()
        val missing = ProductCategory.aisleCategories.filter { it.key !in present }
        if (missing.isEmpty()) return 0
        val ts = now()
        val start = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        storeDao.upsertAisles(
            missing.mapIndexed { index, category ->
                StoreAisleEntity(
                    id = UUID.randomUUID().toString(),
                    storeId = storeId,
                    aisleName = category.defaultAisleName,
                    sortOrder = start + index,
                    categoryKey = category.key,
                    updatedAt = ts,
                    dirty = 1,
                )
            }
        )
        return missing.size
    }

    suspend fun aislesForStore(storeId: String): List<StoreAisleEntity> = storeDao.aislesForStore(storeId)

    suspend fun deleteAisle(aisle: StoreAisleEntity) {
        storeDao.upsertAisle(aisle.copy(deleted = 1, updatedAt = now(), dirty = 1))
    }

    /** Übernimmt die per Drag-and-Drop gezogene Reihenfolge (maerkte A1). */
    suspend fun reorderAisles(storeId: String, orderedIds: List<String>) {
        val byId = storeDao.aislesForStore(storeId).associateBy { it.id }
        val ts = now()
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            byId[id]?.takeIf { it.sortOrder != index }?.copy(sortOrder = index, updatedAt = ts, dirty = 1)
        }
        if (updated.isNotEmpty()) storeDao.upsertAisles(updated)
    }

    suspend fun saveAisleProduct(productName: String, aisleName: String, storeId: String) {
        val key = AisleProductKey.normalize(productName)
        val existing = storeDao.findAisleProductEntry(key, storeId)
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
                    productName = key,
                    storeId = storeId,
                    updatedAt = ts,
                    dirty = 1,
                )
            )
        }
    }

    suspend fun findAisleForProduct(productName: String, storeId: String): String? =
        storeDao.findAisleForProduct(AisleProductKey.normalize(productName), storeId)

    suspend fun findActiveStoreId(): String? = storeDao.findActiveStore()?.id

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
