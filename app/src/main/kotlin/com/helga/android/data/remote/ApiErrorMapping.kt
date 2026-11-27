package com.helga.android.data.remote

import org.json.JSONObject

/** Extrahiert das `detail`-Feld aus einer FastAPI-Fehlerantwort, z. B. `{"detail": "..."}`. */
fun extractErrorDetail(body: String): String? = try {
    JSONObject(body).optString("detail").ifBlank { null }
} catch (_: Exception) {
    null
}
