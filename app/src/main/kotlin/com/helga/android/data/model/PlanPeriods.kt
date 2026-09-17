package com.helga.android.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Ein zusammenhängender Planungszeitraum, beide Grenzen inklusive. */
data class PlanPeriod(val start: LocalDate, val end: LocalDate) {
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(end)

    fun dates(): List<LocalDate> = (0 until dayCount).map { start.plusDays(it.toLong()) }
}

/**
 * Zeitraum-Raster des Wochenplans.
 *
 * Der reguläre Rhythmus sind [length] Tage ab dem Einkaufstag ([anchor]) — geplant wird also
 * immer ab dem Tag, an dem eingekauft wird. [overrides] hält einmalige Ausnahmen als Zuordnung
 * `Zeitraum-Start → abweichendes Zeitraum-Ende`.
 *
 * Eine Ausnahme gilt nur für ihren eigenen Zeitraum: der Folgezeitraum beginnt am Tag danach und
 * endet an der nächsten regulären Rastergrenze. Dadurch sitzt der Rhythmus spätestens ab dem
 * übernächsten Zeitraum wieder auf dem Einkaufstag — egal ob verkürzt oder verlängert wurde.
 * Beispiel (Einkaufstag Montag, 7 Tage): Mo–So auf Mo–Di gekürzt ⇒ Folgezeitraum Mi–So, danach
 * wieder Mo–So.
 */
object PlanPeriods {

    /** Obergrenze für einen selbst gewählten Zeitraum. */
    const val MAX_PERIOD_DAYS = 28

    /** Wie weit Navigation und Rückwärtssuche maximal laufen — schützt vor Endlosschleifen. */
    private const val MAX_STEPS = 400

    /** Festes Bezugsdatum, aus dem das Raster abgeleitet wird. */
    private val REFERENCE: LocalDate = LocalDate.of(2024, 1, 1)

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Einkaufstag aus der Einstellung (0 = Montag … 6 = Sonntag). */
    fun anchorOf(shoppingDayIndex: Int): DayOfWeek = DayOfWeek.of(shoppingDayIndex.coerceIn(0, 6) + 1)

    private fun gridReference(anchor: DayOfWeek): LocalDate =
        REFERENCE.with(TemporalAdjusters.previousOrSame(anchor))

    /** Beginn der regulären Rasterzelle, die [date] enthält. */
    fun gridStartOnOrBefore(date: LocalDate, anchor: DayOfWeek, length: Int): LocalDate {
        val reference = gridReference(anchor)
        val cell = Math.floorDiv(ChronoUnit.DAYS.between(reference, date), length.toLong())
        return reference.plusDays(cell * length)
    }

    /**
     * Reguläres Ende für einen Zeitraum ab [start]: der Tag vor der nächsten Rastergrenze. Für
     * einen versetzt startenden Zeitraum (nach einer Ausnahme) ist das automatisch der Rest bis
     * zum nächsten Einkaufstag.
     */
    fun defaultEnd(start: LocalDate, anchor: DayOfWeek, length: Int): LocalDate =
        gridStartOnOrBefore(start, anchor, length).plusDays(length.toLong()).minusDays(1)

    private fun endFor(
        start: LocalDate,
        anchor: DayOfWeek,
        length: Int,
        overrides: Map<LocalDate, LocalDate>,
    ): LocalDate {
        val override = overrides[start]
        return if (override != null && !override.isBefore(start)) override
        else defaultEnd(start, anchor, length)
    }

    /** Der Zeitraum, der [date] enthält. */
    fun periodAt(
        date: LocalDate,
        anchor: DayOfWeek,
        length: Int,
        overrides: Map<LocalDate, LocalDate> = emptyMap(),
    ): PlanPeriod {
        // Weit genug vor [date] auf einer Rastergrenze starten und vorwärts kacheln, damit auch
        // ein durch eine Ausnahme verschobener Zeitraum korrekt getroffen wird.
        val gridStart = gridStartOnOrBefore(date, anchor, length)
        var start = gridStart.minusDays(8L * length)
        val earliestOverride = overrides.keys.minOrNull()
        if (earliestOverride != null &&
            earliestOverride.isBefore(start) &&
            ChronoUnit.DAYS.between(earliestOverride, date) <= 366
        ) {
            start = earliestOverride
        }
        repeat(MAX_STEPS) {
            val end = endFor(start, anchor, length, overrides)
            if (!date.isAfter(end)) return PlanPeriod(start, end)
            start = end.plusDays(1)
        }
        return PlanPeriod(gridStart, defaultEnd(gridStart, anchor, length))
    }

    /**
     * Der Zeitraum [index] Schritte vom heutigen Zeitraum entfernt (0 = der Zeitraum, in dem
     * [today] liegt, negativ = zurück).
     */
    fun periodFor(
        index: Int,
        today: LocalDate,
        anchor: DayOfWeek,
        length: Int,
        overrides: Map<LocalDate, LocalDate> = emptyMap(),
    ): PlanPeriod {
        var period = periodAt(today, anchor, length, overrides)
        repeat(minOf(Math.abs(index), MAX_STEPS)) {
            val probe = if (index > 0) period.end.plusDays(1) else period.start.minusDays(1)
            period = periodAt(probe, anchor, length, overrides)
        }
        return period
    }

    /**
     * Übernimmt einen im Kalender gewählten Zeitraum als einmalige Ausnahme für den Zeitraum, der
     * bisher bei [currentStart] begann. Verschiebt der Nutzer dabei auch den Start, endet der
     * vorhergehende Zeitraum entsprechend früher bzw. später.
     *
     * Ausnahmen, die ohnehin dem Raster entsprechen, sowie Altlasten über ein Jahr werden dabei
     * verworfen, damit sich die Einstellung nicht unbegrenzt füllt.
     */
    fun withOverride(
        overrides: Map<LocalDate, LocalDate>,
        currentStart: LocalDate,
        newStart: LocalDate,
        newEnd: LocalDate,
        anchor: DayOfWeek,
        length: Int,
        today: LocalDate = LocalDate.now(),
    ): Map<LocalDate, LocalDate> {
        val result = overrides.toMutableMap()
        if (newStart != currentStart) {
            val previous = periodAt(currentStart.minusDays(1), anchor, length, overrides)
            if (newStart.isAfter(previous.start)) result[previous.start] = newStart.minusDays(1)
        }
        result[newStart] = newEnd
        return result
            .filterKeys { !it.isBefore(today.minusDays(366)) }
            .filterNot { (start, end) -> end == defaultEnd(start, anchor, length) }
    }

    /** Serialisierung für DataStore: `start>end` je Eintrag, durch `;` getrennt. */
    fun encodeOverrides(overrides: Map<LocalDate, LocalDate>): String =
        overrides.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key.format(ISO)}>${it.value.format(ISO)}" }

    fun decodeOverrides(raw: String): Map<LocalDate, LocalDate> =
        raw.split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split('>')
                if (parts.size != 2) return@mapNotNull null
                runCatching {
                    LocalDate.parse(parts[0], ISO) to LocalDate.parse(parts[1], ISO)
                }.getOrNull()
            }
            .toMap()
}
