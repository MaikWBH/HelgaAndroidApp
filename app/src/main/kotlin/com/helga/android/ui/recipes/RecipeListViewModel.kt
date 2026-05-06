package com.helga.android.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.sync.SyncStatus
import com.helga.android.data.sync.SyncStatusHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SortOrder { NAME, RATING, UPDATED }

@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val repository: RecipeRepository,
    syncStatusHolder: SyncStatusHolder,
    private val syncScheduler: SyncScheduler,
    preferences: AppPreferences,
) : ViewModel() {

    val selectedTag = MutableStateFlow<String?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NAME)
    val searchQuery = MutableStateFlow("")
    val showFavoritesOnly = MutableStateFlow(false)

    val allTagNames: StateFlow<List<String>> = repository.observeAllTagNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val recipes: StateFlow<List<RecipeEntity>> = combine(
        repository.observeAll(),
        selectedTag.flatMapLatest { tag ->
            if (tag == null) flowOf(null)
            else repository.observeRecipeIdsByTag(tag).map { it.toSet() }
        },
        sortOrder,
    ) { all, tagFilter, sort ->
        val filtered = if (tagFilter == null) all else all.filter { it.id in tagFilter }
        when (sort) {
            SortOrder.NAME -> filtered
            SortOrder.RATING -> filtered.sortedByDescending { it.rating }
            SortOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt }
        }
    }.combine(searchQuery) { recipes, query ->
        if (query.isBlank()) recipes
        else recipes.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
    }.combine(showFavoritesOnly) { recipes, favOnly ->
        if (favOnly) recipes.filter { it.isFavorite == 1 } else recipes
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncStatus: StateFlow<SyncStatus> = syncStatusHolder.status

    fun selectTag(tag: String?) { selectedTag.value = tag }
    fun setSortOrder(order: SortOrder) { sortOrder.value = order }
    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun toggleFavoritesFilter() { showFavoritesOnly.value = !showFavoritesOnly.value }
    fun refresh() = syncScheduler.triggerOneShot()
}
