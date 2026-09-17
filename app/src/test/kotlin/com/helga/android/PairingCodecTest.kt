package com.helga.android

import com.helga.android.data.pairing.PairingCodec
import com.helga.android.data.pairing.PairingPayload
import com.helga.android.data.pairing.PairingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodecTest {

    private fun decodeSuccess(raw: String): PairingPayload {
        val result = PairingCodec.decode(raw)
        assertTrue("Erwartet Success, war $result", result is PairingResult.Success)
        return (result as PairingResult.Success).payload
    }

    @Test
    fun `Kodieren und Dekodieren ist verlustfrei`() {
        val payload = PairingPayload("http://192.168.178.68:8000", "a1b2c3d4e5")
        assertEquals(payload, decodeSuccess(PairingCodec.encode(payload)))
    }

    @Test
    fun `Sonderzeichen im Schluessel ueberstehen den Roundtrip`() {
        // Ein Key aus `openssl rand -base64` kann +, / und = enthalten — genau die Zeichen, die
        // in einer URI eine Bedeutung haben.
        val payload = PairingPayload("https://helga.local", "a+b/c=d&e=f g")
        assertEquals(payload, decodeSuccess(PairingCodec.encode(payload)))
    }

    @Test
    fun `URL mit Port und Pfad bleibt erhalten`() {
        val payload = PairingPayload("https://example.org:8443/helga/api", "key")
        assertEquals(payload, decodeSuccess(PairingCodec.encode(payload)))
    }

    @Test
    fun `Werte werden getrimmt`() {
        val payload = PairingCodec.encode(PairingPayload("  http://a.b  ", "  key  "))
        assertEquals(PairingPayload("http://a.b", "key"), decodeSuccess(payload))
    }

    @Test
    fun `ein Produkt-Barcode ist kein Helga-Code`() {
        assertEquals(PairingResult.NotHelgaCode, PairingCodec.decode("4006381333931"))
    }

    @Test
    fun `eine fremde URL ist kein Helga-Code`() {
        assertEquals(PairingResult.NotHelgaCode, PairingCodec.decode("https://example.org/login"))
    }

    @Test
    fun `eine neuere Version wird als solche gemeldet`() {
        assertEquals(
            PairingResult.UnsupportedVersion,
            PairingCodec.decode("helga://pair?v=2&url=http%3A%2F%2Fa.b&key=k"),
        )
    }

    @Test
    fun `fehlende Version ist ungueltig`() {
        assertEquals(
            PairingResult.Malformed,
            PairingCodec.decode("helga://pair?url=http%3A%2F%2Fa.b&key=k"),
        )
    }

    @Test
    fun `fehlender Schluessel ist ungueltig`() {
        assertEquals(
            PairingResult.Malformed,
            PairingCodec.decode("helga://pair?v=1&url=http%3A%2F%2Fa.b"),
        )
    }

    @Test
    fun `eine URL ohne http-Schema ist ungueltig`() {
        assertEquals(
            PairingResult.Malformed,
            PairingCodec.decode("helga://pair?v=1&url=ftp%3A%2F%2Fa.b&key=k"),
        )
    }

    @Test
    fun `ein leerer Pairing-Code ist ungueltig`() {
        assertEquals(PairingResult.Malformed, PairingCodec.decode("helga://pair?"))
    }
}
