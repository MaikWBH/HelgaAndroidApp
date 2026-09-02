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
    // LEGACY: durch mealSlot ersetzt (siehe MIGRATION_20_21), nur für Alt-Sync-Kompatibilität.
    val mealType: String = "",
    @ColumnInfo(defaultValue = "other")
    val mealSlot: String = "other",  // "breakfast", "lunch", "dinner", "snack", "other"
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
    // Nährwerte, immer für eine feste Basis von 4 Portionen (NUTRITION_BASELINE_PORTIONS).
    // nutritionSource: "manual", "ai" oder "" (noch nicht ermittelt).
    @ColumnInfo(defaultValue = "0.0")
    val nutritionKcal: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0")
    val nutritionProtein: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0")
    val nutritionFat: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0")
    val nutritionCarbs: Double = 0.0,
    // LEGACY: Nutri-Score-Feature entfernt (naehrwerte A3), Spalte bleibt aus
    // Migrationsgründen erhalten (siehe mealType oben für dasselbe Muster).
    @ColumnInfo(defaultValue = "")
    val nutritionNutriScore: String = "",
    @ColumnInfo(defaultValue = "")
    val nutritionSource: String = "",
    // 0 = noch nie geändert, dann gilt der aus recipeYield geparste Standardwert (rezepte A9).
    @ColumnInfo(defaultValue = "0")
    val lastServings: Int = 0,
)
