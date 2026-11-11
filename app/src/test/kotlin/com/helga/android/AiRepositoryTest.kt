package com.helga.android

import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiClassifyResponse
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.remote.dto.AiNutritionResponse
import com.helga.android.data.repository.AiRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
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

    @Test
    fun klassifikation_fehler_wird_weitergereicht() = runTest {
        coEvery { api.classifyRecipe(classifyReq) } throws IOException("offline")

        val thrown = try {
            repo.classifyRecipe(classifyReq)
            null
        } catch (e: IOException) {
            e
        }
        assertNotNull(thrown)
    }

    @Test
    fun naehrwerte_reicht_request_und_antwort_durch() = runTest {
        val response = mockk<AiNutritionResponse>()
        coEvery { api.estimateNutrition(nutritionReq) } returns response

        assertSame(response, repo.estimateNutrition(nutritionReq))
    }
}
