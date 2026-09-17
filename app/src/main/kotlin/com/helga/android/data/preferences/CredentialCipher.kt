package com.helga.android.data.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verschlüsselt Server-URL und API-Schlüssel, bevor sie im DataStore landen.
 *
 * Der Schlüssel liegt im Android-Keystore und verlässt das Gerät nicht. Bewusst **ohne**
 * `setUserAuthenticationRequired` — sonst würde eine Änderung der Bildschirmsperre den Schlüssel
 * ungültig machen und die App könnte ihre eigenen Zugangsdaten nicht mehr lesen.
 *
 * Format: `v1:<base64 iv>:<base64 ciphertext>`. Das Präfix erlaubt es, Altbestände im Klartext
 * zu erkennen und weiterzuverwenden, statt bestehende Installationen abzumelden.
 */
@Singleton
class CredentialCipher @Inject constructor() {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + encode(cipher.iv) + SEPARATOR + encode(ciphertext)
    }

    /**
     * Entschlüsselt einen zuvor mit [encrypt] erzeugten Wert. Gibt `null` zurück, wenn der Wert
     * nicht entschlüsselt werden kann — etwa weil der Keystore-Schlüssel nicht mehr existiert.
     * Der Aufrufer behandelt das wie „nicht eingerichtet"; ein neues Pairing per QR-Code stellt
     * die Verbindung dann in Sekunden wieder her.
     */
    fun decrypt(stored: String): String? = runCatching {
        val parts = stored.removePrefix(PREFIX).split(SEPARATOR)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "helga_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val PREFIX = "v1:"
        private const val SEPARATOR = ":"

        /**
         * Ob ein gespeicherter Wert im verschlüsselten Format vorliegt. Alles andere stammt aus
         * einer Installation von vor der Verschlüsselung und wird als Klartext gelesen.
         */
        fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)
    }
}
