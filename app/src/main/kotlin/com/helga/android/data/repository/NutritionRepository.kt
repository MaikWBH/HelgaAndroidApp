package com.helga.android.data.repository

import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.ReceiptArticleLinkDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.local.entity.ReceiptArticleLinkEntity
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.OffLookupBarcodeResponse
import com.helga.android.data.remote.dto.OffSearchRequest
import com.helga.android.data.util.ReceiptItemNormalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NutritionContributor(
    val displayName: String,
    val kcal: Double,
)

data class PurchasedNutritionSummary(
    val totalKcal: Double,
    val totalProteins: Double,
    val totalFats: Double,
    val totalCarbs: Double,
    val topContributors: List<NutritionContributor>,
    val unmatchedItemCount: Int,
)

/**
 * Verknüpft Bon-Artikel mit Open-Food-Facts-Produkten ("persönliche DB") und
 * aggregiert daraus den Einkaufs-Nährwert-Überblick. Siehe Phasenplan
 * "Nährwerte aus Kassenbons", Phase 1.
 */
@Singleton
class NutritionRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val receiptArticleLinkDao: ReceiptArticleLinkDao,
    private val offProductDao: OffProductDao,
    private val apiFactory: SyncApiFactory,
) {

    suspend fun findLink(normalizedName: String): ReceiptArticleLinkEntity? =
        receiptArticleLinkDao.findByNormalizedName(normalizedName)

    fun confirmedNormalizedNames(): Flow<Set<String>> = receiptArticleLinkDao.observeAll()
        .map { links -> links.filter { it.confirmed == 1 }.map { it.normalizedName }.toSet() }

    suspend fun manualSearch(query: String): List<OffProductEntity> {
        val response = apiFactory.api().searchOffProducts(OffSearchRequest(query = query))
        val entities = response.products.map { it.toEntity() }
        cacheProducts(entities)
        return entities
    }

    suspend fun confirmLink(normalizedName: String, displayName: String, product: OffProductEntity) {
        offProductDao.upsert(product)
        val now = System.currentTimeMillis()
        val existing = receiptArticleLinkDao.findByNormalizedName(normalizedName)
        receiptArticleLinkDao.upsert(
            ReceiptArticleLinkEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                normalizedName = normalizedName,
                displayName = displayName,
                offProductId = product.id,
                offBarcode = product.barcode,
                confirmed = 1,
                confirmedAt = now,
                updatedAt = now,
                deleted = 0,
                dirty = 1,
            )
        )
    }

    suspend fun setManualPackageGrams(productId: String, grams: Double) {
        val product = offProductDao.findById(productId) ?: return
        offProductDao.upsert(
            product.copy(
                packageGrams = grams,
                packageGramsManual = 1,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
        )
    }

    /**
     * Gruppiert alle Bon-Artikel wie [ReceiptRepository.productSummaries] (normalisierter
     * Name, Kotlin-`lowercase()` wegen Umlauten) und summiert die Nährwerte über bestätigte
     * Verknüpfungen mit bekannter Packungsgröße. Artikel ohne verwertbare Verknüpfung zählen
     * nur in [PurchasedNutritionSummary.unmatchedItemCount], statt geraten zu werden.
     */
    suspend fun purchasedNutritionTotals(): PurchasedNutritionSummary {
        val points = receiptDao.allPricePoints()
        if (points.isEmpty()) {
            return PurchasedNutritionSummary(0.0, 0.0, 0.0, 0.0, emptyList(), 0)
        }

        val linksByName = receiptArticleLinkDao.allActive()
            .filter { it.confirmed == 1 }
            .associateBy { it.normalizedName }
        val productsById = offProductDao.allActive().associateBy { it.id }

        var totalKcal = 0.0
        var totalProteins = 0.0
        var totalFats = 0.0
        var totalCarbs = 0.0
        var unmatchedCount = 0
        val contributors = mutableListOf<NutritionContributor>()

        points.groupBy { ReceiptItemNormalizer.normalize(it.name) }.forEach { (normalizedName, group) ->
            val link = linksByName[normalizedName]
            val product = link?.let { productsById[it.offProductId] }
            if (product == null || product.packageGrams <= 0.0) {
                unmatchedCount += group.size
                return@forEach
            }

            val factor = group.sumOf { it.quantity } * product.packageGrams / 100.0
            val kcal = factor * product.kcalPerUnit
            totalKcal += kcal
            totalProteins += factor * product.proteins
            totalFats += factor * product.fats
            totalCarbs += factor * product.carbs

            val displayName = group.groupBy { it.name }.maxBy { it.value.size }.key
            contributors += NutritionContributor(displayName, kcal)
        }

        return PurchasedNutritionSummary(
            totalKcal = totalKcal,
            totalProteins = totalProteins,
            totalFats = totalFats,
            totalCarbs = totalCarbs,
            topContributors = contributors.sortedByDescending { it.kcal }.take(5),
            unmatchedItemCount = unmatchedCount,
        )
    }

    private suspend fun cacheProducts(entities: List<OffProductEntity>) {
        if (entities.isNotEmpty()) offProductDao.upsertAll(entities)
    }
}

private fun OffLookupBarcodeResponse.toEntity(): OffProductEntity = OffProductEntity(
    id = id.ifBlank { barcode },
    barcode = barcode,
    name = name,
    brand = brand,
    categories = categories,
    kcalPerUnit = kcalPerUnit,
    proteins = proteins,
    fats = fats,
    carbs = carbs,
    nutriScore = nutriScore,
    nova = nova,
    ecoScore = ecoScore,
    allergenes = allergenes,
    additives = additives,
    isOrganic = isOrganic,
    vegan = vegan,
    vegetarian = vegetarian,
    imagePath = imagePath,
    isFavorite = isFavorite,
    packageGrams = packageGrams,
    packageGramsManual = packageGramsManual,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)
