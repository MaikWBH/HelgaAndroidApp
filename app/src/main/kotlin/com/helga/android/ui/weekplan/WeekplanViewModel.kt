package com.helga.android.ui.weekplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanDayMarkerEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.model.WeekplanExportItem
import com.helga.android.data.model.WeekplanNutrition
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.remote.dto.WeekplanAssignmentDto
import com.helga.android.data.weekplan.WeekplanGenerateStatus
import com.helga.android.data.weekplan.WeekplanGenerator
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
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
data class DaySummary(val recipeCount: Int, val extraCount: Int)

data class WeekBalance(val meat: Int = 0, val fish: Int = 0, val veg: Int = 0, val other: Int = 0)

/**
 * Zählt Protein-Typen über alle Rezepte einer Woche (wochenplan A5 — als eigene Funktion
 * ausgelagert, damit die Zähllogik ohne Room/DAO testbar ist).
 */
fun computeWeekBalance(
    recipesByDay: Map<String, List<WeekplanRecipeEntity>>,
    recipesById: Map<String, RecipeEntity>,
): WeekBalance {
    var meat = 0; var fish = 0; var veg = 0; var other = 0
    recipesByDay.values.flatten().forEach { wr ->
        val recipe = recipesById[wr.recipeId]
        when (recipe?.proteinType?.lowercase()) {
            in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> meat++
            in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> fish++
            in listOf("vegetarisch", "vegetarian", "vegan") -> veg++
            else -> other++
        }
    }
    return WeekBalance(meat, fish, veg, other)
}

/**
 * Export-Vorschau: alle Kandidaten (Rezeptzutaten + Extras) für die gewählten Tage, bevor sie in
 * die Einkaufsliste geschrieben werden. `deselected` hält die per Checkbox abgewählten Items.
 */
data class ExportPreviewState(
    val items: List<WeekplanExportItem>,
    val listId: String,
    val deselected: Set<String> = emptySet(),
)


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
    private val shoppingRepository: ShoppingRepository,
    private val generator: WeekplanGenerator,
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

    // Marker-Bibliothek (wochenplan A11) – nutzerdefinierte, wiederverwendbare Tagesmarker.
    val allMarkers: StateFlow<List<WeekplanDayMarkerEntity>> = repository.observeMarkers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Marker je Tag der Woche, aufgelöst auf die volle Entity (Name+Farbe) über die Bibliothek.
    val weekMarkers: StateFlow<Map<String, List<WeekplanDayMarkerEntity>>> = combine(
        days.flatMapLatest { dayList ->
            if (dayList.isEmpty()) flowOf(emptyList())
            else repository.observeMarkerAssignmentsForDays(dayList.map { it.id })
        },
        allMarkers,
    ) { assignments, markers ->
        val markerById = markers.associateBy { it.id }
        assignments
            .mapNotNull { assignment -> markerById[assignment.markerId]?.let { assignment.weekplanDayId to it } }
            .groupBy({ it.first }, { it.second })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allRecipes: StateFlow<Map<String, RecipeEntity>> = recipeDao.observeAll()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weekBalance: StateFlow<WeekBalance> = combine(days, allRecipes) { dayList, recipesMap ->
        val recipesByDay = dayList.associate { it.id to weekplanDao.recipesForDay(it.id) }
        computeWeekBalance(recipesByDay, recipesMap)
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
     * Sperrt einen Tag gegen Überschreiben durch Wochenplan-Generierung und Reroll, oder hebt
     * die Sperre wieder auf (wochenplan A10 — Ankerrezepte). Ersetzt den bisherigen impliziten
     * Mechanismus ("jeder Tag mit Rezept bleibt automatisch stehen"): ohne Sperre wird ein Tag
     * bei „Woche generieren" jetzt auch dann neu belegt, wenn er bereits ein Rezept hat.
     */
    fun toggleLocked(day: WeekplanDayEntity) {
        viewModelScope.launch {
            weekplanDao.setLocked(day.id, if (day.isLocked == 0) 1 else 0, System.currentTimeMillis())
            syncScheduler.triggerOneShot()
        }
    }

    /** Wiederverwendbare Tagesmarker-Bibliothek pflegen (wochenplan A11). */
    fun createMarker(name: String, color: String) {
        viewModelScope.launch {
            repository.createMarker(name, color)
            syncScheduler.triggerOneShot()
        }
    }

    fun updateMarker(marker: WeekplanDayMarkerEntity, name: String, color: String) {
        viewModelScope.launch {
            repository.updateMarker(marker, name, color)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteMarker(marker: WeekplanDayMarkerEntity) {
        viewModelScope.launch {
            repository.deleteMarker(marker)
            syncScheduler.triggerOneShot()
        }
    }

    /** Ordnet einen Marker dem gewählten Tag zu oder entfernt ihn wieder. */
    fun toggleMarkerOnDay(dayId: String, markerId: String) {
        viewModelScope.launch {
            repository.toggleMarkerOnDay(dayId, markerId)
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
            if (planDate != null) generator.recordHistory(recipeId, planDate)
            syncScheduler.triggerOneShot()
        }
    }

    fun removeRecipe(entry: WeekplanRecipeEntity) {
        viewModelScope.launch {
            repository.removeRecipe(entry)
            syncScheduler.triggerOneShot()
        }
    }

    fun setMealSlot(entry: WeekplanRecipeEntity, mealSlot: String) {
        viewModelScope.launch {
            repository.setMealSlot(entry, mealSlot)
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

    fun generateWeekplan() {
        _generateStatus.value = WeekplanGenerateStatus.Loading
        viewModelScope.launch {
            _generateStatus.value = generator.generate(days.value, allRecipes.value, constraints.value)
        }
    }

    fun applyProposal(assignments: List<WeekplanAssignmentDto>) {
        viewModelScope.launch {
            generator.applyProposal(assignments, days.value)
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
        if (assignments[index].isLocked) return

        viewModelScope.launch {
            val replacement = generator.pickProposalReplacement(
                assignments, index, days.value, allRecipes.value, constraints.value,
            ) ?: return@launch
            assignments[index] = replacement
            val currentProposal = (_generateStatus.value as? WeekplanGenerateStatus.Proposal)
            _generateStatus.value = WeekplanGenerateStatus.Proposal(assignments, currentProposal?.warnings ?: emptyList())
        }
    }

    fun regenerateDay(dayId: String) {
        viewModelScope.launch {
            if (generator.regenerateDay(dayId, days.value, allRecipes.value, constraints.value)) {
                syncScheduler.triggerOneShot()
            }
        }
    }

    fun repeatLastWeek() {
        _generateStatus.value = WeekplanGenerateStatus.Loading
        viewModelScope.launch {
            _generateStatus.value = generator.repeatLastWeek(
                mondayForOffset(_weekOffset.value), days.value, allRecipes.value,
            )
        }
    }

    suspend fun suggestItems(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return shoppingRepository.suggestItems(query)
    }
}
