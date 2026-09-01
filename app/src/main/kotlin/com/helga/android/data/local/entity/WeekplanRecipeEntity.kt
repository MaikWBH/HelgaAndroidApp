package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "weekplan_recipes",
    indices = [
        Index(value = ["weekplanDayId"]),
        Index(value = ["recipeId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class WeekplanRecipeEntity(
    @PrimaryKey val id: String,
    val weekplanDayId: String,
    val recipeId: String,
    val position: Int = 0,
    /** Frühstück/Mittag/Abend/Snack je Eintrag (wochenplan A14), leer = kein Slot gesetzt. */
    val mealSlot: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
