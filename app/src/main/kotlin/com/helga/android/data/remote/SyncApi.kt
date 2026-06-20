package com.helga.android.data.remote

import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiClassifyResponse
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.remote.dto.AiNutritionResponse
import com.helga.android.data.remote.dto.HealthResponse
import com.helga.android.data.remote.dto.ImageUploadResponse
import com.helga.android.data.remote.dto.ImportedRecipeDto
import com.helga.android.data.remote.dto.OffLookupBarcodeRequest
import com.helga.android.data.remote.dto.OffLookupBarcodeResponse
import com.helga.android.data.remote.dto.ReceiptParseRequest
import com.helga.android.data.remote.dto.ReceiptParseResponse
import com.helga.android.data.remote.dto.ReceiptReconcileRequest
import com.helga.android.data.remote.dto.ReceiptReconcileResponse
import com.helga.android.data.remote.dto.SuggestionsResponse
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.remote.dto.SyncPushRequest
import com.helga.android.data.remote.dto.UrlImportRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface SyncApi {

    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/sync")
    suspend fun pull(@Query("since") since: Long): SyncPullResponse

    @POST("api/sync")
    suspend fun push(@Body payload: SyncPushRequest): SyncPullResponse

    @Multipart
    @POST("api/images/upload")
    suspend fun uploadImage(@Part image: MultipartBody.Part): ImageUploadResponse

    @POST("api/ai/import-url")
    suspend fun importFromUrl(@Body req: UrlImportRequest): ImportedRecipeDto

    @POST("api/ai/classify")
    suspend fun classifyRecipe(@Body req: AiClassifyRequest): AiClassifyResponse

    @POST("api/ai/nutrition")
    suspend fun estimateNutrition(@Body req: AiNutritionRequest): AiNutritionResponse

    @GET("api/suggestions/items")
    suspend fun suggestItems(@Query("q") q: String): SuggestionsResponse

    @POST("api/off/lookup-barcode")
    suspend fun lookupBarcode(@Body req: OffLookupBarcodeRequest): OffLookupBarcodeResponse

    @POST("api/receipts/reconcile")
    suspend fun reconcileReceipt(@Body req: ReceiptReconcileRequest): ReceiptReconcileResponse

    @POST("api/ai/parse-receipt")
    suspend fun parseReceipt(@Body req: ReceiptParseRequest): ReceiptParseResponse
}
