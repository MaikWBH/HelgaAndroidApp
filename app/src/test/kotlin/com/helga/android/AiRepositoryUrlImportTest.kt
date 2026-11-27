package com.helga.android

import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.ImportedRecipeDto
import com.helga.android.data.remote.dto.UrlImportRequest
import com.helga.android.data.repository.AiRepository
import com.helga.android.data.repository.ImportOutcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AiRepositoryUrlImportTest {

    private val api: SyncApi = mockk()
    private val provider: ApiProvider = mockk()
    private val repo = AiRepository(provider)

    init {
        coEvery { provider.api() } returns api
    }

    @Test
    fun erfolg_liefert_rezept() = runTest {
        val dto = mockk<ImportedRecipeDto>()
        coEvery { api.importFromUrl(UrlImportRequest("https://x")) } returns dto

        val outcome = repo.importFromUrl("https://x")

        assertSame(dto, (outcome as ImportOutcome.Success).recipe)
    }

    @Test
    fun http_fehler_nutzt_detail_aus_errorbody() = runTest {
        val body = """{"detail":"Keine Rezeptdaten gefunden"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.importFromUrl(any()) } throws HttpException(Response.error<Any>(422, body))

        val outcome = repo.importFromUrl("https://x")

        assertEquals(ImportOutcome.Error("Keine Rezeptdaten gefunden"), outcome)
    }

    @Test
    fun http_fehler_ohne_detail_nutzt_statuscode() = runTest {
        val body = "<html>kaputt</html>".toResponseBody("text/html".toMediaType())
        coEvery { api.importFromUrl(any()) } throws HttpException(Response.error<Any>(500, body))

        assertEquals(ImportOutcome.Error("Import fehlgeschlagen (Fehler 500)"), repo.importFromUrl("https://x"))
    }

    @Test
    fun io_fehler_liefert_server_nicht_erreichbar() = runTest {
        coEvery { api.importFromUrl(any()) } throws IOException("offline")

        assertEquals(ImportOutcome.Error("Server nicht erreichbar"), repo.importFromUrl("https://x"))
    }
}
