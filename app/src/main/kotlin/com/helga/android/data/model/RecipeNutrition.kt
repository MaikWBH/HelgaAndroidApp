package com.helga.android.data.model

/** Feste Portionsbasis: Nährwerte werden immer für diese Anzahl Portionen
 * berechnet/erfasst, unabhängig von der im UI angezeigten Portionenanzahl. */
const val NUTRITION_BASELINE_PORTIONS = 4

data class RecipeNutrition(
    val totalKcal: Double,          // Gesamtkalorien für NUTRITION_BASELINE_PORTIONS Portionen
    val kcalPerPortion: Double,     // totalKcal / NUTRITION_BASELINE_PORTIONS
    val protein: Double,            // g, für NUTRITION_BASELINE_PORTIONS Portionen
    val fat: Double,                // g, für NUTRITION_BASELINE_PORTIONS Portionen
    val carbs: Double,              // g, für NUTRITION_BASELINE_PORTIONS Portionen
    val nutriScore: String,         // "a"-"e"
    val source: String,             // "manual", "ai" oder "" (noch nicht ermittelt)
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
