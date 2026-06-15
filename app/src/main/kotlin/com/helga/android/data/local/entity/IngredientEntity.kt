package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "recipe_ingredients",
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["dirty"]),
    ],
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int = 0,
    val quantity: Double = 0.0,
    val unit: String = "",
    val food: String = "",
    val note: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
