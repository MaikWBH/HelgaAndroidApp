package com.helga.android

import com.helga.android.data.preferences.CredentialCipher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das eigentliche Ver- und Entschlüsseln hängt am Android-Keystore und ist deshalb nicht im
 * JVM-Unit-Test prüfbar. Testbar — und der kritische Teil — ist die Unterscheidung zwischen
 * verschlüsselten Werten und Klartext-Altbeständen: Wird ein Altbestand fälschlich für
 * verschlüsselt gehalten, scheitert die Entschlüsselung und die Installation gilt als
 * abgemeldet. Genau das soll ein App-Update niemals auslösen.
 */
class CredentialFormatTest {

    @Test
    fun `ein Wert im v1-Format gilt als verschluesselt`() {
        assertTrue(CredentialCipher.isEncrypted("v1:aXY=:Y2lwaGVy"))
    }

    @Test
    fun `eine gespeicherte Server-URL aus einer alten Installation gilt als Klartext`() {
        assertFalse(CredentialCipher.isEncrypted("http://192.168.178.68:8000"))
    }

    @Test
    fun `ein API-Schluessel aus einer alten Installation gilt als Klartext`() {
        assertFalse(CredentialCipher.isEncrypted("9f2c1b7e4a6d8f0c3e5b7a9d1f3c5e7b"))
    }

    @Test
    fun `ein leerer Wert gilt nicht als verschluesselt`() {
        assertFalse(CredentialCipher.isEncrypted(""))
    }
}
