package com.helga.android

import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.receipts.ReceiptImageStore
import com.helga.android.data.receipts.ReceiptSyncer
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRepositoryConfirmTest {

    private val dao: ReceiptDao = mockk(relaxed = true)
    private val store: ReceiptImageStore = mockk(relaxed = true)
    private val repo = ReceiptRepository(
        dao, mockk<ReceiptScanner>(), mockk<MonthlyBudgetDao>(), mockk<ShoppingDao>(),
        mockk<ApiProvider>(), store, mockk<SyncScheduler>(), mockk<ReceiptSyncer>(),
    )

    private fun item(id: String, price: Double = 1.0, deposit: Int = 0, deleted: Int = 0) =
        ReceiptItemEntity(id = id, receiptId = "r1", totalPrice = price, isDeposit = deposit, deleted = deleted)

    @Test
    fun `confirmReceipt writes flags totals and marks removed items deleted`() = runTest {
        coEvery { dao.allItemsForReceipt("r1") } returns listOf(item("a"), item("b"), item("old", deleted = 1))
        val receiptSlot = slot<ReceiptEntity>()
        val itemsSlot = slot<List<ReceiptItemEntity>>()
        coEvery { dao.upsertReceiptWithItems(capture(receiptSlot), capture(itemsSlot)) } returns Unit

        repo.confirmReceipt(
            ReceiptEntity(id = "r1", status = "review", purchaseDay = "2026-11-10", purchaseDate = 1L),
            listOf(item("a", 2.0), item("p", -0.25, deposit = 1), item("q", 0.5, deposit = 1)),
        )

        val receipt = receiptSlot.captured
        assertEquals("confirmed", receipt.status)
        assertEquals(1_794_268_800_000L, receipt.purchaseDate)
        assertEquals(0.25, receipt.depositTotal, 1e-9)
        assertEquals(1, receipt.dirty)
        val written = itemsSlot.captured.associateBy { it.id }
        assertEquals(setOf("a", "p", "q", "b"), written.keys) // "old" war schon gelöscht
        assertEquals(1, written.getValue("b").deleted)
        assertEquals(1, written.getValue("b").dirty)
        assertEquals(listOf(0, 1, 2), listOf("a", "p", "q").map { written.getValue(it).position })
        assertEquals(0, written.getValue("a").deleted)
    }

    @Test
    fun `confirmReceipt keeps the purchase date when no valid day is known`() = runTest {
        coEvery { dao.allItemsForReceipt("r1") } returns emptyList()
        val receiptSlot = slot<ReceiptEntity>()
        coEvery { dao.upsertReceiptWithItems(capture(receiptSlot), any()) } returns Unit
        repo.confirmReceipt(ReceiptEntity(id = "r1", purchaseDay = "", purchaseDate = 42L), emptyList())
        assertEquals(42L, receiptSlot.captured.purchaseDate)
    }

    @Test
    fun `deleteReceipt hard deletes receipts waiting for upload including the photo`() = runTest {
        coEvery { dao.findById("r1") } returns ReceiptEntity(id = "r1", pendingUpload = 1, localImageUri = "/x/r1.jpg")
        repo.deleteReceipt("r1")
        coVerify { store.delete("/x/r1.jpg") }
        coVerify { dao.hardDeleteReceiptWithItems("r1") }
        coVerify(exactly = 0) { dao.upsertReceiptWithItems(any(), any()) }
    }

    @Test
    fun `deleteReceipt hard deletes provisional receipts that never reached the server`() = runTest {
        coEvery { dao.findById("r1") } returns ReceiptEntity(id = "r1", status = "provisional", imagePath = "")
        repo.deleteReceipt("r1")
        coVerify { dao.hardDeleteReceiptWithItems("r1") }
    }

    @Test
    fun `deleteReceipt soft deletes synced receipts`() = runTest {
        coEvery { dao.findById("r1") } returns ReceiptEntity(id = "r1", status = "confirmed", imagePath = "receipt_r1.jpg")
        coEvery { dao.itemsForReceipt("r1") } returns listOf(item("a"))
        repo.deleteReceipt("r1")
        coVerify {
            dao.upsertReceiptWithItems(
                match { it.deleted == 1 && it.dirty == 1 },
                match { items -> items.any { it.id == "a" && it.deleted == 1 && it.dirty == 1 } },
            )
        }
        coVerify(exactly = 0) { dao.hardDeleteReceiptWithItems(any()) }
    }
}
