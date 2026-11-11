package com.helga.android

import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.ConnectionResult
import com.helga.android.data.remote.ServerConnectionChecker
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.HealthResponse
import com.helga.android.data.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ServerConnectionCheckerTest {

    private val api: SyncApi = mockk()
    private val provider: ApiProvider = mockk { every { apiForOnboarding(any(), any()) } returns api }
    private val preferences: AppPreferences = mockk(relaxed = true)
    private val scheduler: SyncScheduler = mockk(relaxed = true)
    private val checker = ServerConnectionChecker(provider, preferences, scheduler)

    @Test
    fun erfolg_speichert_verbindung_und_startet_sync() = runTest {
        coEvery { api.health() } returns mockk<HealthResponse>(relaxed = true)

        val result = checker.test("http://h:8000", "key")

        assertEquals(ConnectionResult.Success, result)
        coVerify { preferences.saveConnection("http://h:8000", "key") }
        verify { scheduler.schedulePeriodic() }
        verify { scheduler.triggerOneShot() }
    }

    @Test
    fun http_401_liefert_code_und_detail_ohne_speichern() = runTest {
        val body = """{"detail":"Ungültiger Key"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.health() } throws HttpException(Response.error<Any>(401, body))

        val result = checker.test("http://h:8000", "bad")

        assertEquals(ConnectionResult.HttpError(401, "Ungültiger Key"), result)
        coVerify(exactly = 0) { preferences.saveConnection(any(), any()) }
        verify(exactly = 0) { scheduler.triggerOneShot() }
    }

    @Test
    fun io_fehler_liefert_unreachable() = runTest {
        coEvery { api.health() } throws IOException("offline")

        assertEquals(ConnectionResult.Unreachable, checker.test("http://h:8000", "key"))
        coVerify(exactly = 0) { preferences.saveConnection(any(), any()) }
    }
}
