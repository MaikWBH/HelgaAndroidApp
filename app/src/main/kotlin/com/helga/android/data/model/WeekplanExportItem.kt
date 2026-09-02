package com.helga.android.data.model

/**
 * Ein Produkt-Kandidat für den Export des Wochenplans in die Einkaufsliste — entweder eine
 * Rezeptzutat oder ein freier Extra-Eintrag. `key` ist die Zutaten-/Extra-Id, dient als stabiler
 * Schlüssel für die Abwahl in der Export-Vorschau.
 */
data class WeekplanExportItem(
    val key: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val recipeName: String,
)
