package com.helga.android.data.util

/**
 * Ergebnis des Parsens einer Freitext-Zutatenzeile wie "200g Mehl" oder "2 EL Zucker (gehäuft)".
 */
data class ParsedIngredientLine(
    val quantity: Double = 0.0,
    val unit: String = "",
    val food: String = "",
    val note: String = "",
)

/**
 * Zerlegt eine KI-generierte oder per URL-Import gescrapte Zutatenzeile in
 * Menge/Einheit/Lebensmittel/Notiz, analog zum Packungsgrößen-Parser in
 * server/app/off.py. Liefert bei nicht erkennbarer Menge/Einheit bewusst die
 * Defaults (0.0/""), der Rest der Zeile bleibt aber immer als `food` erhalten
 * statt verworfen zu werden – kein Informationsverlust gegenüber dem Vorher-Zustand.
 */
object IngredientLineParser {

    private val UNIT_CANONICAL: Map<String, String> = buildMap {
        listOf("g", "gramm", "gr").forEach { put(it, "g") }
        put("kg", "kg")
        listOf("ml", "milliliter").forEach { put(it, "ml") }
        listOf("l", "liter", "ltr").forEach { put(it, "l") }
        listOf("el", "esslöffel", "esslöffeln").forEach { put(it, "EL") }
        listOf("tl", "teelöffel", "teelöffeln").forEach { put(it, "TL") }
        listOf("stück", "stk", "st").forEach { put(it, "Stück") }
        listOf("prise", "prisen").forEach { put(it, "Prise") }
        put("bund", "Bund")
        listOf("dose", "dosen").forEach { put(it, "Dose") }
        listOf("päckchen", "pck", "pkt").forEach { put(it, "Päckchen") }
        listOf("scheibe", "scheiben").forEach { put(it, "Scheibe") }
        listOf("tasse", "tassen").forEach { put(it, "Tasse") }
        listOf("glas", "gläser").forEach { put(it, "Glas") }
        listOf("zehe", "zehen").forEach { put(it, "Zehe") }
        put("handvoll", "Handvoll")
        listOf("blatt", "blätter").forEach { put(it, "Blatt") }
        listOf("zweig", "zweige").forEach { put(it, "Zweig") }
        put("würfel", "Würfel")
        listOf("msp", "messerspitze").forEach { put(it, "Msp.") }
        listOf("packung", "packungen", "pack").forEach { put(it, "Packung") }
    }

    // Menge am Zeilenanfang: Dezimalzahl (Komma/Punkt), einfacher Bruch ("1/2")
    // oder Bereich ("2-3", übernimmt die erste Zahl).
    private val QUANTITY_RE = Regex("""^(\d+[.,]?\d*)\s*(?:/\s*(\d+))?(?:\s*-\s*\d+[.,]?\d*)?\s*""")
    private val TRAILING_NOTE_RE = Regex("""\(([^()]*)\)\s*$""")
    private val FIRST_WORD_RE = Regex("""^([^\s,]+)\s*(.*)$""")

    fun parse(raw: String): ParsedIngredientLine {
        var rest = raw.trim()
        if (rest.isEmpty()) return ParsedIngredientLine()

        var note = ""
        TRAILING_NOTE_RE.find(rest)?.let { match ->
            note = match.groupValues[1].trim()
            rest = rest.removeRange(match.range).trim()
        }

        var quantity = 0.0
        QUANTITY_RE.find(rest)?.takeIf { it.value.isNotBlank() }?.let { match ->
            val whole = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            val fractionDenom = match.groupValues[2].toDoubleOrNull()
            quantity = if (fractionDenom != null && fractionDenom != 0.0) whole / fractionDenom else whole
            rest = rest.substring(match.value.length).trim()
        }

        var unit = ""
        if (rest.isNotEmpty()) {
            FIRST_WORD_RE.find(rest)?.let { match ->
                val firstWord = match.groupValues[1]
                val canonical = UNIT_CANONICAL[firstWord.lowercase().trimEnd('.')]
                if (canonical != null) {
                    unit = canonical
                    rest = match.groupValues[2].trim()
                }
            }
        }

        return ParsedIngredientLine(quantity = quantity, unit = unit, food = rest.trim(), note = note)
    }
}
