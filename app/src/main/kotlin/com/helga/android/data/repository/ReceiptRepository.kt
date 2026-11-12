package com.helga.android.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.model.CostByDate
import com.helga.android.data.model.CostByStore
import com.helga.android.data.model.CostSummary
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.MonthlyBudgetEntity
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.receipts.ReceiptImageStore
import com.helga.android.data.receipts.ReceiptScanFlow
import com.helga.android.data.receipts.ReceiptScanOutcome
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.ReceiptItemNormalizer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.dto.ReceiptReconcileRequest
import com.helga.android.data.remote.dto.ReconcileReceiptItemDto
import com.helga.android.data.remote.dto.ReconcileShoppingItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ── Price history domain models ───────────────────────────────────────────────

data class ProductSummary(
    val normalizedKey: String,
    val displayName: String,
    val buyCount: Int,
    val lastPrice: Double,
    val cheapestPrice: Double,
    val storeCount: Int,
)

data class StoreBestPrice(
    val storeId: String,
    val storeName: String,
    val bestPrice: Double,
    val isCheapest: Boolean,
)

data class ProductPricePoint(
    val purchaseDate: Long,
    val storeId: String,
    val storeName: String,
    val unitPrice: Double,
    val quantity: Double = 1.0,
    val totalPrice: Double = 0.0,
)

data class ProductPriceHistory(
    val displayName: String,
    val points: List<ProductPricePoint>,
    val storeComparison: List<StoreBestPrice>,
    val avgPrice: Double,
    val minPrice: Double,
    val maxPrice: Double,
)

/** Ergebnis eines KI-Abgleichs für die Anzeige (Namen statt IDs). */
data class ReconcileOutcome(
    val matchedCount: Int,
    val unexpectedNames: List<String>,
    val missingNames: List<String>,
)

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val receiptScanner: ReceiptScanner,
    private val monthlyBudgetDao: MonthlyBudgetDao,
    private val shoppingDao: ShoppingDao,
    private val apiProvider: ApiProvider,
    private val imageStore: ReceiptImageStore,
    private val syncScheduler: SyncScheduler,
) {

    fun observeReceipts(): Flow<List<ReceiptEntity>> = receiptDao.observeReceipts()

    /** Gemeinsames Monatsbudget (null = noch nicht geladen). */
    fun observeBudget(): Flow<MonthlyBudgetEntity?> = monthlyBudgetDao.observe()

    /**
     * Speichert das Monatsbudget lokal mit dirty = 1. [amount] wird auf >= 0 begrenzt,
     * [warnThreshold] auf 0.5–1.0.
     */
    suspend fun saveBudget(amount: Double, warnThreshold: Double) {
        monthlyBudgetDao.upsert(
            MonthlyBudgetEntity(
                id = "global",
                amount = amount.coerceAtLeast(0.0),
                warnThreshold = warnThreshold.coerceIn(0.5, 1.0),
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
        )
    }

    fun observeReceiptCountForListToday(listId: String, startMs: Long, endMs: Long): Flow<Int> =
        receiptDao.observeReceiptCountForListToday(listId, startMs, endMs)

    /** Summe und Anzahl der Kassenbons im Zeitraum (Epoch-Sekunden). */
    suspend fun totalCost(startEpochSec: Long, endEpochSec: Long): CostSummary =
        receiptDao.totalCostForRange(startEpochSec, endEpochSec)

    /** Ausgaben pro Markt; ohne Zeitraum über alle Bons. */
    suspend fun costByStore(startEpochSec: Long? = null, endEpochSec: Long? = null): List<CostByStore> =
        if (startEpochSec == null || endEpochSec == null) receiptDao.costByStore()
        else receiptDao.costByStoreRange(startEpochSec, endEpochSec)

    /** Ausgaben pro Datum; ohne Zeitraum über alle Bons. */
    suspend fun costByDate(startEpochSec: Long? = null, endEpochSec: Long? = null): List<CostByDate> =
        if (startEpochSec == null || endEpochSec == null) receiptDao.costByDate()
        else receiptDao.costByDateRange(startEpochSec, endEpochSec)

    fun observeReceiptDetail(id: String): Flow<ReceiptEntity?> = receiptDao.observeById(id)

    fun observeReceiptItems(receiptId: String): Flow<List<ReceiptItemEntity>> =
        receiptDao.observeItems(receiptId)

    suspend fun findReceipt(id: String): ReceiptEntity? = receiptDao.findById(id)

    private val scanFlow = ReceiptScanFlow<Bitmap>(
        scanOnServer = { request ->
            val file = request.file
            val plainText = "text/plain".toMediaType()
            apiProvider.api().scanReceipt(
                file = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody("image/jpeg".toMediaType()),
                ),
                receiptId = request.receiptId.toRequestBody(plainText),
                shoppingListId = request.shoppingListId.toRequestBody(plainText),
                capturedAt = request.capturedAt.toString().toRequestBody(plainText),
                preliminary = null,
            )
        },
        scanLocally = { bitmap ->
            val scanned = receiptScanner.scanReceiptImage(bitmap)
            scanned.receipt to scanned.items
        },
        saveLocal = { receipt, items ->
            receiptDao.upsertReceipt(receipt)
            receiptDao.upsertItems(items)
        },
        deleteFile = { imageStore.delete(it.absolutePath) },
    )

    /**
     * Scannt einen Bon: verkleinertes Foto zum Server (KI liest aus), bei Ausfall vorläufige ML-Kit-Werte.
     * Das Ergebnis liegt immer in Room; die Kamera-Temp-Datei wird in jedem Fall gelöscht.
     */
    suspend fun scanFromUri(uri: Uri, shoppingListId: String): ReceiptScanOutcome = withContext(Dispatchers.IO) {
        val receiptId = UUID.randomUUID().toString()
        try {
            val prepared = imageStore.prepare(uri, receiptId)
                ?: throw IllegalStateException("Bild konnte nicht geladen werden")
            val outcome = scanFlow.run(receiptId, prepared.file, prepared.bitmap, shoppingListId)
            prepared.bitmap.recycle()
            // Foto wartet lokal: Upload einreihen, sobald Netz da ist.
            if (outcome is ReceiptScanOutcome.Provisional && !outcome.queuedOnServer) {
                syncScheduler.enqueueReceiptUpload()
            }
            outcome
        } finally {
            imageStore.deleteCameraTemp(uri)
        }
    }

    suspend fun saveReceipt(receipt: ReceiptEntity, items: List<ReceiptItemEntity> = emptyList()) {
        val now = System.currentTimeMillis()
        receiptDao.upsertReceipt(receipt.copy(updatedAt = now, dirty = 1))

        if (items.isNotEmpty()) {
            val itemsWithReceiptId = items.map { it.copy(receiptId = receipt.id, updatedAt = now, dirty = 1) }
            receiptDao.upsertItems(itemsWithReceiptId)
        }
    }

    suspend fun updateReceipt(receipt: ReceiptEntity) {
        receiptDao.upsertReceipt(
            receipt.copy(updatedAt = System.currentTimeMillis(), dirty = 1)
        )
    }

    suspend fun addItemToReceipt(receiptId: String, item: ReceiptItemEntity) {
        val now = System.currentTimeMillis()
        val items = receiptDao.itemsForReceipt(receiptId)
        val position = items.size

        receiptDao.upsertItem(
            item.copy(
                receiptId = receiptId,
                position = position,
                updatedAt = now,
                dirty = 1,
            )
        )
    }

    suspend fun deleteReceipt(receiptId: String) {
        val now = System.currentTimeMillis()
        // Soft delete receipt
        val receipt = receiptDao.findById(receiptId) ?: return
        receiptDao.upsertReceipt(receipt.copy(deleted = 1, updatedAt = now, dirty = 1))

        // Soft delete all items
        val items = receiptDao.itemsForReceipt(receiptId)
        items.forEach { item ->
            receiptDao.upsertItem(item.copy(deleted = 1, updatedAt = now, dirty = 1))
        }
    }

    suspend fun linkReceiptToShoppingList(receiptId: String, shoppingListId: String) {
        val receipt = receiptDao.findById(receiptId) ?: return
        receiptDao.upsertReceipt(
            receipt.copy(
                shoppingListId = shoppingListId,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
        )
    }

    suspend fun updateReceiptStore(receiptId: String, storeId: String, storeName: String) {
        val receipt = receiptDao.findById(receiptId) ?: return
        receiptDao.upsertReceipt(
            receipt.copy(
                storeId = storeId,
                storeName = storeName,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
        )
    }

    // ── Price History ─────────────────────────────────────────────────────────

    /**
     * Groups all purchased items by normalized name and returns one summary per product.
     * Grouping uses Kotlin lowercase() so German umlauts are handled correctly.
     */
    suspend fun productSummaries(): List<ProductSummary> {
        val points = receiptDao.allPricePoints()
        if (points.isEmpty()) return emptyList()

        val grouped = points.groupBy { ReceiptItemNormalizer.normalize(it.name) }

        return grouped.map { (key, group) ->
            val displayName = group.groupBy { it.name }.maxBy { it.value.size }.key
            ProductSummary(
                normalizedKey = key,
                displayName = displayName,
                buyCount = group.size,
                lastPrice = group.first().unitPrice, // already sorted by purchaseDate DESC
                cheapestPrice = group.minOf { it.unitPrice },
                storeCount = group.map { it.storeId.ifBlank { it.storeName } }.distinct().size,
            )
        }.sortedBy { it.displayName }
    }

    /**
     * Returns the full price history for a single product identified by its normalized key.
     */
    suspend fun productPriceHistory(normalizedKey: String): ProductPriceHistory? {
        val points = receiptDao.allPricePoints()
        val group = points.filter { ReceiptItemNormalizer.normalize(it.name) == normalizedKey }
        if (group.isEmpty()) return null

        val displayName = group.groupBy { it.name }.maxBy { it.value.size }.key

        // Best (minimum) price per store
        val byStore = group.groupBy { it.storeId.ifBlank { it.storeName } }
        val storePrices = byStore.map { (_, entries) ->
            val sample = entries.first()
            val best = entries.minOf { it.unitPrice }
            Triple(sample.storeId, sample.storeName, best)
        }
        val globalMin = storePrices.minOf { it.third }

        val storeComparison = storePrices.map { (storeId, storeName, best) ->
            StoreBestPrice(
                storeId = storeId,
                storeName = storeName.ifBlank { storeId.ifBlank { "Unbekannter Markt" } },
                bestPrice = best,
                isCheapest = best == globalMin,
            )
        }.sortedBy { it.bestPrice }

        val priceList = group.map { it.unitPrice }

        return ProductPriceHistory(
            displayName = displayName,
            points = group.map {
                ProductPricePoint(it.purchaseDate, it.storeId, it.storeName, it.unitPrice, it.quantity, it.totalPrice)
            },
            storeComparison = storeComparison,
            avgPrice = priceList.average(),
            minPrice = priceList.min(),
            maxPrice = priceList.max(),
        )
    }

    /**
     * KI-Abgleich (Phase 4): vergleicht die abgehakten Items der verknüpften
     * Einkaufsliste mit den Bon-Positionen. Schreibt matchStatus/matchedShoppingItemId
     * zurück und setzt receipt.status = "reconciled".
     */
    suspend fun reconcile(receiptId: String): ReconcileOutcome {
        val receipt = receiptDao.findById(receiptId)
            ?: return ReconcileOutcome(0, emptyList(), emptyList())

        val checked = if (receipt.shoppingListId.isNotBlank()) {
            shoppingDao.checkedItems(receipt.shoppingListId)
        } else emptyList()
        val items = receiptDao.itemsForReceipt(receiptId)

        val request = ReceiptReconcileRequest(
            checkedItems = checked.map {
                ReconcileShoppingItemDto(id = it.id, name = it.name, quantity = it.quantity, unit = it.unit)
            },
            receiptItems = items.map {
                ReconcileReceiptItemDto(id = it.id, name = it.name, rawText = it.rawText, totalPrice = it.totalPrice)
            },
        )

        val response = apiProvider.api().reconcileReceipt(request)

        val matchByReceiptId = response.matches.associate { it.receiptItemId to it.shoppingItemId }
        val unexpectedIds = response.unexpected.toSet()
        val now = System.currentTimeMillis()

        val updatedItems = items.map { item ->
            val matchedShoppingId = matchByReceiptId[item.id]
            val status = when {
                matchedShoppingId != null -> "matched"
                item.id in unexpectedIds -> "unexpected"
                else -> ""
            }
            item.copy(
                matchedShoppingItemId = matchedShoppingId ?: "",
                matchStatus = status,
                updatedAt = now,
                dirty = 1,
            )
        }
        if (updatedItems.isNotEmpty()) receiptDao.upsertItems(updatedItems)
        receiptDao.upsertReceipt(receipt.copy(status = "reconciled", updatedAt = now, dirty = 1))

        val missingIds = response.missing.toSet()
        val missingNames = checked.filter { it.id in missingIds }.map { it.name }
        val unexpectedNames = items.filter { it.id in unexpectedIds }.map { it.name.ifBlank { it.rawText } }

        return ReconcileOutcome(
            matchedCount = matchByReceiptId.size,
            unexpectedNames = unexpectedNames,
            missingNames = missingNames,
        )
    }

    private companion object {
    }
}
