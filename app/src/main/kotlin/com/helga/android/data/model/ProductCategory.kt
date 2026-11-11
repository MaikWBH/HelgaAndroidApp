package com.helga.android.data.model

/**
 * Gemeinsamer Kategorien-Katalog für Bons, Auswertung und Gänge (Phase 3).
 * Quelle der Wahrheit: `.claude/phasen/phase-3-daten/kategorien.md`; die Schlüssel sind identisch zu
 * `server/app/categories.py`. Reihenfolge = Anzeige- und Standard-Gangreihenfolge.
 */
enum class ProductCategory(val key: String, val isAisle: Boolean) {
    OBST_GEMUESE("obst_gemuese", true),
    BROT_BACKWAREN("brot_backwaren", true),
    MILCH_EIER("milch_eier", true),
    FLEISCH_WURST("fleisch_wurst", true),
    FISCH("fisch", true),
    TIEFKUEHL("tiefkuehl", true),
    TROCKENWARE("trockenware", true),
    WUERZEN_BACKEN("wuerzen_backen", true),
    FRUEHSTUECK("fruehstueck", true),
    GETRAENKE("getraenke", true),
    ALKOHOL("alkohol", true),
    SUESSES_SNACKS("suesses_snacks", true),
    FEINKOST("feinkost", true),
    DROGERIE("drogerie", true),
    HAUSHALT("haushalt", true),
    SONSTIGES("sonstiges", false),
    PFAND("pfand", false);

    companion object {
        /** Schlüssel → Kategorie; getrimmt und kleingeschrieben, unbekannt/leer → `null`. */
        fun fromKey(key: String?): ProductCategory? {
            val cleaned = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == cleaned }
        }

        /** Die 15 Gang-Kategorien in Katalogreihenfolge (Standard-Gangsatz neuer Märkte). */
        val aisleCategories: List<ProductCategory> = entries.filter { it.isAisle }

        /**
         * Ersatz, wenn ein Markt keinen Gang mit dieser Kategorie hat (genau eine Stufe tief).
         * Siehe „Ausweichkette" in kategorien.md.
         */
        fun fallbackFor(category: ProductCategory): ProductCategory? = when (category) {
            WUERZEN_BACKEN -> TROCKENWARE
            FRUEHSTUECK -> TROCKENWARE
            FEINKOST -> MILCH_EIER
            ALKOHOL -> GETRAENKE
            FISCH -> FLEISCH_WURST
            HAUSHALT -> DROGERIE
            DROGERIE -> HAUSHALT
            else -> null
        }
    }
}
