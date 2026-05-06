package com.helga.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für URL-Validierungslogik aus dem Settings-Onboarding.
 * Spiegelt die Bedingung `url.isNotBlank() && url.startsWith("http")` wider.
 */
class SettingsValidationTest {

    private fun isValidServerUrl(url: String): Boolean =
        url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))

    @Test fun `http url is valid`() = assertTrue(isValidServerUrl("http://192.168.1.10:8000"))
    @Test fun `https url is valid`() = assertTrue(isValidServerUrl("https://helga.example.com"))
    @Test fun `empty url is invalid`() = assertFalse(isValidServerUrl(""))
    @Test fun `blank url is invalid`() = assertFalse(isValidServerUrl("   "))
    @Test fun `url without scheme is invalid`() = assertFalse(isValidServerUrl("192.168.1.10:8000"))
    @Test fun `ftp url is invalid`() = assertFalse(isValidServerUrl("ftp://example.com"))
    @Test fun `url with trailing slash is valid`() = assertTrue(isValidServerUrl("http://192.168.1.10:8000/"))
}
