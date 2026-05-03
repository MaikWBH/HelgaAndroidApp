package com.helga.android.data.remote

import com.helga.android.data.remote.dto.HealthResponse
import com.helga.android.data.remote.dto.ImageUploadResponse
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.remote.dto.SyncPushRequest
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
}
