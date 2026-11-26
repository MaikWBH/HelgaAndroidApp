package com.helga.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.helga.android.R
import com.helga.android.data.model.MealSlots

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
