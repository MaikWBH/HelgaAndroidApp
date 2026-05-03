package com.helga.android.data.remote

import com.helga.android.data.preferences.AppPreferences
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stellt eine [SyncApi] basierend auf der aktuellen Server-Konfiguration bereit.
 *
 * Server-URL und API-Key kommen aus [AppPreferences] und können sich nach dem
 * Onboarding ändern. Der OkHttp-Client (mit Connection-Pool) ist Singleton; nur
 * Retrofit + Header-Interceptor werden bei URL-/Key-Wechsel neu gebaut.
 */
@Singleton
class SyncApiFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val preferences: AppPreferences,
) {
    @Volatile private var cached: Cached? = null

    suspend fun api(): SyncApi {
        val conn = preferences.currentConnection()
        require(conn.isConfigured) { "Helga-Server nicht konfiguriert" }

        cached?.takeIf { it.serverUrl == conn.serverUrl && it.apiKey == conn.apiKey }
            ?.let { return it.api }

        val baseUrl = if (conn.serverUrl.endsWith("/")) conn.serverUrl else conn.serverUrl + "/"
        val client = httpClient.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("X-Api-Key", conn.apiKey)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SyncApi::class.java)

        cached = Cached(conn.serverUrl, conn.apiKey, api)
        return api
    }

    /**
     * Erzeugt eine einmalige API-Instanz für den Onboarding-Healthcheck –
     * unabhängig von gespeicherten Prefs.
     */
    fun apiForOnboarding(serverUrl: String, apiKey: String): SyncApi {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val client = httpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-Api-Key", apiKey)
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SyncApi::class.java)
    }

    private data class Cached(val serverUrl: String, val apiKey: String, val api: SyncApi)
}
