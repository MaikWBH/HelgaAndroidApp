package com.helga.android

import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.OffLookupBarcodeRequest
import com.helga.android.data.remote.dto.SuggestionsResponse
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.StoreRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ShoppingSuggestionsTest {

    private val api: SyncApi = mockk()
    private val provider: ApiProvider = mockk()
    private val repo = ShoppingRepository(
        shoppingDao = mockk<ShoppingDao>(relaxed = true),
        storeRepository = mockk<StoreRepository>(relaxed = true),
        receiptRepository = mockk<ReceiptRepository>(relaxed = true),
        quickEmojiDao = mockk<QuickEmojiDao>(relaxed = true),
        apiProvider = provider,
        aisleAssigner = mockk(relaxed = true),
        syncScheduler = mockk(relaxed = true),
    )

    init {
        coEvery { provider.api() } returns api
    }

    @Test
    fun erfolg_liefert_servervorschlaege() = runTest {
        val response = mockk<SuggestionsResponse> { every { suggestions } returns listOf("Milch", "Mehl") }
        coEvery { api.suggestItems("mi") } returns response

        assertEquals(listOf("Milch", "Mehl"), repo.suggestItems("mi"))
    }

    @Test
    fun timeout_liefert_leere_liste() = runTest {
        coEvery { api.suggestItems(any()) } coAnswers {
            delay(5_000)
            mockk<SuggestionsResponse>()
        }

        assertEquals(emptyList<String>(), repo.suggestItems("mi"))
    }

    @Test
    fun fehler_liefert_leere_liste() = runTest {
        coEvery { api.suggestItems(any()) } throws IOException("offline")

        assertEquals(emptyList<String>(), repo.suggestItems("mi"))
    }

    @Test
    fun zu_kurze_anfrage_ruft_server_nicht_auf() = runTest {
        assertEquals(emptyList<String>(), repo.suggestItems("m"))
        coVerify(exactly = 0) { api.suggestItems(any()) }
    }

    @Test
    fun barcode_wird_an_server_weitergereicht() = runTest {
        val req = OffLookupBarcodeRequest("4000417025005")
        val response = mockk<com.helga.android.data.remote.dto.OffLookupBarcodeResponse>()
        coEvery { api.lookupBarcode(req) } returns response

        assertEquals(response, repo.lookupBarcode(req))
    }
}
