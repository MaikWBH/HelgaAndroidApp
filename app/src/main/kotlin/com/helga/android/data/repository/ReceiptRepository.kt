package com.helga.android.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.local.ReceiptScanResult
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.toDbValue
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.util.ReceiptItemNormalizer
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.ReceiptParseRequest
import com.helga.android.data.remote.dto.ReceiptReconcileRequest
import com.helga.android.data.remote.dto.ReconcileReceiptItemDto
import com.helga.android.data.remote.dto.ReconcileShoppingItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
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
    private val shoppingDao: ShoppingDao,
    private val apiFactory: SyncApiFactory,
) {

    fun observeReceipts(): Flow<List<ReceiptEntity>> = receiptDao.observeReceipts()

    fun observeReceiptDetail(id: String): Flow<ReceiptEntity?> = receiptDao.observeById(id)

    fun observeReceiptItems(receiptId: String): Flow<List<ReceiptItemEntity>> =
        receiptDao.observeItems(receiptId)

    suspend fun findReceipt(id: String): ReceiptEntity? = receiptDao.findById(id)

    /**
     * Liest einen Bon aus — bevorzugt per KI-Vision auf dem Server (deutlich
     * zuverlässiger), mit automatischem Fallback auf die On-Device-OCR (ML Kit),
     * wenn der Server nicht erreichbar ist oder kein brauchbares Ergebnis liefert.
     * Kein DB-Write, kein Speichern — nur für die Vorschau.
     */
    suspend fun scanReceipt(bitmap: Bitmap): ReceiptScanResult = withContext(Dispatchers.IO) {
        try {
            val aiResult = parseReceiptWithAi(bitmap)
            if (aiResult != null) return@withContext aiResult
            Timber.i("KI-Bon-Erkennung ohne Ergebnis – Fallback auf On-Device-OCR")
        } catch (e: Exception) {
            Timber.w(e, "KI-Bon-Erkennung fehlgeschlagen – Fallback auf On-Device-OCR")
        }
        receiptScanner.scanReceiptImage(bitmap)
    }

    /** Scannt und speichert in einem Schritt (Direkt-Speicherung ohne Vorschau). */
    suspend fun scanAndSaveReceipt(bitmap: Bitmap): String {
        val result = scanReceipt(bitmap)
        saveReceipt(result.receipt, result.items)
        return result.receipt.id
    }

    /**
     * Sendet das Bon-Foto an das Vision-Modell und baut daraus ein [ReceiptScanResult].
     * Gibt `null` zurück, wenn die KI nichts Verwertbares erkannt hat (→ Fallback).
     */
    private suspend fun parseReceiptWithAi(bitmap: Bitmap): ReceiptScanResult? {
        val base64 = encodeJpegBase64(bitmap)
        val response = apiFactory.api().parseReceipt(
            ReceiptParseRequest(imageBase64 = base64, mimeType = "image/jpeg")
        )

        val hasContent = response.items.isNotEmpty() ||
            response.storeName.isNotBlank() ||
            response.totalAmount > 0.0
        if (!hasContent) return null

        val now = System.currentTimeMillis()
        val receiptId = UUID.randomUUID().toString()
        val receipt = ReceiptEntity(
            id = receiptId,
            storeName = response.storeName,
            purchaseDate = if (response.purchaseDate > 0) response.purchaseDate else now,
            totalAmount = response.totalAmount,
            currency = "EUR",
            rawOcrText = "",
            status = "scanned",
            source = ScanSource.AI.toDbValue(),
            updatedAt = now,
            deleted = 0,
            dirty = 0,
        )
        val lowConfidenceIds = mutableSetOf<String>()
        val items = response.items.mapIndexed { index, dto ->
            val total = if (dto.totalPrice != 0.0) dto.totalPrice else dto.unitPrice * dto.quantity
            val qty = if (dto.quantity != 0.0) dto.quantity else 1.0
            val itemId = UUID.randomUUID().toString()
            // Unsicher, wenn das Modell wenig Vertrauen meldet oder kein Preis erkannt wurde.
            if (dto.confidence < CONFIDENCE_THRESHOLD || total <= 0.0) {
                lowConfidenceIds += itemId
            }
            ReceiptItemEntity(
                id = itemId,
                receiptId = receiptId,
                position = index,
                rawText = dto.name,
                name = dto.name,
                quantity = qty,
                unitPrice = if (dto.unitPrice != 0.0) dto.unitPrice else total / qty,
                totalPrice = total,
                updatedAt = now,
                deleted = 0,
                dirty = 0,
            )
        }

        // Deterministischer Gegencheck: Summe der Positionen vs. Bon-Gesamtbetrag.
        // Das kann Smart Receipts nicht (nur Kopfdaten) – wir haben die Positionen.
        val itemsSum = items.sumOf { it.totalPrice }
        val sumMismatch = response.totalAmount > 0.0 &&
            kotlin.math.abs(itemsSum - response.totalAmount) >
            maxOf(response.totalAmount * 0.05, 0.10)

        val needsReview = lowConfidenceIds.isNotEmpty() ||
            response.confidence < CONFIDENCE_THRESHOLD ||
            sumMismatch

        return ReceiptScanResult(receipt, items, lowConfidenceIds, needsReview, ScanSource.AI)
    }

    /** Skaliert das Bild herunter und kodiert es als Base64-JPEG für die KI-Anfrage. */
    private fun encodeJpegBase64(bitmap: Bitmap): String {
        val scaled = downscale(bitmap, MAX_AI_IMAGE_DIM)
        val baos = ByteArrayOutputStream()
        // Höhere Qualität: kleiner Bon-Druck darf durch JPEG-Artefakte nicht
        // verschmieren – das Vision-Modell liest sonst Preise/Namen falsch.
        scaled.compress(Bitmap.CompressFormat.JPEG, AI_JPEG_QUALITY, baos)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
        )
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

        val response = apiFactory.api().reconcileReceipt(request)

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
        // Längste Bildkante für die KI-Anfrage; begrenzt Payload-Größe und Latenz,
        // ohne dass Bon-Text unleserlich wird. Bewusst höher als zuvor (1600),
        // damit kleiner Druck auf langen Bons für das Vision-Modell lesbar bleibt.
        const val MAX_AI_IMAGE_DIM = 2000
        // JPEG-Qualität für die KI-Anfrage (höher = schärferer Text, größere Payload).
        const val AI_JPEG_QUALITY = 90
        // Unter diesem Konfidenz-Wert wird eine Position zur Prüfung markiert.
        const val CONFIDENCE_THRESHOLD = 0.7
    }
}
