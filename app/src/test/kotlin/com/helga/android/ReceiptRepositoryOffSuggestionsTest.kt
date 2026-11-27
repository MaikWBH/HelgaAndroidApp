package com.helga.android

import com.helga.android.data.local.ReceiptScanner
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.receipts.ReceiptImageStore
import com.helga.android.data.receipts.ReceiptSyncer
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.OffProductDto
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** `suggestOffProducts` schluckt Netzfehler, gibt aber Abbrüche der Coroutine weiter (Phase 6, S8). */
class ReceiptRepositoryOffSuggestionsTest {

    private val provider: ApiProvider = mockk()
    private val api: SyncApi = mockk()
    private val repo = ReceiptRepository(
        mockk<ReceiptDao>(relaxed = true), mockk<ReceiptScanner>(), mockk<MonthlyBudgetDao>(), mockk<ShoppingDao>(),
        provider, mockk<ReceiptImageStore>(relaxed = true), mockk<SyncScheduler>(), mockk<ReceiptSyncer>(),
    )

    init {
        coEvery { provider.api() } returns api
    }

    @Test
    fun `Abbruch der Coroutine wird weitergeworfen`() {
        coEvery { api.searchOff(any()) } throws CancellationException("abgebrochen")
        assertThrows(CancellationException::class.java) {
            runBlocking { repo.suggestOffProducts("Milch") }
        }
    }

    @Test
    fun `Netzfehler ergeben eine leere Liste`() = runTest {
        coEvery { api.searchOff(any()) } throws IOException("offline")
        assertEquals(emptyList<OffProductDto>(), repo.suggestOffProducts("Milch"))
    }
}
