package com.helga.android

import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.model.OlderReceiptsResult
import com.helga.android.data.receipts.ReceiptImageStore
import com.helga.android.data.receipts.ReceiptSyncer
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

class ReceiptRepositoryOlderTest {

    private val dao: ReceiptDao = mockk(relaxed = true)
    private val syncer: ReceiptSyncer = mockk()
    private val repo = ReceiptRepository(
        dao, mockk<ReceiptScanner>(), mockk<MonthlyBudgetDao>(), mockk<ShoppingDao>(),
        mockk<ApiProvider>(), mockk<ReceiptImageStore>(relaxed = true), mockk<SyncScheduler>(), syncer,
    )
    private val today = LocalDate.of(2026, 11, 10)
    private val nowMs = 1_794_000_000_000L

    init {
        coEvery { dao.oldestPurchaseDay() } returns null
    }

    @Test
    fun `loads the first older month that has receipts`() = runTest {
        coEvery { syncer.loadRange(any(), any(), any()) } returnsMany listOf(0, 0, 2)
        val result = repo.loadOlderMonth(today, nowMs)
        assertEquals(OlderReceiptsResult.Loaded(2, YearMonth.of(2026, 8)), result)
        coVerify(exactly = 3) { syncer.loadRange(any(), any(), any()) }
    }

    @Test
    fun `nothing found after twelve months`() = runTest {
        coEvery { syncer.loadRange(any(), any(), any()) } returns 0
        val result = repo.loadOlderMonth(today, nowMs)
        assertEquals(OlderReceiptsResult.NothingFound(YearMonth.of(2026, 11)), result)
        coVerify(exactly = 12) { syncer.loadRange(any(), any(), any()) }
    }

    @Test
    fun `network error returns failed`() = runTest {
        coEvery { syncer.loadRange(any(), any(), any()) } throws IOException("offline")
        assertEquals(OlderReceiptsResult.Failed, repo.loadOlderMonth(today, nowMs))
    }

    @Test
    fun `cursor older than a day is ignored`() = runTest {
        coEvery { syncer.loadRange(any(), any(), any()) } returns 0
        repo.loadOlderMonth(today, nowMs)
        coEvery { syncer.loadRange(any(), any(), any()) } returns 1
        val later = nowMs + 25 * 60 * 60 * 1000L
        val result = repo.loadOlderMonth(today, later)
        assertEquals(OlderReceiptsResult.Loaded(1, YearMonth.of(2026, 10)), result)
    }
}
