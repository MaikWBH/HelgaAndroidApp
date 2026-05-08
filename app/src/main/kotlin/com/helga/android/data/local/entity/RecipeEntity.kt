package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
        Index(value = ["dirty"]),
    ],
)
data class RecipeEntity(
    @PrimaryKey val id: String,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val recipeYield: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val imagePath: String = "",
    val sourceUrl: String = "",
    val rating: Int = 0,
    val proteinType: String = "",
    val effort: String = "",
    val cuisine: String = "",
    val mealType: String = "",
    val seasonFit: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
    val localImageUri: String = "",
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Int = 0,
    @ColumnInfo(defaultValue = "")
    val personalNotes: String = "",
)
