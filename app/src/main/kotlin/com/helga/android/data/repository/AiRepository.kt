package com.helga.android.data.repository

import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.extractErrorDetail
import com.helga.android.data.remote.dto.ImportedRecipeDto
import com.helga.android.data.remote.dto.UrlImportRequest
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiClassifyResponse
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.remote.dto.AiNutritionResponse
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines URL-Imports: Rezept oder eine anzeigbare Fehlermeldung. */
sealed interface ImportOutcome {
    data class Success(val recipe: ImportedRecipeDto) : ImportOutcome
    data class Error(val message: String) : ImportOutcome
}

/** Kapselt die KI-Aufrufe des Sync-Servers (Klassifikation, Nährwertschätzung). */
@Singleton
class AiRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {
    suspend fun classifyRecipe(req: AiClassifyRequest): AiClassifyResponse =
        apiProvider.api().classifyRecipe(req)

    suspend fun estimateNutrition(req: AiNutritionRequest): AiNutritionResponse =
        apiProvider.api().estimateNutrition(req)

    suspend fun importFromUrl(url: String): ImportOutcome = try {
        ImportOutcome.Success(apiProvider.api().importFromUrl(UrlImportRequest(url)))
    } catch (e: HttpException) {
        val detail = e.response()?.errorBody()?.string()?.let(::extractErrorDetail)
        ImportOutcome.Error(detail ?: "Import fehlgeschlagen (Fehler ${e.code()})")
    } catch (e: IOException) {
        ImportOutcome.Error("Server nicht erreichbar")
    } catch (e: Exception) {
        ImportOutcome.Error(e.message ?: "Unbekannter Fehler")
    }
}
