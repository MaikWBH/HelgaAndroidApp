package com.helga.android.data.pairing

import java.net.URLDecoder
import java.net.URLEncoder

/** Server-URL und API-Key, wie sie ein bereits eingerichtetes Gerät per QR-Code weitergibt. */
data class PairingPayload(val serverUrl: String, val apiKey: String)

sealed interface PairingResult {
    data class Success(val payload: PairingPayload) : PairingResult

    /** Ein fremder Code — etwa ein Produkt-Barcode aus dem Kassenzettel-Scanner. */
    data object NotHelgaCode : PairingResult

    /** Helga-Code, aber aus einer neueren App-Version. */
    data object UnsupportedVersion : PairingResult

    /** Helga-Code, dem Pflichtangaben fehlen oder deren Werte unbrauchbar sind. */
    data object Malformed : PairingResult
}

/**
 * Kodiert die Zugangsdaten für den Pairing-QR-Code und liest sie wieder aus.
 *
 * Bewusst eine URI statt JSON: kürzer, damit der QR-Code eine niedrigere Version bekommt und
 * auch bei schlechtem Licht zuverlässig erkannt wird. Das eigene Schema grenzt außerdem fremde
 * Codes sauber ab, statt sie als kaputte Zugangsdaten zu interpretieren.
 *
 * Geparst wird ohne `android.net.Uri`, damit die Logik ohne Robolectric im JVM-Unit-Test läuft.
 */
object PairingCodec {

    const val VERSION = 1

    private const val PREFIX = "helga://pair?"
    private const val CHARSET = "UTF-8"

    fun encode(payload: PairingPayload): String =
        PREFIX + "v=$VERSION" +
            "&url=${URLEncoder.encode(payload.serverUrl, CHARSET)}" +
            "&key=${URLEncoder.encode(payload.apiKey, CHARSET)}"

    fun decode(raw: String): PairingResult {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX)) return PairingResult.NotHelgaCode

        val params = mutableMapOf<String, String>()
        trimmed.removePrefix(PREFIX)
            .split('&')
            .filter { it.isNotEmpty() }
            .forEach { pair ->
                // Nur am ersten '=' trennen — der Wert selbst ist prozentkodiert und kann
                // seinerseits '=' enthalten.
                val separator = pair.indexOf('=')
                if (separator <= 0) return@forEach
                val name = pair.substring(0, separator)
                val value = runCatching {
                    URLDecoder.decode(pair.substring(separator + 1), CHARSET)
                }.getOrNull() ?: return PairingResult.Malformed
                params[name] = value
            }

        val version = params["v"]?.toIntOrNull() ?: return PairingResult.Malformed
        if (version != VERSION) return PairingResult.UnsupportedVersion

        val url = params["url"].orEmpty().trim()
        val key = params["key"].orEmpty().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return PairingResult.Malformed
        if (key.isEmpty()) return PairingResult.Malformed

        return PairingResult.Success(PairingPayload(serverUrl = url, apiKey = key))
    }
}
