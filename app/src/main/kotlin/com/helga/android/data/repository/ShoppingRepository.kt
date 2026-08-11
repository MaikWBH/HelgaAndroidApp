package com.helga.android.data.repository

import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.model.ItemCostEstimate
import com.helga.android.data.model.ItemOrigin
import com.helga.android.data.model.ItemOrigins
import com.helga.android.data.model.ListCostEstimate
import com.helga.android.data.model.StoreCost
import com.helga.android.data.util.ReceiptItemNormalizer
import com.helga.android.data.util.ShoppingUnitConverter
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val storeRepository: StoreRepository,
    private val receiptRepository: ReceiptRepository,
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
        recipeName: String = "",
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val origins = listOf(ItemOrigin(recipe = recipeName, quantity = quantity, unit = unit))
        shoppingDao.upsertItem(
            ShoppingItemEntity(
                id = id,
                listId = listId,
                name = name,
                quantity = quantity,
                unit = unit,
                aisle = aisle,
                source = source,
                origins = ItemOrigins.encode(origins),
                updatedAt = now,
                dirty = 1,
            )
        )
        return id
    }

    suspend fun toggleChecked(item: ShoppingItemEntity) {
        val now = System.currentTimeMillis()
        val newCheckedState = if (item.isChecked == 0) 1 else 0
        shoppingDao.upsertItem(
            item.copy(
                isChecked = newCheckedState,
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
     * Schätzt die Kosten einer Einkaufsliste anhand des Kassenbon-Preisverlaufs
     * ([ReceiptRepository.productPriceHistory]). Pro Position wird der Name über
     * [ReceiptItemNormalizer] auf denselben Schlüssel wie die Bon-Erfassung
     * abgebildet; ohne bisherigen Kauf bleibt die Position preislos (price = null).
     */
    suspend fun estimateListCosts(listId: String): ListCostEstimate {
        val items = shoppingDao.itemsByList(listId).filter { it.deleted == 0 }
        val histories = items.associateWith { item ->
            receiptRepository.productPriceHistory(ReceiptItemNormalizer.normalize(item.name))
        }
        val itemEstimates = items.map { item ->
            val price = histories[item]?.avgPrice
            ItemCostEstimate(
                itemId = item.id,
                name = item.name,
                quantity = item.quantity,
                unit = item.unit,
                price = price,
                totalPrice = price,
            )
        }
        val pricedCount = itemEstimates.count { it.totalPrice != null }
        val totalCost = itemEstimates.sumOf { it.totalPrice ?: 0.0 }
        val accuracy = if (itemEstimates.isEmpty()) 0.0 else pricedCount.toDouble() / itemEstimates.size

        val storeNames = mutableMapOf<String, String>()
        val storeSums = mutableMapOf<String, Double>()
        histories.values.filterNotNull().forEach { history ->
            history.storeComparison.forEach { store ->
                val key = store.storeId.ifBlank { store.storeName }
                storeNames[key] = store.storeName
                storeSums[key] = (storeSums[key] ?: 0.0) + store.bestPrice
            }
        }
        val storeComparison = storeSums.map { (key, sum) ->
            StoreCost(storeName = storeNames[key] ?: key, totalCost = sum)
        }.sortedBy { it.totalCost }

        return ListCostEstimate(
            listId = listId,
            items = itemEstimates,
            totalCost = totalCost,
            estimatedAccuracy = accuracy,
            storeComparison = storeComparison,
        )
    }

    /**
     * Fügt eine Zutat zur Liste hinzu oder mergt sie in ein bestehendes, noch
     * unabgehaktes Item mit gleichem Namen. Einheiten aus derselben Umrechnungs-
     * familie (g/kg, ml/l/cl, siehe [ShoppingUnitConverter]) werden dabei
     * zusammengeführt statt als getrennte Positionen stehen zu bleiben; bei
     * inkompatiblen Einheiten (z. B. "Stück" vs. "g") bleiben sie wie bisher
     * getrennt. Gang-Zuordnung wird bei Neuanlage automatisch über die aktive
     * Filiale nachgeschlagen. Gemeinsamer Pfad für Rezept- und Wochenplan-
     * Export, damit beide identische Ergebnisse liefern.
     */
    suspend fun addOrMergeItem(
        listId: String,
        name: String,
        quantity: Double,
        unit: String,
        source: String = "recipe",
        recipeName: String = "",
    ) {
        val norm = name.trim()
        if (norm.isBlank()) return
        val cleanUnit = unit.trim()
        val existing = shoppingDao.findUncheckedItemsByName(listId, norm)
            .firstOrNull { ShoppingUnitConverter.isCompatible(it.unit, cleanUnit) }
        val now = System.currentTimeMillis()
        val newOrigin = ItemOrigin(recipe = recipeName, quantity = quantity, unit = cleanUnit)
        if (existing != null) {
            val convertedQuantity = ShoppingUnitConverter.convert(quantity, cleanUnit, existing.unit)
            val mergedOrigins = ItemOrigins.decode(existing.origins) + newOrigin
            shoppingDao.upsertItem(
                existing.copy(
                    quantity = existing.quantity + convertedQuantity,
                    origins = ItemOrigins.encode(mergedOrigins),
                    updatedAt = now,
                    dirty = 1,
                )
            )
        } else {
            val aisle = storeRepository.findActiveStoreId()
                ?.let { storeId -> storeRepository.findAisleForProduct(norm, storeId) }
                ?: ""
            shoppingDao.upsertItem(
                ShoppingItemEntity(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    name = norm,
                    quantity = quantity,
                    unit = cleanUnit,
                    aisle = aisle,
                    source = source,
                    origins = ItemOrigins.encode(listOf(newOrigin)),
                    updatedAt = now,
                    dirty = 1,
                )
            )
        }
    }
}
