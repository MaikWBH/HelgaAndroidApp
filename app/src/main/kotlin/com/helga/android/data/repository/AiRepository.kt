package com.helga.android.data.repository

import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiClassifyResponse
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.remote.dto.AiNutritionResponse
import javax.inject.Inject
import javax.inject.Singleton

/** Kapselt die KI-Aufrufe des Sync-Servers (Klassifikation, Nährwertschätzung). */
@Singleton
class AiRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {
    suspend fun classifyRecipe(req: AiClassifyRequest): AiClassifyResponse =
        apiProvider.api().classifyRecipe(req)

    suspend fun estimateNutrition(req: AiNutritionRequest): AiNutritionResponse =
        apiProvider.api().estimateNutrition(req)
}
