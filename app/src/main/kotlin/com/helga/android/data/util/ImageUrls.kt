package com.helga.android.data.util

/**
 * Baut die Anzeige-URL für ein auf dem Server liegendes Bild.
 *
 * Hintergrund: Aus der alten Helga-App migrierte Rezepte tragen `imagePath`-Werte
 * mit Verzeichnis-Präfix (z. B. "images/foo.jpg"), während neu hochgeladene Bilder
 * nur den reinen Dateinamen ("<uuid>.jpg") haben. Der Server-Endpoint
 * `/api/images/{filename}` akzeptiert nur ein einzelnes Pfadsegment – ein doppeltes
 * Segment ("/api/images/images/foo.jpg") matcht die Route nicht und liefert 404.
 *
 * `substringAfterLast('/')` schneidet einen etwaigen Verzeichnis-Präfix weg und
 * lässt bare Dateinamen unverändert – deckt also beide Fälle ab.
 */
object ImageUrls {
    fun serverImageUrl(serverUrl: String, imagePath: String): String =
        "${serverUrl.trimEnd('/')}/api/images/${imagePath.substringAfterLast('/')}"
}
