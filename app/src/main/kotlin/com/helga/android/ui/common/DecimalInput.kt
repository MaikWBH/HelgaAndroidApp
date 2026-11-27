package com.helga.android.ui.common

/**
 * Liest Dezimalzahlen aus Eingabefeldern (Beträge, Mengen).
 *
 * Regel (Phase 7, F12): Leerzeichen und `€` werden entfernt. Stehen `.` und `,` beide im Text, ist der zuletzt
 * stehende das Dezimalzeichen und der andere wird entfernt („1.234,56“ und „1,234.56“ sind 1234,56). Danach ist
 * höchstens ein Dezimalzeichen erlaubt, sonst ist die Eingabe ungültig (`null`).
 */
object DecimalInput {

    private val valid = Regex("""^-?(\d+(\.\d*)?|\.\d+)$""")

    fun parse(raw: String): Double? {
        var text = raw.replace(" ", "").replace(" ", "").replace("€", "")
        if (text.isEmpty()) return null
        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')
        if (lastDot >= 0 && lastComma >= 0) {
            val other = if (lastComma > lastDot) '.' else ','
            text = text.replace(other.toString(), "")
        }
        text = text.replace(',', '.')
        if (text.count { it == '.' } > 1) return null
        if (!valid.matches(text)) return null
        return text.toDoubleOrNull()
    }
}
