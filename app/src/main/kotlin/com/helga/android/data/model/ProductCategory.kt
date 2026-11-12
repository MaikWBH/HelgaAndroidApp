package com.helga.android.data.model

/**
 * Gemeinsamer Kategorien-Katalog für Bons, Auswertung und Gänge (Phase 3).
 * Quelle der Wahrheit: `.claude/phasen/phase-3-daten/kategorien.md`; die Schlüssel sind identisch zu
 * `server/app/categories.py`. Reihenfolge = Anzeige- und Standard-Gangreihenfolge.
 *
 * `defaultAisleName` ist der deutsche Datenwert des Standard-Gangnamens (kein UI-Text; Anzeigenamen kommen aus
 * `category_<schluessel>` in strings.xml). Er wird beim Anlegen von Märkten als Gangname gespeichert.
 */
enum class ProductCategory(val key: String, val isAisle: Boolean, val defaultAisleName: String) {
    OBST_GEMUESE("obst_gemuese", true, "Obst & Gemüse"),
    BROT_BACKWAREN("brot_backwaren", true, "Brot & Backwaren"),
    MILCH_EIER("milch_eier", true, "Milchprodukte & Eier"),
    FLEISCH_WURST("fleisch_wurst", true, "Fleisch & Wurst"),
    FISCH("fisch", true, "Fisch"),
    TIEFKUEHL("tiefkuehl", true, "Tiefkühl"),
    TROCKENWARE("trockenware", true, "Konserven & Trockenware"),
    WUERZEN_BACKEN("wuerzen_backen", true, "Backen, Gewürze & Saucen"),
    FRUEHSTUECK("fruehstueck", true, "Frühstück, Kaffee & Tee"),
    GETRAENKE("getraenke", true, "Getränke"),
    ALKOHOL("alkohol", true, "Bier, Wein & Spirituosen"),
    SUESSES_SNACKS("suesses_snacks", true, "Süßes & Snacks"),
    FEINKOST("feinkost", true, "Feinkost & Fertiggerichte"),
    DROGERIE("drogerie", true, "Drogerie & Baby"),
    HAUSHALT("haushalt", true, "Haushalt & Tierbedarf"),
    SONSTIGES("sonstiges", false, "Sonstiges"),
    PFAND("pfand", false, "Pfand");

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
