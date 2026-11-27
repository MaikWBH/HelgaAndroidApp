package com.helga.android.data.weekplan

import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Gesperrte Tage mit bereits vorhandenem Rezept sind Anker: sie bleiben bei der Generierung
 * unangetastet (wochenplan A10). Liefert Tag-ID → bestehende Rezepte.
 */
fun selectAnchorDays(
    days: List<WeekplanDayEntity>,
    existingByDay: Map<String, List<WeekplanRecipeEntity>>,
): Map<String, List<WeekplanRecipeEntity>> =
    days.filter { it.isLocked == 1 }
        .mapNotNull { day -> existingByDay[day.id]?.takeIf { it.isNotEmpty() }?.let { day.id to it } }
        .toMap()

/** Stichtag (ISO-Datum) für die Wiederholungssperre: heute minus [maxRepeatDays]. */
fun repeatCutoffDate(today: LocalDate, maxRepeatDays: Int): String =
    today.minusDays(maxRepeatDays.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

/** Summe der 👍/👎-Werte je Rezept (positiv = bevorzugt, negativ = vermeiden). */
fun sumFeedbackScores(feedback: List<RecipeFeedbackEntity>): Map<String, Int> =
    feedback.groupBy { it.recipeId }.mapValues { (_, entries) -> entries.sumOf { it.liked } }

/** Kandidaten ohne kürzlich geplante Rezepte; fällt auf alle zurück, wenn das den Pool leeren würde. */
fun candidatePool(allActive: List<RecipeEntity>, recentIds: Set<String>): List<RecipeEntity> =
    allActive.filter { it.id !in recentIds }.ifEmpty { allActive }
