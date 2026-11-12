package com.helga.android

import com.helga.android.data.local.dao.ReceiptAggregateDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.receipts.ReceiptImageStore
import com.helga.android.data.receipts.ReceiptSyncer
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.OkResponse
import com.helga.android.data.remote.dto.ReceiptAggregateDto
import com.helga.android.data.remote.dto.ReceiptAggregatesResponse
import com.helga.android.data.remote.dto.ReceiptListResponse
import com.helga.android.data.remote.dto.ReceiptPutResponse
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptRemoteItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class ReceiptSyncerTest {

    private val dao: ReceiptDao = mockk(relaxed = true)
    private val api: SyncApi = mockk()
    private val provider: ApiProvider = mockk()
    private val prefs: AppPreferences = mockk(relaxed = true)
    private val imageStore: ReceiptImageStore = mockk(relaxed = true)
    private val aggregateDao: ReceiptAggregateDao = mockk(relaxed = true)
    private val syncer = ReceiptSyncer(dao, provider, prefs, imageStore, aggregateDao)
    private val today = LocalDate.of(2026, 11, 10)

    private fun remote(id: String, updatedAt: Long = 100, deleted: Int = 0, items: List<String> = listOf("i1")) =
        ReceiptRemoteDto(
            id = id, storeName = "REWE", updatedAt = updatedAt, deleted = deleted, serverSeq = 9,
            items = items.map { ReceiptRemoteItemDto(id = it, receiptId = id, updatedAt = updatedAt) },
        )

    private fun response(vararg receipts: ReceiptRemoteDto, seq: Long = 42) =
        ReceiptListResponse(serverSeq = seq, receipts = receipts.toList())

    @Before
    fun setUp() {
        coEvery { provider.api() } returns api
        every { prefs.receiptRetentionMonths } returns flowOf(3)
        every { prefs.receiptSyncSeq } returns flowOf(7L)
        every { prefs.receiptSyncFromDay } returns flowOf("2026-08-10")
        coEvery { dao.findById(any()) } returns null
    }

    @Test
    fun `pull creates new receipts replaces items and saves the cursor`() = runTest {
        coEvery { api.listReceipts(any(), any(), any(), any()) } returns response(remote("a", items = listOf("i1", "i2")))
        assertEquals(1, syncer.pull(today))
        coVerify { api.listReceipts("2026-08-10", null, 7L, 1) }
        coVerify { dao.upsertReceipt(match { it.id == "a" && it.dirty == 0 }) }
        coVerify { dao.upsertItems(match { it.map { i -> i.id } == listOf("i1", "i2") }) }
        coVerify { dao.hardDeleteItemsExcept("a", listOf("i1", "i2")) }
        coVerify { prefs.saveReceiptSyncState(42L, "2026-08-10") }
    }

    @Test
    fun `pull skips receipts with newer local changes and pending uploads`() = runTest {
        coEvery { dao.findById("dirty") } returns ReceiptEntity(id = "dirty", dirty = 1, updatedAt = 500)
        coEvery { dao.findById("pending") } returns ReceiptEntity(id = "pending", pendingUpload = 1, updatedAt = 1)
        coEvery { api.listReceipts(any(), any(), any(), any()) } returns response(remote("dirty", 100), remote("pending", 100))
        assertEquals(0, syncer.pull(today))
        coVerify(exactly = 0) { dao.upsertReceipt(any()) }
    }

    @Test
    fun `server wins over an older dirty local receipt`() = runTest {
        coEvery { dao.findById("a") } returns ReceiptEntity(id = "a", dirty = 1, updatedAt = 50)
        coEvery { api.listReceipts(any(), any(), any(), any()) } returns response(remote("a", 100))
        assertEquals(1, syncer.pull(today))
        coVerify { dao.upsertReceipt(match { it.id == "a" && it.dirty == 0 }) }
    }

    @Test
    fun `pull removes server side deleted receipts unless local changes are newer`() = runTest {
        coEvery { dao.findById("keep") } returns ReceiptEntity(id = "keep", dirty = 1, updatedAt = 500)
        coEvery { api.listReceipts(any(), any(), any(), any()) } returns
            response(remote("gone", deleted = 1), remote("keep", deleted = 1, updatedAt = 100))
        assertEquals(1, syncer.pull(today))
        coVerify { dao.hardDeleteItemsOf("gone") }
        coVerify { dao.hardDeleteReceipt("gone") }
        coVerify(exactly = 0) { dao.hardDeleteReceipt("keep") }
    }

    @Test
    fun `widened window restarts from zero`() = runTest {
        every { prefs.receiptRetentionMonths } returns flowOf(6) // Fenster ab 2026-05-10, gespeichert 2026-08-10
        coEvery { api.listReceipts(any(), any(), any(), any()) } returns response()
        syncer.pull(today)
        coVerify { api.listReceipts("2026-05-10", null, 0L, 1) }
        coVerify { prefs.saveReceiptSyncState(42L, "2026-05-10") }
    }

    @Test
    fun `never delete means everything and is saved as empty text`() = runTest {
        every { prefs.receiptRetentionMonths } returns flowOf(0)
        coEvery { api.listReceipts(any(), any(), any(), any()) } returns response()
        syncer.pull(today)
        coVerify { api.listReceipts(null, null, 0L, 1) }
        coVerify { prefs.saveReceiptSyncState(42L, "") }
    }

    @Test
    fun `pushDirty puts changed receipts and writes the server answer`() = runTest {
        val receipt = ReceiptEntity(id = "a", dirty = 1, updatedAt = 10, storeName = "REWE")
        coEvery { dao.dirtyReceiptsForPush() } returns listOf(receipt)
        coEvery { dao.itemsForReceipt("a") } returns listOf(ReceiptItemEntity(id = "i1", receiptId = "a"))
        val body = slot<com.helga.android.data.remote.dto.ReceiptUpsertDto>()
        coEvery { api.putReceipt("a", capture(body)) } returns ReceiptPutResponse(applied = true, receipt = remote("a"))
        assertEquals(1, syncer.pushDirty())
        assertEquals(listOf("i1"), body.captured.items.map { it.id })
        coVerify { dao.upsertReceipt(match { it.id == "a" && it.dirty == 0 }) }
    }

    @Test
    fun `pushDirty deletes on the server and removes the local rows hard`() = runTest {
        coEvery { dao.dirtyReceiptsForPush() } returns listOf(ReceiptEntity(id = "x", deleted = 1, dirty = 1, updatedAt = 77))
        coEvery { api.deleteReceipt("x", 77L) } returns OkResponse(true)
        assertEquals(1, syncer.pushDirty())
        coVerify { dao.hardDeleteItemsOf("x") }
        coVerify { dao.hardDeleteReceipt("x") }
    }

    @Test
    fun `evict does nothing for never delete and removes old receipts otherwise`() = runTest {
        every { prefs.receiptRetentionMonths } returns flowOf(0)
        assertEquals(0, syncer.evict(today, 1L))
        coVerify(exactly = 0) { dao.evictableIds(any(), any()) }

        every { prefs.receiptRetentionMonths } returns flowOf(3)
        coEvery { dao.evictableIds(any(), any()) } returns listOf("old1", "old2")
        assertEquals(2, syncer.evict(today, 1L))
        coVerify { dao.evictableIds(1_786_320_000_000L, 1L) } // 2026-08-10 00:00 UTC
        coVerify { dao.hardDeleteReceipt("old1") }
        coVerify { dao.hardDeleteReceipt("old2") }
    }

    @Test
    fun `loadRange keeps loaded receipts in the cache for the given time`() = runTest {
        coEvery { api.listReceipts("2026-07-01", "2026-07-31", 0L, 0) } returns response(remote("a"))
        assertEquals(1, syncer.loadRange("2026-07-01", "2026-07-31", 5_000L))
        coVerify { dao.upsertReceipt(match { it.id == "a" && it.cachedUntil == 5_000L }) }
    }

    @Test
    fun `refreshAggregates replaces the local copy with the server rows`() = runTest {
        coEvery { api.receiptAggregates() } returns ReceiptAggregatesResponse(
            generatedAt = 1,
            rows = listOf(
                ReceiptAggregateDto(period = "2026-11", dimension = "total", amount = 12.5, count = 2),
                ReceiptAggregateDto(period = "2026-11", dimension = "store", key = "name:rewe", label = "REWE", amount = 12.5),
            ),
        )
        assertEquals(true, syncer.refreshAggregates())
        coVerify {
            aggregateDao.replaceAll(match { rows ->
                rows.map { it.id } == listOf("total|2026-11|", "store|2026-11|name:rewe") && rows[0].amount == 12.5
            })
        }
    }

    @Test
    fun `refreshAggregates keeps the old copy when the server is unreachable`() = runTest {
        coEvery { api.receiptAggregates() } throws IOException("offline")
        assertEquals(false, syncer.refreshAggregates())
        coVerify(exactly = 0) { aggregateDao.replaceAll(any()) }
    }
}
