package com.helga.android.data.weekplan

import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import java.time.LocalDate

/**
 * Gemeinsame Kandidaten-Filter für Generierung und Neuwürfeln (wochenplan A5/A6): mealSlot (nur
 * lunch/dinner, behebt den Süßspeisen-als-Abendessen-Bug), Allergene, Kcal-Budget, Saison. Jede
 * Stufe fällt auf die vorherige zurück, wenn sie den Pool leer räumen würde. Der Saison-Filter
 * war vorher in generateWeekplan() nur eine Sortier-Präferenz (durch nachfolgende stabile
 * Sortierungen praktisch wirkungslos) — hier als echter Filter mit Fallback, damit er auch bei
 * regenerateDay()/regenerateProposalDay() (reiner Zufallsgriff, keine Sortierung) tatsächlich
 * etwas bewirkt. Als reine Funktion ausgelagert (statt DAO-Zugriff für den Allergen-Filter
 * direkt hier drin), damit sie ohne Room testbar ist — [WeekplanViewModel.applyRecipeFilters]
 * holt die Zutaten vorher und reicht sie als [ingredientsByRecipe] durch. [today] ist
 * parametrisiert, damit der Saison-Filter in Tests ein festes Datum nutzen kann, statt vom
 * tatsächlichen Kalendertag abzuhängen.
 */
fun filterCandidateRecipes(
    candidates: List<RecipeEntity>,
    constraints: WeekplanConstraintsEntity,
    ingredientsByRecipe: Map<String, List<String>>,
    warnings: MutableList<String>? = null,
    today: LocalDate = LocalDate.now(),
): List<RecipeEntity> {
    val mealFiltered = candidates.filter { it.mealSlot in listOf("lunch", "dinner") }
    if (mealFiltered.isEmpty()) {
        warnings?.add("⚠️ Zu wenige klassifizierte Hauptgericht-Rezepte. Alle Rezepte werden verwendet.")
    }
    val mealFilteredSafe = mealFiltered.ifEmpty { candidates }

    val excludeAllergens = parseAllergenJson(constraints.excludeAllergens)
    val allergenFiltered = if (excludeAllergens.isEmpty()) {
        mealFilteredSafe
    } else {
        mealFilteredSafe.filter { recipe ->
            val foods = ingredientsByRecipe[recipe.id] ?: emptyList()
            foods.none { food -> excludeAllergens.any { allergen -> food.contains(allergen, ignoreCase = true) } }
        }
    }
    if (allergenFiltered.isEmpty() && excludeAllergens.isNotEmpty()) {
        warnings?.add("⚠️ Keine Rezepte ohne ausgeschlossene Allergene gefunden. Allergen-Filter wird ignoriert.")
    }
    val allergenFilteredSafe = allergenFiltered.ifEmpty { mealFilteredSafe }

    val kcalFiltered = allergenFilteredSafe.filter { recipe ->
        recipe.nutritionKcal <= 0.0 || recipe.nutritionKcal <= constraints.maxKcalPerPortion
    }
    val kcalFilteredSafe = kcalFiltered.ifEmpty { allergenFilteredSafe }

    val currentSeason = when (today.monthValue) {
        in 3..5 -> "frühling"
        in 6..8 -> "sommer"
        in 9..11 -> "herbst"
        else -> "winter"
    }
    val seasonFiltered = kcalFilteredSafe.filter { recipe ->
        recipe.seasonFit.isBlank() ||
            recipe.seasonFit.lowercase().let { it == "ganzjährig" || it == currentSeason }
    }
    return seasonFiltered.ifEmpty { kcalFilteredSafe }
}

fun parseAllergenJson(json: String): List<String> = json
    .trim()
    .removePrefix("[")
    .removeSuffix("]")
    .split(",")
    .map { it.trim().trim('"') }
    .filter { it.isNotBlank() }
