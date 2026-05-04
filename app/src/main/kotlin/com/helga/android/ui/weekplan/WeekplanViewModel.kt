package com.helga.android.ui.weekplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.repository.WeekplanRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
data class DaySummary(val recipeCount: Int, val extraCount: Int)

@HiltViewModel
class WeekplanViewModel @Inject constructor(
    private val repository: WeekplanRepository,
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val weekplanDao: WeekplanDao,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _selectedDayId = MutableStateFlow<String?>(null)

    val days: StateFlow<List<WeekplanDayEntity>> = repository.observeDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val shoppingLists: StateFlow<List<ShoppingListEntity>> = shoppingDao.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedDayId: StateFlow<String?> = _selectedDayId

    fun selectDay(id: String) {
        _selectedDayId.value = id
    }

    fun ensureWeek() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val monday = today.with(java.time.DayOfWeek.MONDAY)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            (0..6).forEach { offset ->
                repository.getOrCreateDay(monday.plusDays(offset.toLong()).format(fmt))
            }
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
}
