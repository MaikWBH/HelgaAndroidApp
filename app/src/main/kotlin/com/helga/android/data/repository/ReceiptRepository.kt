package com.helga.android.data.repository

import android.graphics.Bitmap
import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.local.ReceiptScanResult
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.ReceiptReconcileRequest
import com.helga.android.data.remote.dto.ReconcileReceiptItemDto
import com.helga.android.data.remote.dto.ReconcileShoppingItemDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Scannt lokal per ML Kit (kein DB-Write, kein Server) — für die Vorschau. */
    suspend fun scanReceipt(bitmap: Bitmap): ReceiptScanResult =
        receiptScanner.scanReceiptImage(bitmap)

    /** Scannt und speichert in einem Schritt (Direkt-Speicherung ohne Vorschau). */
    suspend fun scanAndSaveReceipt(bitmap: Bitmap): String {
        val result = receiptScanner.scanReceiptImage(bitmap)
        saveReceipt(result.receipt, result.items)
        return result.receipt.id
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
}
