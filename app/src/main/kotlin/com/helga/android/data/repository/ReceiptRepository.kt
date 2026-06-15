package com.helga.android.data.repository

import android.graphics.Bitmap
import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.local.ReceiptScanResult
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val receiptScanner: ReceiptScanner,
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
}
