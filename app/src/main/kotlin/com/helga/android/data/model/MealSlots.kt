package com.helga.android.data.model

/** Feste Mahlzeiten-Slots — muss mit RecipeEntity.mealSlot und Server-Enum übereinstimmen. */
object MealSlots {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"
    const val OTHER = "other"

    val ALL = listOf(BREAKFAST, LUNCH, DINNER, SNACK, OTHER)
}
