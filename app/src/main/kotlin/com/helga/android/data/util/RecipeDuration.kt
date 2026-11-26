package com.helga.android.data.util

import kotlin.math.roundToInt

/**
 * Zeitangaben von Rezepten: ISO-8601 („PT1H30M“) und deutscher Freitext („1 Std 30 Min“, „1,5 h“, „30-40 Min“).
 * Gespeichert wird ISO (wie im URL-Import); die Anzeige formatiert Minuten über Strings (Phase 7, F19).
 */
object RecipeDuration {

    private val iso = Regex("""(?i)^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")
    private val range = Regex("""(\d+(?:\.\d+)?)\s*[-–]\s*\d+(?:\.\d+)?""")
    private val clock = Regex("""^(\d+):(\d{2})\s*(?:h|std\.?)?$""")
    private val hours = Regex("""(\d+(?:\.\d+)?)\s*(?:stunden|stunde|std\.?|h)(?![a-zäöü])""")
    private val minutes = Regex("""(\d+)\s*(?:minuten|minute|min\.?|m)(?![a-zäöü])""")
    private val plain = Regex("""^\d+$""")

    /** Minuten aus ISO oder Freitext; `null`, wenn nichts erkannt wird oder das Ergebnis nicht positiv ist. */
    fun parseMinutes(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        iso.matchEntire(text)?.let { m ->
            val groups = m.groupValues.drop(1)
            if (groups.any { it.isNotEmpty() }) {
                val (d, h, min, s) = groups.map { it.toLongOrNull() ?: 0L }
                val seconds = d * 86_400 + h * 3_600 + min * 60 + s
                return Math.round(seconds / 60.0).toInt().takeIf { it > 0 }
            }
        }
        val lower = range.replace(text.lowercase().replace(',', '.'), "$1")
        if (plain.matches(lower)) return lower.toInt().takeIf { it > 0 }
        clock.matchEntire(lower)?.let { m ->
            return (m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()).takeIf { it > 0 }
        }
        val hourMatches = hours.findAll(lower).toList()
        val minuteMatches = minutes.findAll(lower).toList()
        if (hourMatches.isEmpty() && minuteMatches.isEmpty()) return null
        val total = hourMatches.sumOf { it.groupValues[1].toDouble() * 60.0 } +
            minuteMatches.sumOf { it.groupValues[1].toDouble() }
        return total.roundToInt().takeIf { it > 0 }
    }

    /** Wie `_mins_to_iso` im Server (`ai.py`): ≤ 0 → leer, sonst `PT1H30M`, `PT1H`, `PT45M`. */
    fun toIso(minutes: Int): String {
        if (minutes <= 0) return ""
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "PT${h}H${m}M"
            h > 0 -> "PT${h}H"
            else -> "PT${m}M"
        }
    }
}
