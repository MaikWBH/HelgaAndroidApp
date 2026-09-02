package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "weekplan_constraints",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])]
)
data class WeekplanConstraintsEntity(
    @PrimaryKey val id: String = "global",
    val maxMeatPerWeek: Int = 3,
    val maxFishPerWeek: Int = 2,
    val minVegetarianPerWeek: Int = 2,
    val maxRepeatDays: Int = 14,
    val maxKcalPerPortion: Int = 700,
    // LEGACY: Nutri-Score-Filter entfernt (naehrwerte A3/wochenplan A16), Spalte bleibt aus
    // Migrationsgründen erhalten (siehe RecipeEntity.mealType für dasselbe Muster).
    val minNutriScore: String = "c",
    val preferOrganic: Int = 0, // boolean flag
    val excludeAllergens: String = "[]", // JSON array of allergen strings
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
