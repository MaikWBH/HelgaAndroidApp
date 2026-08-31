package com.helga.android.ui.weekplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import com.helga.android.data.local.entity.RecipeHistoryEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.model.WeekplanExportItem
import com.helga.android.data.model.WeekplanNutrition
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.remote.dto.WeekplanAssignmentDto
import com.helga.android.data.repository.WeekplanRepository
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
data class DaySummary(val recipeCount: Int, val extraCount: Int)

data class WeekBalance(val meat: Int = 0, val fish: Int = 0, val veg: Int = 0, val other: Int = 0)

/**
 * Export-Vorschau: alle Kandidaten (Rezeptzutaten + Extras) für die gewählten Tage, bevor sie in
 * die Einkaufsliste geschrieben werden. `deselected` hält die per Checkbox abgewählten Items.
 */
data class ExportPreviewState(
    val items: List<WeekplanExportItem>,
    val listId: String,
    val deselected: Set<String> = emptySet(),
)

sealed interface WeekplanGenerateStatus {
    data object Idle : WeekplanGenerateStatus
    data object Loading : WeekplanGenerateStatus
    data class Proposal(
        val assignments: List<WeekplanAssignmentDto>,
        val warnings: List<String> = emptyList(),
    ) : WeekplanGenerateStatus
    data class Error(val message: String) : WeekplanGenerateStatus
}

@HiltViewModel
class WeekplanViewModel @Inject constructor(
    private val repository: WeekplanRepository,
    private val recipeDao: RecipeDao,
    private val recipeRepository: RecipeRepository,
    private val recipeHistoryDao: RecipeHistoryDao,
    private val recipeFeedbackDao: RecipeFeedbackDao,
    private val shoppingDao: ShoppingDao,
    private val weekplanDao: WeekplanDao,
    private val weekplanSettingsDao: WeekplanSettingsDao,
    private val weekplanConstraintsDao: WeekplanConstraintsDao,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val apiFactory: com.helga.android.data.remote.SyncApiFactory,
) : ViewModel() {

    private val _selectedDayId = MutableStateFlow<String?>(null)
    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    private fun mondayForOffset(offset: Int): LocalDate =
        LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())

    val weekLabel: StateFlow<String> = _weekOffset.map { offset ->
        val monday = mondayForOffset(offset)
        val sunday = monday.plusDays(6)
        val kw = monday.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
        val fmt = DateTimeFormatter.ofPattern("dd.MM.")
        "KW $kw · ${monday.format(fmt)}–${sunday.format(fmt)}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val days: StateFlow<List<WeekplanDayEntity>> = _weekOffset.flatMapLatest { offset ->
        val monday = mondayForOffset(offset)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        // Fenster bis zum Maximum der Zeitraum-Einstellung (7/10/14 Tage) statt fest auf
        // Mo-So begrenzt – sonst blieben Tage 8-14 bei einer 10/14-Tage-Einstellung
        // unsichtbar, und eine einmalige Verlängerung (addDayToWeek) hätte nichts anzuzeigen.
        repository.observeDaysBetween(
            startDate = monday.format(fmt),
            endDate = monday.plusDays(13).format(fmt),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ob für die aktuell angezeigte Woche noch ein weiterer Tag angehängt werden kann (Deckel 14). */
    val canExtendWeek: StateFlow<Boolean> = days.map { it.size < 14 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val weekplanRecipes: StateFlow<List<WeekplanRecipeEntity>> = _selectedDayId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.observeRecipesForDay(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekplanExtras: StateFlow<List<WeekplanExtraEntity>> = _selectedDayId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.observeExtrasForDay(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daySummaries: StateFlow<Map<String, DaySummary>> = days
        .flatMapLatest { dayList ->
            if (dayList.isEmpty()) flowOf(emptyMap())
            else {
                val countFlows = dayList.map { day ->
                    combine(
                        weekplanDao.observeRecipeCount(day.id),
                        weekplanDao.observeExtraCount(day.id),
                    ) { rc, ec -> day.id to DaySummary(rc, ec) }
                }
                combine(countFlows) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Rezepte aller Tage der Woche – damit jeder Tag direkt seine Rezepte zeigt
    val weekRecipes: StateFlow<Map<String, List<WeekplanRecipeEntity>>> = days
        .flatMapLatest { dayList ->
            if (dayList.isEmpty()) flowOf(emptyMap())
            else weekplanDao.observeRecipesForDays(dayList.map { it.id })
                .map { recipes -> recipes.groupBy { it.weekplanDayId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Extras aller Tage der Woche – direkt sichtbar ohne Tag anzuklicken
    val weekExtras: StateFlow<Map<String, List<WeekplanExtraEntity>>> = days
        .flatMapLatest { dayList ->
            if (dayList.isEmpty()) flowOf(emptyMap())
            else weekplanDao.observeExtrasForDays(dayList.map { it.id })
                .map { extras -> extras.groupBy { it.weekplanDayId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allRecipes: StateFlow<Map<String, RecipeEntity>> = recipeDao.observeAll()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weekBalance: StateFlow<WeekBalance> = combine(days, allRecipes) { dayList, recipesMap ->
        var meat = 0; var fish = 0; var veg = 0; var other = 0
        dayList.forEach { day ->
            weekplanDao.recipesForDay(day.id).forEach { wr ->
                val recipe = recipesMap[wr.recipeId]
                when (recipe?.proteinType?.lowercase()) {
                    in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> meat++
                    in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> fish++
                    in listOf("vegetarisch", "vegetarian", "vegan") -> veg++
                    else -> other++
                }
            }
        }
        WeekBalance(meat, fish, veg, other)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekBalance())

    val weekNutrition: StateFlow<WeekplanNutrition?> = combine(days, weekRecipes) { dayList, _ ->
        if (dayList.isEmpty()) null
        else repository.getWeekplanNutrition(dayList.first().planDate, dayList.last().planDate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exportEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportEvent = _exportEvent

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val userAllergies: StateFlow<List<String>> = preferences.allergies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shoppingLists: StateFlow<List<ShoppingListEntity>> = shoppingDao.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultShoppingListId: StateFlow<String> = preferences.defaultShoppingListId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _exportPreview = MutableStateFlow<ExportPreviewState?>(null)
    val exportPreview: StateFlow<ExportPreviewState?> = _exportPreview.asStateFlow()

    val selectedDayId: StateFlow<String?> = _selectedDayId

    val constraints: StateFlow<WeekplanConstraintsEntity> = weekplanConstraintsDao.observe()
        .map { it ?: WeekplanConstraintsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekplanConstraintsEntity())

    private val _generateStatus = MutableStateFlow<WeekplanGenerateStatus>(WeekplanGenerateStatus.Idle)
    val generateStatus: StateFlow<WeekplanGenerateStatus> = _generateStatus

    val feedbackForSelectedDay: StateFlow<Map<String, Int>> = _selectedDayId
        .flatMapLatest { dayId ->
            val planDate = days.value.find { it.id == dayId }?.planDate
            if (planDate == null) flowOf(emptyMap())
            else recipeFeedbackDao.observeForDate(planDate)
                .map { list -> list.associate { it.recipeId to it.liked } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun selectDay(id: String) {
        _selectedDayId.value = id
    }

    fun nextWeek() {
        _weekOffset.value++
        _selectedDayId.value = null
    }

    fun prevWeek() {
        _weekOffset.value--
        _selectedDayId.value = null
    }

    fun goToCurrentWeek() {
        _weekOffset.value = 0
        _selectedDayId.value = null
    }

    fun ensureWeek() {
        viewModelScope.launch {
            val dayCount = preferences.weekplanDays.first()
            val monday = mondayForOffset(_weekOffset.value)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            (0 until dayCount).forEach { offset ->
                repository.getOrCreateDay(monday.plusDays(offset.toLong()).format(fmt))
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun saveWeekplanSettings(planDays: Int, shoppingDay: Int) {
        viewModelScope.launch {
            val validDays = if (planDays in setOf(7, 10, 14)) planDays else 7
            val validShoppingDay = shoppingDay.coerceIn(0, 6)
            val now = System.currentTimeMillis()
            preferences.saveWeekplanDays(validDays)
            preferences.saveShoppingDay(validShoppingDay)
            weekplanSettingsDao.upsert(
                com.helga.android.data.local.entity.WeekplanSettingsEntity(
                    id = "global",
                    planDays = validDays,
                    shoppingDay = validShoppingDay,
                    updatedAt = now,
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun updateNote(dayId: String, note: String) {
        viewModelScope.launch {
            repository.updateNote(dayId, note)
            syncScheduler.triggerOneShot()
        }
    }

    fun toggleQuickDay(day: WeekplanDayEntity) {
        viewModelScope.launch {
            val updated = day.copy(
                isQuickDay = if (day.isQuickDay == 0) 1 else 0,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
            weekplanDao.upsertDay(updated)
            syncScheduler.triggerOneShot()
        }
    }

    fun toggleGuestDay(day: WeekplanDayEntity) {
        viewModelScope.launch {
            val updated = day.copy(
                isGuestDay = if (day.isGuestDay == 0) 1 else 0,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
            weekplanDao.upsertDay(updated)
            syncScheduler.triggerOneShot()
        }
    }

    /**
     * Markiert einen Tag als "kein Kochen nötig" oder hebt das wieder auf. Beim Aktivieren
     * werden bereits zugewiesene Rezepte/Extras entfernt (siehe [WeekplanRepository.setSkipped])
     * – der Aufrufer muss vorher warnen, falls der Tag noch belegt war.
     */
    fun toggleSkipped(day: WeekplanDayEntity) {
        viewModelScope.launch {
            repository.setSkipped(day.id, day.isSkipped == 0)
            syncScheduler.triggerOneShot()
        }
    }

    /**
     * Verlängert die aktuell angezeigte Woche einmalig um einen Tag (bis zu 14 Tage ab
     * Montag), ohne die globale Zeitraum-Einstellung zu ändern.
     */
    fun addDayToWeek() {
        viewModelScope.launch {
            val monday = mondayForOffset(_weekOffset.value)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val maxDate = monday.plusDays(13)
            val lastDate = days.value.maxOfOrNull { LocalDate.parse(it.planDate, fmt) }
                ?: monday.minusDays(1)
            if (lastDate >= maxDate) return@launch
            repository.getOrCreateDay(lastDate.plusDays(1).format(fmt))
            syncScheduler.triggerOneShot()
        }
    }

    fun setFeedback(recipeId: String, planDate: String, liked: Int) {
        viewModelScope.launch {
            val existing = recipeFeedbackDao.findByRecipeAndDate(recipeId, planDate)
            val newLiked = if (existing?.liked == liked) 0 else liked
            val entity = existing?.copy(
                liked = newLiked,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            ) ?: RecipeFeedbackEntity(
                id = java.util.UUID.randomUUID().toString(),
                recipeId = recipeId,
                plannedDate = planDate,
                liked = newLiked,
                updatedAt = System.currentTimeMillis(),
                dirty = 1,
            )
            recipeFeedbackDao.upsert(entity)
            recipeRepository.recalculateRating(recipeId)
            syncScheduler.triggerOneShot()
        }
    }

    fun addRecipe(dayId: String, recipeId: String) {
        viewModelScope.launch {
            repository.addRecipe(dayId, recipeId)
            val planDate = days.value.find { it.id == dayId }?.planDate
            if (planDate != null) recordHistory(recipeId, planDate)
            syncScheduler.triggerOneShot()
        }
    }

    fun removeRecipe(entry: WeekplanRecipeEntity) {
        viewModelScope.launch {
            repository.removeRecipe(entry)
            syncScheduler.triggerOneShot()
        }
    }

    fun addExtra(dayId: String, text: String) {
        viewModelScope.launch {
            repository.addExtra(dayId, text)
            syncScheduler.triggerOneShot()
        }
    }

    fun removeExtra(extra: WeekplanExtraEntity) {
        viewModelScope.launch {
            repository.removeExtra(extra)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteDay(dayId: String) {
        viewModelScope.launch {
            repository.deleteDay(dayId)
            if (_selectedDayId.value == dayId) _selectedDayId.value = null
            syncScheduler.triggerOneShot()
        }
    }

    /** Startet die Export-Vorschau für einen Tag oder die ganze Woche — schreibt noch nichts. */
    fun startExportPreview(dayId: String, shoppingListId: String, servings: Int = 0) {
        viewModelScope.launch {
            val dayIds = if (dayId == "all") days.value.map { it.id } else listOf(dayId)
            val items = repository.collectExportItems(dayIds, servings)
            _exportPreview.value = ExportPreviewState(items = items, listId = shoppingListId)
        }
    }

    /** Wechselt die Abwahl eines einzelnen Produkts in der Export-Vorschau. */
    fun toggleExportItem(key: String) {
        _exportPreview.update { state ->
            state?.copy(
                deselected = if (key in state.deselected) state.deselected - key else state.deselected + key,
            )
        }
    }

    /** Schreibt die in der Vorschau noch ausgewählten Produkte in die Einkaufsliste. */
    fun confirmExportPreview() {
        val state = _exportPreview.value ?: return
        viewModelScope.launch {
            val selected = state.items.filter { it.key !in state.deselected }
            repository.applyExportItems(selected, state.listId)
            syncScheduler.triggerOneShot()
            _exportPreview.value = null
        }
    }

    fun cancelExportPreview() {
        _exportPreview.value = null
    }

    fun saveConstraints(
        maxMeat: Int,
        maxFish: Int,
        minVeg: Int,
        maxRepeat: Int,
        maxKcalPerPortion: Int = 700,
        preferOrganic: Boolean = false,
        excludeAllergens: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val allergenJson = com.squareup.moshi.Moshi.Builder().build()
                .adapter(List::class.java).toJson(excludeAllergens)
            weekplanConstraintsDao.upsert(
                WeekplanConstraintsEntity(
                    id = "global",
                    maxMeatPerWeek = maxMeat,
                    maxFishPerWeek = maxFish,
                    minVegetarianPerWeek = minVeg,
                    maxRepeatDays = maxRepeat,
                    maxKcalPerPortion = maxKcalPerPortion,
                    preferOrganic = if (preferOrganic) 1 else 0,
                    excludeAllergens = allergenJson,
                    updatedAt = now,
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    /**
     * Gemeinsame Kandidaten-Filter für Generierung und Neuwürfeln: mealSlot (nur lunch/dinner,
     * behebt den Süßspeisen-als-Abendessen-Bug), Allergene, Kcal-Budget, Saison. Jede Stufe
     * fällt auf die vorherige zurück, wenn sie den Pool leer räumen würde. Der Saison-Filter war
     * vorher in generateWeekplan() nur eine Sortier-Präferenz (durch nachfolgende stabile
     * Sortierungen praktisch wirkungslos) — hier als echter Filter mit Fallback, damit er auch
     * bei regenerateDay()/regenerateProposalDay() (reiner Zufallsgriff, keine Sortierung)
     * tatsächlich etwas bewirkt.
     */
    private suspend fun applyRecipeFilters(
        candidates: List<RecipeEntity>,
        constraints: WeekplanConstraintsEntity,
        warnings: MutableList<String>? = null,
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
            val ingredientsByRecipe = recipeDao.allActiveIngredients().groupBy { it.recipeId }
            mealFilteredSafe.filter { recipe ->
                val foods = ingredientsByRecipe[recipe.id]?.map { it.food } ?: emptyList()
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

        val currentSeason = when (LocalDate.now().monthValue) {
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

    fun generateWeekplan() {
        _generateStatus.value = WeekplanGenerateStatus.Loading
        viewModelScope.launch {
            try {
                val c = constraints.value
                val dayCount = preferences.weekplanDays.first()
                val currentDays = days.value.take(dayCount).filter { it.isSkipped == 0 }
                if (currentDays.isEmpty()) {
                    _generateStatus.value = WeekplanGenerateStatus.Error("Keine Tage vorhanden")
                    return@launch
                }

                // --- Anker-Rezepte: Tage die bereits Rezepte haben, überspringen ---
                val anchorDays = mutableMapOf<String, List<WeekplanRecipeEntity>>()
                currentDays.forEach { day ->
                    val existing = weekplanDao.recipesForDay(day.id)
                    if (existing.isNotEmpty()) anchorDays[day.id] = existing
                }

                // Kürzlich geplante Rezepte ausschließen (Wiederholungssperre)
                val since = LocalDate.now().minusDays(c.maxRepeatDays.toLong())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                val recentIds = recipeHistoryDao.getRecentRecipeIds(since).toSet()

                // Feedback-Scores laden (positiv = bevorzugt, negativ = vermeiden)
                val allFeedback = recipeFeedbackDao.getAll()
                val feedbackScores = allFeedback
                    .groupBy { it.recipeId }
                    .mapValues { (_, entries) -> entries.sumOf { it.liked } }

                // Alle verfügbaren Rezepte
                val allRecipesList = allRecipes.value.values.toList()
                    .filter { it.deleted == 0 }
                val candidates = allRecipesList.filter { it.id !in recentIds }
                    .ifEmpty { allRecipesList } // Fallback wenn alle kürzlich geplant

                if (candidates.isEmpty()) {
                    _generateStatus.value = WeekplanGenerateStatus.Error("Keine Rezepte verfügbar")
                    return@launch
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
                    val recipe = allRecipes.value[wr.recipeId]
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
                        val recipe = anchorRecipe?.let { allRecipes.value[it.recipeId] }
                        assignments.add(
                            WeekplanAssignmentDto(
                                date = day.planDate,
                                recipeId = anchorRecipe?.recipeId ?: "",
                                recipeName = recipe?.name?.ifBlank { recipe.slug } ?: "Anker",
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

                _generateStatus.value = WeekplanGenerateStatus.Proposal(assignments, warnings)
            } catch (e: Exception) {
                _generateStatus.value = WeekplanGenerateStatus.Error(e.message ?: "Fehler bei der Generierung")
            }
        }
    }

    fun applyProposal(assignments: List<WeekplanAssignmentDto>) {
        viewModelScope.launch {
            assignments.forEach { assignment ->
                val day = days.value.find { it.planDate == assignment.date } ?: return@forEach
                val existing = repository.observeRecipesForDay(day.id).first()
                existing.forEach { entry ->
                    repository.removeRecipe(entry)
                }
                repository.addRecipe(day.id, assignment.recipeId)
                recordHistory(assignment.recipeId, assignment.date)
            }
            _generateStatus.value = WeekplanGenerateStatus.Idle
            syncScheduler.triggerOneShot()
            _exportEvent.tryEmit("applied")
        }
    }

    fun discardProposal() {
        _generateStatus.value = WeekplanGenerateStatus.Idle
    }

    fun regenerateProposalDay(index: Int) {
        val current = (_generateStatus.value as? WeekplanGenerateStatus.Proposal) ?: return
        val assignments = current.assignments.toMutableList()
        val target = assignments[index]

        viewModelScope.launch {
            val c = constraints.value
            val recipesMap = allRecipes.value

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

            val since = LocalDate.now().minusDays(c.maxRepeatDays.toLong())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
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
            val day = days.value.find { it.planDate == target.date }
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

            val chosen = effortFiltered.shuffled().firstOrNull() ?: return@launch
            assignments[index] = WeekplanAssignmentDto(
                date = target.date,
                recipeId = chosen.id,
                recipeName = chosen.name.ifBlank { chosen.slug },
            )
            val currentProposal = (_generateStatus.value as? WeekplanGenerateStatus.Proposal)
            _generateStatus.value = WeekplanGenerateStatus.Proposal(assignments, currentProposal?.warnings ?: emptyList())
        }
    }

    fun regenerateDay(dayId: String) {
        viewModelScope.launch {
            val day = days.value.find { it.id == dayId } ?: return@launch
            if (day.isSkipped == 1) return@launch
            val c = constraints.value

            // Sammle alle aktuellen Rezept-IDs der Woche (außer dem zu ersetzenden Tag)
            val otherDayRecipeIds = days.value
                .filter { it.id != dayId }
                .flatMap { d -> weekplanDao.recipesForDay(d.id).map { it.recipeId } }
                .toSet()

            // Zähle aktuelle Bilanz (ohne den zu ersetzenden Tag)
            val recipesMap = allRecipes.value
            var meatCount = 0; var fishCount = 0; var vegCount = 0
            otherDayRecipeIds.forEach { id ->
                when (recipesMap[id]?.proteinType?.lowercase()) {
                    in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> meatCount++
                    in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> fishCount++
                    in listOf("vegetarisch", "vegetarian", "vegan") -> vegCount++
                }
            }

            val since = LocalDate.now().minusDays(c.maxRepeatDays.toLong())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
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

            val chosen = effortFiltered.shuffled().firstOrNull() ?: return@launch

            // Bestehende Rezepte des Tages entfernen
            weekplanDao.recipesForDay(dayId).forEach { repository.removeRecipe(it) }
            repository.addRecipe(dayId, chosen.id)
            recordHistory(chosen.id, day.planDate)
            syncScheduler.triggerOneShot()
        }
    }

    fun repeatLastWeek() {
        _generateStatus.value = WeekplanGenerateStatus.Loading
        viewModelScope.launch {
            try {
                val monday = mondayForOffset(_weekOffset.value)
                val lastMonday = monday.minusWeeks(1)
                val fmt = DateTimeFormatter.ISO_LOCAL_DATE
                val lastDays = weekplanDao.getDaysBetween(
                    lastMonday.format(fmt),
                    lastMonday.plusDays(6).format(fmt),
                )
                if (lastDays.isEmpty()) {
                    _generateStatus.value = WeekplanGenerateStatus.Error("Keine Vorwoche gefunden")
                    return@launch
                }
                val assignments = mutableListOf<WeekplanAssignmentDto>()
                val currentDays = days.value
                lastDays.forEach { lastDay ->
                    val lastDate = LocalDate.parse(lastDay.planDate, fmt)
                    val dayOfWeek = lastDate.dayOfWeek
                    val newDate = monday.with(dayOfWeek)
                    val matchingCurrentDay = currentDays.find { it.planDate == newDate.format(fmt) }
                    if (matchingCurrentDay != null) {
                        val recipes = weekplanDao.recipesForDay(lastDay.id)
                        val firstRecipe = recipes.firstOrNull() ?: return@forEach
                        val recipe = allRecipes.value[firstRecipe.recipeId]
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
                    _generateStatus.value = WeekplanGenerateStatus.Error("Vorwoche hat keine Rezepte")
                    return@launch
                }
                _generateStatus.value = WeekplanGenerateStatus.Proposal(assignments, emptyList())
            } catch (e: Exception) {
                _generateStatus.value = WeekplanGenerateStatus.Error(e.message ?: "Fehler")
            }
        }
    }

    fun generateWithAnchors(startDate: String) {
        generateWeekplan()
    }

    private suspend fun recordHistory(recipeId: String, planDate: String) {
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

    suspend fun suggestItems(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return try {
            apiFactory.api().suggestItems(query).suggestions
        } catch (_: Exception) {
            emptyList()
        }
    }
}
