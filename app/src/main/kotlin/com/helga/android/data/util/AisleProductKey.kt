package com.helga.android.data.util

/**
 * Bildet für Produktnamen einen stabilen Lookup-Schlüssel für die Gang-Zuordnung — unabhängig von
 * Groß-/Kleinschreibung, Klammerzusätzen ("Tomaten (klein)") und einfacher Singular-/Pluralform
 * ("Tomate" vs. "Tomaten"). Kein echter Lemmatisierer, nur ein gefaltetes Suffix: entscheidend ist
 * nicht die grammatikalisch korrekte Grundform, sondern dass Schreib- und Lesepfad denselben
 * Schlüssel treffen. Muss deshalb an beiden Stellen identisch angewendet werden
 * (`StoreRepository.saveAisleProduct`/`findAisleForProduct`).
 */
object AisleProductKey {
    private val TRAILING_NOTE_RE = Regex("""\s*\([^()]*\)\s*$""")
    private val WHITESPACE_RE = Regex("""\s+""")

    // Längster Treffer zuerst, damit "en" vor dem kürzeren "n" geprüft wird.
    private val PLURAL_SUFFIXES = listOf("en", "er", "n", "e")
    private const val MIN_STEM_LENGTH = 3

    fun normalize(raw: String): String {
        var key = raw.trim().lowercase().replace(TRAILING_NOTE_RE, "").trim()
        key = WHITESPACE_RE.replace(key, " ")
        val suffix = PLURAL_SUFFIXES.firstOrNull { key.endsWith(it) && key.length - it.length >= MIN_STEM_LENGTH }
        if (suffix != null) key = key.dropLast(suffix.length)
        return key
    }
}
