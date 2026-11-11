package com.helga.android.data.weekplan

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeHistoryEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.dto.WeekplanAssignmentDto
import com.helga.android.data.repository.WeekplanRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wochenplan-Generierung: Vorschlag erzeugen, einzelne Tage neu würfeln, letzte Woche übernehmen.
 * Liest keinen UI-Zustand; alle Eingaben kommen als Parameter, Ergebnisse als Rückgabewerte.
 */
@Singleton
class WeekplanGenerator @Inject constructor(
    private val repository: WeekplanRepository,
    private val weekplanDao: WeekplanDao,
    private val recipeDao: RecipeDao,
    private val recipeHistoryDao: RecipeHistoryDao,
    private val recipeFeedbackDao: RecipeFeedbackDao,
    private val preferences: AppPreferences,
) {

    /** Erzeugt einen Wochenvorschlag für die ersten N Tage (N = Einstellung). */
    suspend fun generate(
        days: List<WeekplanDayEntity>,
        allRecipes: Map<String, RecipeEntity>,
        c: WeekplanConstraintsEntity,
    ): WeekplanGenerateStatus {
        return try {
            val dayCount = preferences.weekplanDays.first()
            val currentDays = days.take(dayCount).filter { it.isSkipped == 0 }
            if (currentDays.isEmpty()) {
                return WeekplanGenerateStatus.Error("Keine Tage vorhanden")
            }

            // --- Anker-Rezepte: gesperrte Tage mit vorhandenem Rezept bleiben unangetastet,
            // alle anderen (auch bereits befüllte) werden neu generiert (wochenplan A10) ---
            val lockedExisting = currentDays.filter { it.isLocked == 1 }
                .associate { it.id to weekplanDao.recipesForDay(it.id) }
            val anchorDays = selectAnchorDays(currentDays, lockedExisting)

            // Kürzlich geplante Rezepte ausschließen (Wiederholungssperre)
            val since = repeatCutoffDate(LocalDate.now(), c.maxRepeatDays)
            val recentIds = recipeHistoryDao.getRecentRecipeIds(since).toSet()

            // Feedback-Scores laden (positiv = bevorzugt, negativ = vermeiden)
            val feedbackScores = sumFeedbackScores(recipeFeedbackDao.getAll())

            // Alle verfügbaren Rezepte
            val allRecipesList = allRecipes.values.toList().filter { it.deleted == 0 }
            val candidates = candidatePool(allRecipesList, recentIds) // Fallback wenn alle kürzlich geplant
            if (candidates.isEmpty()) {
                return WeekplanGenerateStatus.Error("Keine Rezepte verfügbar")
            }

            val warnings = mutableListOf<String>()
            val seasonAware = applyRecipeFilters(candidates, c, warnings)

            // Rezepte nach Typ kategorisieren
            val meatRecipes = seasonAware.filter { it.proteinType.lowercase() in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") }
            val fishRecipes = seasonAware.filter { it.proteinType.lowercase() in listOf("fisch", "fish", "meeresfrüchte", "seafood") }
            val vegRecipes = seasonAware.filter { it.proteinType.lowercase() in listOf("vegetarisch", "vegetarian", "vegan") }
            val otherRecipes = seasonAware.filter { it !in meatRecipes && it !in fishRecipes && it !in vegRecipes }

            val quickRecipes = seasonAware.filter { it.effort.lowercase() in listOf("einfach", "easy", "schnell", "quick", "15min", "20min", "30min") }
            val fancyRecipes = seasonAware.filter { it.effort.lowercase() in listOf("aufwendig", "elaborate", "gourmet", "fancy") }

            val organicIds = if (c.preferOrganic == 1) recipeDao.recipeIdsWithOrganicTag().toSet() else emptySet()

            val assignments = mutableListOf<WeekplanAssignmentDto>()
            val usedIds = mutableSetOf<String>()
            val usedCuisines = mutableListOf<String>()
            var meatCount = 0
            var fishCount = 0
            var vegCount = 0

            // Anker-Rezepte in Bilanz einrechnen
            anchorDays.values.flatten().forEach { wr ->
                val recipe = allRecipes[wr.recipeId]
                usedIds.add(wr.recipeId)
                if (recipe != null) {
                    when {
                        recipe in meatRecipes || recipe.proteinType.lowercase() in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> meatCount++
                        recipe in fishRecipes || recipe.proteinType.lowercase() in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> fishCount++
                        recipe in vegRecipes || recipe.proteinType.lowercase() in listOf("vegetarisch", "vegetarian", "vegan") -> vegCount++
                    }
                    if (recipe.cuisine.isNotBlank()) usedCuisines.add(recipe.cuisine.lowercase())
                }
            }

            for (day in currentDays) {
                // Anker-Tag: bestehende Rezepte beibehalten
                val anchorList = anchorDays[day.id]
                if (anchorList != null) {
                    val anchorRecipe = anchorList.firstOrNull()
                    val recipe = anchorRecipe?.let { allRecipes[it.recipeId] }
                    assignments.add(
                        WeekplanAssignmentDto(
                            date = day.planDate,
                            recipeId = anchorRecipe?.recipeId ?: "",
                            recipeName = recipe?.name?.ifBlank { recipe.slug } ?: "Anker",
                            isLocked = true,
                        )
                    )
                    continue
                }

                val isQuick = day.isQuickDay == 1
                val isGuest = day.isGuestDay == 1

                // Pool für diesen Tag bestimmen
                val dayPool = when {
                    // Pflicht-Vegetarisch wenn Quote noch nicht erreicht und wenig Tage übrig
                    vegCount < c.minVegetarianPerWeek && (currentDays.size - assignments.size) <= (c.minVegetarianPerWeek - vegCount) ->
                        (vegRecipes + otherRecipes).filter { it.id !in usedIds }

                    // Fleisch-Limit erreicht → kein Fleisch mehr
                    meatCount >= c.maxMeatPerWeek ->
                        (fishRecipes + vegRecipes + otherRecipes).filter { it.id !in usedIds }

                    // Fisch-Limit erreicht → kein Fisch mehr
                    fishCount >= c.maxFishPerWeek ->
                        (meatRecipes + vegRecipes + otherRecipes).filter { it.id !in usedIds }

                    else -> seasonAware.filter { it.id !in usedIds }
                }

                // Effort-Filter je nach Tages-Flag
                val effortFiltered = when {
                    isQuick -> {
                        val quick = dayPool.filter { it in quickRecipes }
                        quick.ifEmpty { dayPool }
                    }
                    isGuest -> {
                        val fancy = dayPool.filter { it in fancyRecipes }
                        fancy.ifEmpty { dayPool }
                    }
                    else -> dayPool
                }

                val finalPool = effortFiltered.ifEmpty { dayPool }.ifEmpty { seasonAware }

                // Küchen-Diversität: Rezepte bevorzugen deren Küche noch nicht 2× vorkommt
                val diverseSorted = finalPool.sortedBy { recipe ->
                    val c2 = recipe.cuisine.lowercase()
                    if (c2.isBlank()) 0 else usedCuisines.count { it == c2 }
                }

                // Gewichtete Auswahl: Feedback-Score + Favoriten-Boost + Bio-Boost
                val chosen = diverseSorted
                    .sortedByDescending {
                        (feedbackScores[it.id] ?: 0) +
                            (if (it.isFavorite == 1) 2 else 0) +
                            (if (it.id in organicIds) 1 else 0)
                    }
                    .let { sorted ->
                        // Top 40% bevorzugen, aber zufällig aus ihnen wählen
                        val topCount = maxOf(1, (sorted.size * 0.4).toInt())
                        sorted.take(topCount).random()
                    }

                usedIds.add(chosen.id)
                if (chosen.cuisine.isNotBlank()) usedCuisines.add(chosen.cuisine.lowercase())
                when {
                    chosen in meatRecipes -> meatCount++
                    chosen in fishRecipes -> fishCount++
                    chosen in vegRecipes -> vegCount++
                }

                assignments.add(
                    WeekplanAssignmentDto(
                        date = day.planDate,
                        recipeId = chosen.id,
                        recipeName = chosen.name.ifBlank { chosen.slug },
                    )
                )
            }

            WeekplanGenerateStatus.Proposal(assignments, warnings)
        } catch (e: Exception) {
            WeekplanGenerateStatus.Error(e.message ?: "Fehler bei der Generierung")
        }
    }

    /** Übernimmt einen Vorschlag in den Wochenplan (ersetzt die Rezepte der betroffenen Tage). */
    suspend fun applyProposal(assignments: List<WeekplanAssignmentDto>, days: List<WeekplanDayEntity>) {
        assignments.forEach { assignment ->
            val day = days.find { it.planDate == assignment.date } ?: return@forEach
            val existing = repository.observeRecipesForDay(day.id).first()
            existing.forEach { entry ->
                repository.removeRecipe(entry)
            }
            repository.addRecipe(day.id, assignment.recipeId)
            recordHistory(assignment.recipeId, assignment.date)
        }
    }

    /**
     * Würfelt den Vorschlag an [index] neu (ohne die anderen Tage des Vorschlags zu wiederholen).
     * Liefert den neuen Eintrag oder null, wenn kein Kandidat übrig ist.
     */
    suspend fun pickProposalReplacement(
        assignments: List<WeekplanAssignmentDto>,
        index: Int,
        days: List<WeekplanDayEntity>,
        recipesMap: Map<String, RecipeEntity>,
        c: WeekplanConstraintsEntity,
    ): WeekplanAssignmentDto? {
        val target = assignments[index]

        // IDs der anderen Tage im Proposal
        val otherIds = assignments.filterIndexed { i, _ -> i != index }.map { it.recipeId }.toSet()

        // Bilanz ohne den zu ersetzenden Tag
        var meatCount = 0; var fishCount = 0; var vegCount = 0
        otherIds.forEach { id ->
            when (recipesMap[id]?.proteinType?.lowercase()) {
                in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> meatCount++
                in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> fishCount++
                in listOf("vegetarisch", "vegetarian", "vegan") -> vegCount++
            }
        }

        val since = repeatCutoffDate(LocalDate.now(), c.maxRepeatDays)
        val recentIds = recipeHistoryDao.getRecentRecipeIds(since).toSet()

        val allRecipesList = recipesMap.values.filter { it.deleted == 0 }
        val recencyFiltered = allRecipesList
            .filter { it.id !in recentIds && it.id !in otherIds && it.id != target.recipeId }
            .ifEmpty { allRecipesList.filter { it.id !in otherIds && it.id != target.recipeId } }
            .ifEmpty { allRecipesList }
        val available = applyRecipeFilters(recencyFiltered, c)

        val filtered = when {
            meatCount >= c.maxMeatPerWeek && fishCount >= c.maxFishPerWeek ->
                available.filter { it.proteinType.lowercase() in listOf("vegetarisch", "vegetarian", "vegan") || it.proteinType.isBlank() }
            meatCount >= c.maxMeatPerWeek ->
                available.filter { it.proteinType.lowercase() !in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") }
            fishCount >= c.maxFishPerWeek ->
                available.filter { it.proteinType.lowercase() !in listOf("fisch", "fish", "meeresfrüchte", "seafood") }
            else -> available
        }.ifEmpty { available }

        // Quick/Guest-Day berücksichtigen
        val day = days.find { it.planDate == target.date }
        val effortFiltered = when {
            day?.isQuickDay == 1 -> {
                val quick = filtered.filter { it.effort.lowercase() in listOf("einfach", "easy", "schnell", "quick", "15min", "20min", "30min") }
                quick.ifEmpty { filtered }
            }
            day?.isGuestDay == 1 -> {
                val fancy = filtered.filter { it.effort.lowercase() in listOf("aufwendig", "elaborate", "gourmet", "fancy") }
                fancy.ifEmpty { filtered }
            }
            else -> filtered
        }

        val chosen = effortFiltered.shuffled().firstOrNull() ?: return null
        return WeekplanAssignmentDto(
            date = target.date,
            recipeId = chosen.id,
            recipeName = chosen.name.ifBlank { chosen.slug },
        )
    }

    /** Würfelt einen Tag neu. Liefert true, wenn der Tag geändert wurde (dann Sync anstoßen). */
    suspend fun regenerateDay(
        dayId: String,
        days: List<WeekplanDayEntity>,
        recipesMap: Map<String, RecipeEntity>,
        c: WeekplanConstraintsEntity,
    ): Boolean {
        val day = days.find { it.id == dayId } ?: return false
        if (day.isSkipped == 1 || day.isLocked == 1) return false

        // Sammle alle aktuellen Rezept-IDs der Woche (außer dem zu ersetzenden Tag)
        val otherDayRecipeIds = days
            .filter { it.id != dayId }
            .flatMap { d -> weekplanDao.recipesForDay(d.id).map { it.recipeId } }
            .toSet()

        // Zähle aktuelle Bilanz (ohne den zu ersetzenden Tag)
        var meatCount = 0; var fishCount = 0; var vegCount = 0
        otherDayRecipeIds.forEach { id ->
            when (recipesMap[id]?.proteinType?.lowercase()) {
                in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> meatCount++
                in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> fishCount++
                in listOf("vegetarisch", "vegetarian", "vegan") -> vegCount++
            }
        }

        val since = repeatCutoffDate(LocalDate.now(), c.maxRepeatDays)
        val recentIds = recipeHistoryDao.getRecentRecipeIds(since).toSet()

        val allRecipesList = recipesMap.values.filter { it.deleted == 0 }
        val recencyFiltered = allRecipesList
            .filter { it.id !in recentIds && it.id !in otherDayRecipeIds }
            .ifEmpty { allRecipesList.filter { it.id !in otherDayRecipeIds } }
            .ifEmpty { allRecipesList }
        val available = applyRecipeFilters(recencyFiltered, c)

        // Constraint-basierte Auswahl
        val filtered = when {
            meatCount >= c.maxMeatPerWeek && fishCount >= c.maxFishPerWeek ->
                available.filter { it.proteinType.lowercase() in listOf("vegetarisch", "vegetarian", "vegan") || it.proteinType.isBlank() }
            meatCount >= c.maxMeatPerWeek ->
                available.filter { it.proteinType.lowercase() !in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") }
            fishCount >= c.maxFishPerWeek ->
                available.filter { it.proteinType.lowercase() !in listOf("fisch", "fish", "meeresfrüchte", "seafood") }
            else -> available
        }.ifEmpty { available }

        // Quick/Guest-Day berücksichtigen
        val effortFiltered = when {
            day.isQuickDay == 1 -> {
                val quick = filtered.filter { it.effort.lowercase() in listOf("einfach", "easy", "schnell", "quick", "15min", "20min", "30min") }
                quick.ifEmpty { filtered }
            }
            day.isGuestDay == 1 -> {
                val fancy = filtered.filter { it.effort.lowercase() in listOf("aufwendig", "elaborate", "gourmet", "fancy") }
                fancy.ifEmpty { filtered }
            }
            else -> filtered
        }

        val chosen = effortFiltered.shuffled().firstOrNull() ?: return false

        // Bestehende Rezepte des Tages entfernen
        weekplanDao.recipesForDay(dayId).forEach { repository.removeRecipe(it) }
        repository.addRecipe(dayId, chosen.id)
        recordHistory(chosen.id, day.planDate)
        return true
    }

    /** Übernimmt die Rezepte der Vorwoche (je Wochentag das erste Rezept) als Vorschlag. */
    suspend fun repeatLastWeek(
        monday: LocalDate,
        currentDays: List<WeekplanDayEntity>,
        recipesMap: Map<String, RecipeEntity>,
    ): WeekplanGenerateStatus {
        return try {
            val lastMonday = monday.minusWeeks(1)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val lastDays = weekplanDao.getDaysBetween(
                lastMonday.format(fmt),
                lastMonday.plusDays(6).format(fmt),
            )
            if (lastDays.isEmpty()) {
                return WeekplanGenerateStatus.Error("Keine Vorwoche gefunden")
            }
            val assignments = mutableListOf<WeekplanAssignmentDto>()
            lastDays.forEach { lastDay ->
                val lastDate = LocalDate.parse(lastDay.planDate, fmt)
                val dayOfWeek = lastDate.dayOfWeek
                val newDate = monday.with(dayOfWeek)
                val matchingCurrentDay = currentDays.find { it.planDate == newDate.format(fmt) }
                if (matchingCurrentDay != null) {
                    val recipes = weekplanDao.recipesForDay(lastDay.id)
                    val firstRecipe = recipes.firstOrNull() ?: return@forEach
                    val recipe = recipesMap[firstRecipe.recipeId]
                    assignments.add(
                        WeekplanAssignmentDto(
                            date = newDate.format(fmt),
                            recipeId = firstRecipe.recipeId,
                            recipeName = recipe?.name?.ifBlank { recipe.slug } ?: firstRecipe.recipeId,
                        )
                    )
                }
            }
            if (assignments.isEmpty()) {
                return WeekplanGenerateStatus.Error("Vorwoche hat keine Rezepte")
            }
            WeekplanGenerateStatus.Proposal(assignments, emptyList())
        } catch (e: Exception) {
            WeekplanGenerateStatus.Error(e.message ?: "Fehler")
        }
    }

    /** Gemeinsame Kandidaten-Filter (Allergene werden nur geladen, wenn welche ausgeschlossen sind). */
    private suspend fun applyRecipeFilters(
        candidates: List<RecipeEntity>,
        constraints: WeekplanConstraintsEntity,
        warnings: MutableList<String>? = null,
    ): List<RecipeEntity> {
        val excludeAllergens = parseAllergenJson(constraints.excludeAllergens)
        val ingredientsByRecipe = if (excludeAllergens.isEmpty()) {
            emptyMap()
        } else {
            recipeDao.allActiveIngredients().groupBy({ it.recipeId }, { it.food })
        }
        return filterCandidateRecipes(candidates, constraints, ingredientsByRecipe, warnings)
    }

    /** Protokolliert ein geplantes Rezept im Verlauf (Wiederholungssperre). */
    suspend fun recordHistory(recipeId: String, planDate: String) {
        val entry = RecipeHistoryEntity(
            id = UUID.randomUUID().toString(),
            recipeId = recipeId,
            plannedDate = planDate,
            updatedAt = System.currentTimeMillis(),
            deleted = 0,
            dirty = 1,
        )
        recipeHistoryDao.upsertAll(listOf(entry))
    }
}
