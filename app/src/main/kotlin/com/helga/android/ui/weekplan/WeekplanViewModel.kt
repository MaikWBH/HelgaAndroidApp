package com.helga.android.ui.weekplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntryEntity
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.WeekplanAssignmentDto
import com.helga.android.data.remote.dto.WeekplanGenerateRequest
import com.helga.android.data.repository.WeekplanRepository
import com.helga.android.data.repository.WeekplanTemplateRepository
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

sealed interface WeekplanGenerateStatus {
    data object Idle : WeekplanGenerateStatus
    data object Loading : WeekplanGenerateStatus
    data class Proposal(val assignments: List<WeekplanAssignmentDto>) : WeekplanGenerateStatus
    data class Error(val message: String) : WeekplanGenerateStatus
}

@HiltViewModel
class WeekplanViewModel @Inject constructor(
    private val repository: WeekplanRepository,
    private val templateRepository: WeekplanTemplateRepository,
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val weekplanDao: WeekplanDao,
    private val weekplanSettingsDao: WeekplanSettingsDao,
    private val weekplanConstraintsDao: WeekplanConstraintsDao,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val apiFactory: SyncApiFactory,
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
        repository.observeDaysBetween(
            startDate = monday.format(fmt),
            endDate = monday.plusDays(6).format(fmt),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val allRecipes: StateFlow<List<RecipeEntity>> = recipeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val shoppingLists: StateFlow<List<ShoppingListEntity>> = shoppingDao.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedDayId: StateFlow<String?> = _selectedDayId

    val constraints: StateFlow<WeekplanConstraintsEntity> = weekplanConstraintsDao.observe()
        .map { it ?: WeekplanConstraintsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekplanConstraintsEntity())

    private val _generateStatus = MutableStateFlow<WeekplanGenerateStatus>(WeekplanGenerateStatus.Idle)
    val generateStatus: StateFlow<WeekplanGenerateStatus> = _generateStatus

    val templates: StateFlow<List<WeekplanTemplateEntity>> = templateRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun addRecipe(dayId: String, recipeId: String) {
        viewModelScope.launch {
            repository.addRecipe(dayId, recipeId)
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

    fun exportToShoppingList(dayId: String, shoppingListId: String) {
        viewModelScope.launch {
            repository.exportToShoppingList(listOf(dayId), shoppingListId)
            syncScheduler.triggerOneShot()
        }
    }

    fun exportWeekToShoppingList(shoppingListId: String) {
        viewModelScope.launch {
            val dayIds = days.value.map { it.id }
            repository.exportToShoppingList(dayIds, shoppingListId)
            syncScheduler.triggerOneShot()
        }
    }

    fun saveConstraints(maxMeat: Int, minVeg: Int, maxRepeat: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            weekplanConstraintsDao.upsert(
                WeekplanConstraintsEntity(
                    id = "global",
                    maxMeatPerWeek = maxMeat,
                    minVegetarianPerWeek = minVeg,
                    maxRepeatDays = maxRepeat,
                    updatedAt = now,
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun generateWeekplan(startDate: String) {
        val c = constraints.value
        _generateStatus.value = WeekplanGenerateStatus.Loading
        viewModelScope.launch {
            try {
                val dayCount = preferences.weekplanDays.first()
                val api = apiFactory.api()
                val response = api.generateWeekplan(
                    WeekplanGenerateRequest(
                        startDate = startDate,
                        planDays = dayCount,
                        maxMeatPerWeek = c.maxMeatPerWeek,
                        minVegetarianPerWeek = c.minVegetarianPerWeek,
                        maxRepeatDays = c.maxRepeatDays,
                    )
                )
                if (response.assignments.isEmpty()) {
                    _generateStatus.value = WeekplanGenerateStatus.Error("Keine passenden Rezepte gefunden")
                } else {
                    _generateStatus.value = WeekplanGenerateStatus.Proposal(response.assignments)
                }
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
            }
            _generateStatus.value = WeekplanGenerateStatus.Idle
            syncScheduler.triggerOneShot()
        }
    }

    fun discardProposal() {
        _generateStatus.value = WeekplanGenerateStatus.Idle
    }

    fun saveCurrentWeekAsTemplate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val monday = mondayForOffset(_weekOffset.value)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val templateId = UUID.randomUUID().toString()
            val entries = mutableListOf<WeekplanTemplateEntryEntity>()
            days.value.forEach { day ->
                val dayDate = LocalDate.parse(day.planDate, fmt)
                val dayOffset = ChronoUnit.DAYS.between(monday, dayDate).toInt()
                weekplanDao.recipesForDay(day.id).forEachIndexed { pos, recipe ->
                    entries.add(
                        WeekplanTemplateEntryEntity(
                            id = UUID.randomUUID().toString(),
                            templateId = templateId,
                            dayOffset = dayOffset,
                            recipeId = recipe.recipeId,
                            position = pos,
                        )
                    )
                }
            }
            templateRepository.save(id = templateId, name = name, entries = entries)
        }
    }

    fun applyTemplate(templateId: String) {
        viewModelScope.launch {
            val monday = mondayForOffset(_weekOffset.value)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val entries = templateRepository.entriesForTemplate(templateId)
            entries.map { it.dayOffset }.toSet().forEach { offset ->
                val day = repository.getOrCreateDay(monday.plusDays(offset.toLong()).format(fmt))
                weekplanDao.recipesForDay(day.id).forEach { repository.removeRecipe(it) }
            }
            entries.forEach { entry ->
                val day = repository.getOrCreateDay(monday.plusDays(entry.dayOffset.toLong()).format(fmt))
                repository.addRecipe(day.id, entry.recipeId)
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch { templateRepository.delete(templateId) }
    }
}
