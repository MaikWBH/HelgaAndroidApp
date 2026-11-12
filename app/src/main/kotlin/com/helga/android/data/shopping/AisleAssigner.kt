package com.helga.android.data.shopping

import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.model.ProductCategory
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.util.AisleKeywordMatcher
import com.helga.android.data.util.AisleProductKey
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
}
