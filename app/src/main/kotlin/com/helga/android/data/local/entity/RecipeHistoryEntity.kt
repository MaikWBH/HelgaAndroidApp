package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "recipe_history",
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["plannedDate"]),
        Index(value = ["updatedAt"]),
    ],
)
data class RecipeHistoryEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val plannedDate: String,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
