package com.helga.android.ui.weekplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.WeekplanRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.ui.recipes.SortOrder
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekplanRecipePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    private val weekplanRepository: WeekplanRepository,
    private val syncScheduler: SyncScheduler,
    preferences: AppPreferences,
) : ViewModel() {

    val dayId: String = checkNotNull(savedStateHandle["dayId"])

    val selectedTag = MutableStateFlow<String?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NAME)

    val allTagNames: StateFlow<List<String>> = recipeRepository.observeAllTagNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val recipes: StateFlow<List<RecipeEntity>> = combine(
        recipeRepository.observeAll(),
        selectedTag.flatMapLatest { tag ->
            if (tag == null) flowOf(null)
            else recipeRepository.observeRecipeIdsByTag(tag).map { it.toSet() }
        },
        sortOrder,
    ) { all, tagFilter, sort ->
        val filtered = if (tagFilter == null) all else all.filter { it.id in tagFilter }
        when (sort) {
            SortOrder.NAME -> filtered
            SortOrder.RATING -> filtered.sortedByDescending { it.rating }
            SortOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addRecipe(recipeId: String) {
        viewModelScope.launch {
            weekplanRepository.addRecipe(dayId, recipeId)
            syncScheduler.triggerOneShot()
        }
    }

    fun selectTag(tag: String?) { selectedTag.value = tag }
    fun setSortOrder(order: SortOrder) { sortOrder.value = order }
}
