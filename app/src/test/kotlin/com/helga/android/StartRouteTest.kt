package com.helga.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert die Entscheidung über das Startziel ab. Der ursprüngliche Fehler — angemeldete Nutzer
 * landeten nach jedem Kaltstart wieder im Onboarding — entstand nicht in dieser Logik, sondern
 * dadurch, dass es sie gar nicht gab: das Startziel war fest das Onboarding und ein
 * `LaunchedEffect` sollte nachträglich wegnavigieren. Dieser Test hält die jetzt explizite
 * Zuordnung fest.
 */
class StartRouteTest {

    @Test
    fun `ohne Zugangsdaten fuehrt der Start ins Onboarding`() {
        assertEquals(ROUTE_ONBOARDING, startRouteFor(isConfigured = false, importUrl = null))
    }

    @Test
    fun `ohne Zugangsdaten gewinnt das Onboarding auch gegen eine geteilte URL`() {
        assertEquals(
            ROUTE_ONBOARDING,
            startRouteFor(isConfigured = false, importUrl = "https://example.org/rezept"),
        )
    }

    @Test
    fun `mit Zugangsdaten startet die App in der Einkaufsliste`() {
        assertEquals(ROUTE_SHOPPING, startRouteFor(isConfigured = true, importUrl = null))
    }

    @Test
    fun `eine geteilte URL fuehrt direkt in den Import`() {
        assertEquals(
            ROUTE_RECIPE_URL_IMPORT,
            startRouteFor(isConfigured = true, importUrl = "https://example.org/rezept"),
        )
    }
}
