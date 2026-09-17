package com.helga.android

/**
 * Bestimmt das Startziel der Navigation aus dem Verbindungszustand.
 *
 * Bewusst als reine Funktion ausgelagert: Vorher wurde das Startziel fest auf
 * [ROUTE_ONBOARDING] gesetzt und nachträglich per `LaunchedEffect` weggenavigiert, falls
 * Zugangsdaten vorlagen. Dieser Effekt fing den zu diesem Zeitpunkt noch `null`-wertigen
 * `currentBackStackEntryAsState()` ein, brach deshalb bei jedem Kaltstart ab und ließ
 * angemeldete Nutzer im Onboarding stehen. Ohne imperative Startnavigation kann weder das
 * noch der frühere Rotationsbug (Navigation feuert bei jeder Activity-Neuerstellung) erneut
 * auftreten.
 */
internal fun startRouteFor(isConfigured: Boolean, importUrl: String?): String = when {
    !isConfigured -> ROUTE_ONBOARDING
    importUrl != null -> ROUTE_RECIPE_URL_IMPORT
    else -> ROUTE_SHOPPING
}
