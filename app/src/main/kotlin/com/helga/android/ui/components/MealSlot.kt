package com.helga.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.helga.android.R

/** Feste Mahlzeiten-Slots — muss mit RecipeEntity.mealSlot und Server-Enum übereinstimmen. */
object MealSlots {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"
    const val OTHER = "other"

    val ALL = listOf(BREAKFAST, LUNCH, DINNER, SNACK, OTHER)
}

@Composable
fun mealSlotLabel(slot: String): String = stringResource(
    when (slot) {
        MealSlots.BREAKFAST -> R.string.meal_slot_breakfast
        MealSlots.LUNCH -> R.string.meal_slot_lunch
        MealSlots.DINNER -> R.string.meal_slot_dinner
        MealSlots.SNACK -> R.string.meal_slot_snack
        else -> R.string.meal_slot_other
    }
)
