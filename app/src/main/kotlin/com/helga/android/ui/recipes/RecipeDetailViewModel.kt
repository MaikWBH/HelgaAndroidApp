package com.helga.android.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeDetailUiState(
    val recipe: RecipeEntity? = null,
    val ingredients: List<IngredientEntity> = emptyList(),
    val instructions: List<InstructionEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val recipeId: String = checkNotNull(savedStateHandle["recipeId"])

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        repository.observeById(recipeId),
        repository.observeIngredients(recipeId),
        repository.observeInstructions(recipeId),
        repository.observeTags(recipeId),
    ) { recipe, ingredients, instructions, tags ->
        RecipeDetailUiState(recipe, ingredients, instructions, tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setRating(rating: Int) {
        viewModelScope.launch {
            repository.updateRating(recipeId, rating)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteRecipe(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val recipe = repository.findById(recipeId) ?: return@launch
            repository.softDelete(recipe)
            syncScheduler.triggerOneShot()
            onDeleted()
        }
    }
}
