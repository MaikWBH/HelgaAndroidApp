package com.helga.android.data.receipts

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Reine Datumshilfen für Bons (kein Android, voll testbar). Kaufdatum wird als `YYYY-MM-DD` geführt. */
object ReceiptDates {

    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun parseDay(day: String): LocalDate? =
        try { LocalDate.parse(day) } catch (_: DateTimeParseException) { null }

    /** Mitternacht UTC des Tages in Unix-ms (Sortierschlüssel wie auf dem Server); ungültig → 0. */
    fun dayToEpochMs(day: String): Long =
        parseDay(day)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() ?: 0L

    /** Tag eines Zeitpunkts in der gegebenen Zone als `YYYY-MM-DD`; `ms <= 0` → leer. */
    fun epochMsToDay(ms: Long, zone: ZoneId): String =
        if (ms <= 0L) "" else LocalDate.ofInstant(Instant.ofEpochMilli(ms), zone).toString()

    fun isValidDay(day: String): Boolean = parseDay(day) != null

    /** `HH:MM` (24 Stunden). */
    fun isValidTime(time: String): Boolean =
        Regex("""\d{2}:\d{2}""").matches(time) && try { LocalTime.parse(time); true } catch (_: DateTimeParseException) { false }

    /** `TT.MM.JJJJ` aus dem gedruckten Tag, sonst aus dem Kaufzeitpunkt, sonst leer. */
    fun displayDate(day: String, epochMs: Long, zone: ZoneId): String {
        val parsed = parseDay(day) ?: epochMsToDay(epochMs, zone).takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        return parsed?.format(DISPLAY).orEmpty()
    }

    /** Ältester Tag, der lokal gehalten wird (`months` Monate vor heute); `months <= 0` → alles laden (`null`). */
    fun cutoffDay(today: LocalDate, months: Int): String? =
        if (months <= 0) null else today.minusMonths(months.toLong()).toString()
}
