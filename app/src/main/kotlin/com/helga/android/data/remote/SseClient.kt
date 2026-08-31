package com.helga.android.data.remote

import com.helga.android.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val preferences: AppPreferences,
) {
    suspend fun collect(endpoint: String, bodyJson: String): String = withContext(Dispatchers.IO) {
        val conn = preferences.currentConnection()
        require(conn.isConfigured) { "Server nicht konfiguriert" }
        val baseUrl = conn.serverUrl.trimEnd('/') + "/"
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .addHeader("X-Api-Key", conn.apiKey)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        val sb = StringBuilder()
        var streamCompleted = false
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val source = response.body?.source() ?: throw IOException("Leere Antwort")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") {
                        streamCompleted = true
                        break
                    }
                    sb.append(data.replace("\\n", "\n"))
                }
            }
        }
        // Verbindung kann vor [DONE] abreißen (Server-Timeout, Netzwerkabbruch) — ohne diese
        // Prüfung würde der Aufrufer ein stillschweigend abgeschnittenes Ergebnis erhalten.
        if (!streamCompleted) throw IOException("Stream wurde vorzeitig beendet")
        sb.toString()
    }
}
