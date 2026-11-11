package com.helga.android.data.remote

import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiClassifyResponse
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.remote.dto.AiNutritionResponse
import com.helga.android.data.remote.dto.HealthResponse
import com.helga.android.data.remote.dto.ImageUploadResponse
import com.helga.android.data.remote.dto.ImportedRecipeDto
import com.helga.android.data.remote.dto.LexiconListResponse
import com.helga.android.data.remote.dto.OffLookupBarcodeRequest
import com.helga.android.data.remote.dto.OffLookupBarcodeResponse
import com.helga.android.data.remote.dto.OffSearchRequest
import com.helga.android.data.remote.dto.OffSearchResponse
import com.helga.android.data.remote.dto.OkResponse
import com.helga.android.data.remote.dto.ReceiptImageResponse
import com.helga.android.data.remote.dto.ReceiptListResponse
import com.helga.android.data.remote.dto.ReceiptParseRequest
import com.helga.android.data.remote.dto.ReceiptParseResponse
import com.helga.android.data.remote.dto.ReceiptPutResponse
import com.helga.android.data.remote.dto.ReceiptReconcileRequest
import com.helga.android.data.remote.dto.ReceiptReconcileResponse
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptScanResponse
import com.helga.android.data.remote.dto.ReceiptUpsertDto
import com.helga.android.data.remote.dto.SuggestionsResponse
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.remote.dto.SyncPushRequest
import com.helga.android.data.remote.dto.UrlImportRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
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

    // ── Bon-Archiv (Phase 3a): bon-weises Protokoll, siehe server-vertrag.md ─────────────

    @GET("api/receipts")
    suspend fun listReceipts(
        @Query("from_day") fromDay: String?,
        @Query("to_day") toDay: String?,
        @Query("since_seq") sinceSeq: Long,
        @Query("include_deleted") includeDeleted: Int,
    ): ReceiptListResponse

    @GET("api/receipts/{id}")
    suspend fun getReceipt(@Path("id") id: String): ReceiptRemoteDto

    @PUT("api/receipts/{id}")
    suspend fun putReceipt(@Path("id") id: String, @Body body: ReceiptUpsertDto): ReceiptPutResponse

    @DELETE("api/receipts/{id}")
    suspend fun deleteReceipt(@Path("id") id: String, @Query("updated_at") updatedAt: Long): OkResponse

    @Multipart
    @POST("api/receipts/scan")
    suspend fun scanReceipt(
        @Part file: MultipartBody.Part,
        @Part("receipt_id") receiptId: RequestBody,
        @Part("shopping_list_id") shoppingListId: RequestBody,
        @Part("captured_at") capturedAt: RequestBody,
        @Part("preliminary") preliminary: RequestBody?,
    ): ReceiptScanResponse

    @Multipart
    @POST("api/receipts/{id}/image")
    suspend fun uploadReceiptImage(@Path("id") id: String, @Part file: MultipartBody.Part): ReceiptImageResponse

    @POST("api/receipts/{id}/reparse")
    suspend fun reparseReceipt(@Path("id") id: String): ReceiptScanResponse

    @GET("api/receipts/lexicon")
    suspend fun receiptLexicon(@Query("store_key") storeKey: String?): LexiconListResponse

    @DELETE("api/receipts/lexicon/{id}")
    suspend fun deleteLexiconEntry(@Path("id") id: String): OkResponse

    @POST("api/off/search")
    suspend fun searchOff(@Body req: OffSearchRequest): OffSearchResponse
}
