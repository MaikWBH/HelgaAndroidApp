package com.helga.android.data.model

/** Zeitraum der Karten „Märkte", „Kategorien" und „Top-Artikel" (Kalendermonate). */
enum class StatsRange { THIS_MONTH, THREE_MONTHS, TWELVE_MONTHS, ALL }

/** Summe einer Periode (`YYYY-MM` bzw. `YYYY-Www`). */
data class PeriodAmount(val period: String, val amount: Double, val count: Int)

/** Summe je Schlüssel (Markt, Kategorie oder Artikel) über einen Zeitraum. */
data class KeyAmount(val key: String, val label: String, val amount: Double, val quantity: Double, val count: Int)

/** Durchschnittlicher Stückpreis eines Artikels in einer Periode. */
data class TrendPoint(val period: String, val avgUnitPrice: Double)
