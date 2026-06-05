package com.helga.android.data.model

data class RecipeNutrition(
    val totalKcal: Double,          // Gesamtkalorien für alle Zutaten
    val kcalPerPortion: Double,     // pro Portion (basierend auf recipeYield)
    val protein: Double,            // g
    val fat: Double,                // g
    val carbs: Double,              // g
    val nutriScore: String,         // "a"-"e", beste Score der Zutaten
    val matchedIngredientsCount: Int, // Wie viele Zutaten mit OFF-Daten gefunden
    val totalIngredientsCount: Int,   // Gesamt-Zutaten
)

data class DayNutrition(
    val date: String,
    val recipeNames: List<String>,
    val avgKcal: Double,           // Durchschnitt der Rezepte des Tages
    val avgNutriScore: String,     // Beste Score des Tages
    val totalRecipes: Int,
)

data class WeekplanNutrition(
    val days: List<DayNutrition>,
    val weekAvgKcal: Double,       // Durchschnitt über alle Tage
    val weekAvgNutriScore: String, // Beste Score der Woche
    val totalRecipes: Int,
)
