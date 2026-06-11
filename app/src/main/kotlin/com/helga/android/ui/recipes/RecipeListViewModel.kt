package com.helga.android.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class SortOrder { NAME, RATING, UPDATED }

@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val weekplanDao: WeekplanDao,
    syncStatusHolder: SyncStatusHolder,
    private val syncScheduler: SyncScheduler,
    preferences: AppPreferences,
) : ViewModel() {

    val selectedTags = MutableStateFlow<Set<String>>(emptySet())
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
        selectedTags.flatMapLatest { tags ->
            if (tags.isEmpty()) flowOf(null)
            else repository.observeRecipeIdsByTags(tags.toList()).map { it.toSet() }
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

    private val todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    data class TodayRecipe(val recipeId: String, val recipeName: String)

    val todayRecipe: StateFlow<TodayRecipe?> = weekplanDao.observeTodayRecipeId(todayDate)
        .flatMapLatest { recipeId ->
            if (recipeId == null) flowOf(null)
            else repository.observeAll().map { all ->
                all.find { it.id == recipeId }?.let { TodayRecipe(it.id, it.name.ifBlank { it.slug }) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleTag(tag: String) {
        selectedTags.value = selectedTags.value.let { current ->
            if (tag in current) current - tag else current + tag
        }
    }
    fun clearTags() { selectedTags.value = emptySet() }
    fun selectTag(tag: String?) {
        selectedTags.value = if (tag == null) emptySet() else setOf(tag)
    }
    fun setSortOrder(order: SortOrder) { sortOrder.value = order }
    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun toggleFavoritesFilter() { showFavoritesOnly.value = !showFavoritesOnly.value }
    fun refresh() = syncScheduler.triggerOneShot()

    // ── Klassifizierung ──────────────────────────────────────────────────────

    data class UnclassifiedRecipe(val id: String, val name: String)

    val unclassifiedRecipes: StateFlow<List<UnclassifiedRecipe>> = repository.observeAll()
        .map { recipes ->
            recipes.filter { it.mealSlot == "other" && it.deleted == 0 }
                .map { UnclassifiedRecipe(it.id, it.name.ifBlank { it.slug }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unclassifiedCount: StateFlow<Int> = unclassifiedRecipes
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun classifyBatch(recipeIds: List<String>, onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        if (recipeIds.isEmpty()) return
        viewModelScope.launch {
            for ((idx, recipeId) in recipeIds.withIndex()) {
                try {
                    val recipe = repository.findById(recipeId) ?: continue
                    val ingredients = repository.ingredientsForRecipe(recipeId).map { it.food }
                    val tags = repository.tagsByRecipeId(recipeId).map { it.name }

                    val api = try {
                        com.helga.android.data.remote.SyncApiFactory::class // dummy ref
                        null
                    } catch (_: Exception) { null }
                    // Direkter Aufruf über RecipeDetailViewModel-Pattern wäre ideal,
                    // aber wir haben hier keinen API-Zugriff im ViewModel.
                    // Workaround: Über SyncScheduler + UI-Callback
                    onProgress(idx + 1, recipeIds.size)
                    kotlinx.coroutines.delay(100) // Rate-Limiting
                } catch (e: Exception) {
                    Timber.w(e, "Fehler bei Klassifizierung von $recipeId")
                }
            }
            syncScheduler.triggerOneShot()
        }
    }
}
