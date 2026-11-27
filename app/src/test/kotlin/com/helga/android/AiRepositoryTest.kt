package com.helga.android

import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiClassifyResponse
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.remote.dto.AiNutritionResponse
import com.helga.android.data.repository.AiRepository
import com.helga.android.data.repository.AiUnavailableException
import io.mockk.coEvery
import io.mockk.coVerify
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

class AiRepositoryTest {

    private val api: SyncApi = mockk()
    private val provider: ApiProvider = mockk()
    private val repo = AiRepository(provider)

    init {
        coEvery { provider.api() } returns api
    }

    private val classifyReq = AiClassifyRequest(
        name = "Gulasch", description = "", tags = listOf("deftig"), ingredients = listOf("Rindfleisch"),
    )
    private val nutritionReq = AiNutritionRequest(
        name = "Gulasch", description = "", ingredients = listOf("500 g Rindfleisch"), portions = 4,
    )

    @Test
    fun klassifikation_reicht_request_und_antwort_durch() = runTest {
        val response = mockk<AiClassifyResponse>()
        coEvery { api.classifyRecipe(classifyReq) } returns response

        assertSame(response, repo.classifyRecipe(classifyReq))
        coVerify(exactly = 1) { api.classifyRecipe(classifyReq) }
    }

    private suspend fun kindOf(block: suspend () -> Unit): AiUnavailableException.Kind? = try {
        block()
        null
    } catch (e: AiUnavailableException) {
        e.kind
    }

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun klassifikation_ohne_netz_ist_offline() = runTest {
        coEvery { api.classifyRecipe(classifyReq) } throws IOException("offline")
        assertEquals(AiUnavailableException.Kind.OFFLINE, kindOf { repo.classifyRecipe(classifyReq) })
    }

    @Test
    fun klassifikation_http_500_ist_server() = runTest {
        coEvery { api.classifyRecipe(classifyReq) } throws httpError(500)
        assertEquals(AiUnavailableException.Kind.SERVER, kindOf { repo.classifyRecipe(classifyReq) })
    }

    @Test
    fun naehrwerte_http_502_ist_ki_nicht_erreichbar() = runTest {
        coEvery { api.estimateNutrition(nutritionReq) } throws httpError(502)
        assertEquals(AiUnavailableException.Kind.AI_DOWN, kindOf { repo.estimateNutrition(nutritionReq) })
    }

    @Test
    fun naehrwerte_reicht_request_und_antwort_durch() = runTest {
        val response = mockk<AiNutritionResponse>()
        coEvery { api.estimateNutrition(nutritionReq) } returns response

        assertSame(response, repo.estimateNutrition(nutritionReq))
    }
}
