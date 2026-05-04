package com.helga.android.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RecipeCookUiState(
    val recipe: RecipeEntity? = null,
    val instructions: List<InstructionEntity> = emptyList(),
)

@HiltViewModel
class RecipeCookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: RecipeRepository,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedStateHandle["recipeId"])

    val uiState: StateFlow<RecipeCookUiState> = combine(
        repository.observeById(recipeId),
        repository.observeInstructions(recipeId),
    ) { recipe, instructions ->
        RecipeCookUiState(recipe, instructions.sortedBy { it.position })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeCookUiState())
}
