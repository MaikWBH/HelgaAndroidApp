package com.helga.android

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.receipts.toEntity
import com.helga.android.data.receipts.toRemoteDto
import com.helga.android.data.receipts.toUpsertDto
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptRemoteItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptMappingTest {

    private val remoteItem = ReceiptRemoteItemDto(
        id = "i1", receiptId = "r1", position = 2, rawText = "G&G H-MILCH", name = "H-Milch",
        quantity = 2.0, unit = "stk", unitPrice = 0.95, totalPrice = 1.9, discount = 0.3, isDeposit = 0,
        category = "milch_eier", offBarcode = "4001", confidence = 0.8, matchedShoppingItemId = "s1",
        matchStatus = "matched", updatedAt = 77, deleted = 0,
    )

    private val remote = ReceiptRemoteDto(
        id = "r1", storeId = "st1", storeName = "REWE", storeAddress = "Hauptstr. 1", shoppingListId = "l1",
        purchaseDate = 1_794_268_800_000L, purchaseDay = "2026-11-10", purchaseTime = "17:42", totalAmount = 12.3,
        discountTotal = 0.5, depositTotal = 0.25, currency = "EUR", imagePath = "receipt_r1.jpg", status = "review",
        source = "ai", confidence = 0.9, parseStatus = "done", updatedAt = 99, deleted = 0, serverSeq = 5,
        items = listOf(remoteItem),
    )

    @Test
    fun `receipt round trip keeps all server fields`() {
        val entity = remote.toEntity(null)
        val back = entity.toUpsertDto(listOf(remoteItem.toEntity()))
        assertEquals("REWE", back.storeName)
        assertEquals("Hauptstr. 1", back.storeAddress)
        assertEquals("2026-11-10", back.purchaseDay)
        assertEquals("17:42", back.purchaseTime)
        assertEquals(12.3, back.totalAmount, 0.0)
        assertEquals(0.5, back.discountTotal, 0.0)
        assertEquals(0.25, back.depositTotal, 0.0)
        assertEquals("review", back.status)
        assertEquals("ai", back.source)
        assertEquals(99L, back.updatedAt)
        assertEquals(listOf(remoteItem), back.items)
        assertEquals(0, entity.dirty)
    }

    @Test
    fun `item round trip keeps all fields`() {
        assertEquals(remoteItem, remoteItem.toEntity().toRemoteDto())
    }

    @Test
    fun `local only fields are kept from the existing entity`() {
        val local = ReceiptEntity(
            id = "r1", localImageUri = "file:///a.jpg", pendingUpload = 1, cachedUntil = 123L,
            rawOcrText = "roh", source = "on_device",
        )
        val merged = remote.copy(source = "").toEntity(local)
        assertEquals("file:///a.jpg", merged.localImageUri)
        assertEquals(1, merged.pendingUpload)
        assertEquals(123L, merged.cachedUntil)
        assertEquals("roh", merged.rawOcrText)
        assertEquals("on_device", merged.source) // leere Server-Quelle → lokale bleibt
        assertEquals("review", merged.status)    // alles andere kommt vom Server
    }

    @Test
    fun `empty server source without local entity defaults to ai`() {
        assertEquals("ai", remote.copy(source = "").toEntity(null).source)
    }

    @Test
    fun `server source wins over local source`() {
        assertEquals("manual", remote.copy(source = "manual").toEntity(ReceiptEntity(id = "r1", source = "ai")).source)
    }

    @Test
    fun `unused item entity keeps default values`() {
        val item = ReceiptItemEntity(id = "x", receiptId = "r")
        assertEquals("", item.unit)
        assertEquals(1.0, item.confidence, 0.0)
    }
}
