package com.helga.android.data.shopping

import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.model.ProductCategory
import com.helga.android.data.remote.dto.CategorizeRequest
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.util.AisleKeywordMatcher
import com.helga.android.data.util.AisleProductKey
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordnet Einkaufslisten-Artikel automatisch einem Gang des aktiven Markts zu (Phase 3b).
 * Reihenfolge der Quellen: 1. gelernte Zuordnung (`aisle_products`, Handkorrekturen gewinnen immer),
 * 2. lokale Stichwortliste ([AisleKeywordMatcher]) über die Gang-Kategorien des Markts, 3. Server-KI im Hintergrund.
 */
@Singleton
class AisleAssigner @Inject constructor(
    private val storeDao: StoreDao,
    private val shoppingDao: ShoppingDao,
    private val apiProvider: ApiProvider,
) {

    /**
     * Gangname des Markts für eine Kategorie: erster Gang (nach `sortOrder`) mit diesem Schlüssel, sonst erster
     * Gang der Ausweichkategorie (eine Stufe tief), sonst `""`. `sonstiges`, `pfand` und Unbekanntes → `""`.
     */
    suspend fun aisleForCategory(storeId: String, categoryKey: String): String {
        val category = ProductCategory.fromKey(categoryKey)?.takeIf { it.isAisle } ?: return ""
        val aisles = storeDao.aislesForStore(storeId).sortedBy { it.sortOrder }
        aisles.firstOrNull { it.categoryKey == category.key }?.let { return it.aisleName }
        val fallback = ProductCategory.fallbackFor(category) ?: return ""
        return aisles.firstOrNull { it.categoryKey == fallback.key }?.aisleName.orEmpty()
    }

    /** Lokale Zuordnung ohne Netz: Gelerntes, dann Stichwort. Ohne aktiven Markt (`storeId == null`) → `""`. */
    suspend fun resolveLocal(productName: String, storeId: String?): String {
        if (storeId == null) return ""
        storeDao.findAisleForProduct(AisleProductKey.normalize(productName), storeId)?.let { return it }
        return aisleForCategory(storeId, AisleKeywordMatcher.categoryFor(productName))
    }

    /**
     * Sortiert offene Artikel ohne Gang nach: erst lokal ([resolveLocal], falls inzwischen Gänge/Gelerntes da sind),
     * dann die übrigen Namen (höchstens 50, je Name einmal) per Server-KI. Eine Server-Kategorie wird nur
     * übernommen, wenn der aktive Markt dafür einen Gang hat; die Zuordnung wird als `AisleProduct` gelernt.
     * @return Anzahl geänderter Artikel; ohne aktiven Markt, ohne Kandidaten oder bei Fehlern 0 für den Server-Teil
     */
    suspend fun assignRemote(): Int {
        val storeId = storeDao.findActiveStore()?.id ?: return 0
        val open = shoppingDao.uncheckedItemsWithoutAisle()
        if (open.isEmpty()) return 0
        var changed = 0
        val unresolved = mutableListOf<ShoppingItemEntity>()
        val now = System.currentTimeMillis()
        for (item in open) {
            val aisle = resolveLocal(item.name, storeId)
            if (aisle.isNotEmpty()) {
                shoppingDao.upsertItem(item.copy(aisle = aisle, updatedAt = now, dirty = 1))
                changed++
            } else {
                unresolved += item
            }
        }
        val names = unresolved.map { it.name.trim() }.filter { it.isNotEmpty() }
            .distinctBy { AisleProductKey.normalize(it) }.take(MAX_NAMES)
        if (names.isEmpty()) return changed

        val response = try {
            apiProvider.api().categorizeItems(CategorizeRequest(names))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Server-Kategorisierung der Artikel fehlgeschlagen")
            return changed
        }
        for (result in response.results) {
            val aisle = aisleForCategory(storeId, result.category)
            if (aisle.isEmpty()) continue
            val key = AisleProductKey.normalize(result.name)
            val matching = unresolved.filter { AisleProductKey.normalize(it.name) == key }
            if (matching.isEmpty()) continue
            matching.forEach { shoppingDao.upsertItem(it.copy(aisle = aisle, updatedAt = now, dirty = 1)) }
            changed += matching.size
            learn(key, aisle, storeId, now)
        }
        return changed
    }

    private suspend fun learn(productKey: String, aisleName: String, storeId: String, now: Long) {
        val existing = storeDao.findAisleProductEntry(productKey, storeId)
        storeDao.upsertAisleProduct(
            existing?.copy(aisleName = aisleName, updatedAt = now, dirty = 1)
                ?: AisleProductEntity(
                    id = UUID.randomUUID().toString(),
                    aisleName = aisleName,
                    productName = productKey,
                    storeId = storeId,
                    updatedAt = now,
                    dirty = 1,
                )
        )
    }

    private companion object {
        const val MAX_NAMES = 50
    }
}
