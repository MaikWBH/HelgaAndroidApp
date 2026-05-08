package com.helga.android.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class RecipeCookUiState(
    val recipe: RecipeEntity? = null,
    val instructions: List<InstructionEntity> = emptyList(),
    val ingredients: List<IngredientEntity> = emptyList(),
    val checkedIngredientIds: Set<String> = emptySet(),
)

@HiltViewModel
class RecipeCookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: RecipeRepository,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedStateHandle["recipeId"])
    private val _checkedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _completedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val completedSteps: StateFlow<Set<Int>> = _completedSteps.asStateFlow()

    val uiState: StateFlow<RecipeCookUiState> = combine(
        repository.observeById(recipeId),
        repository.observeInstructions(recipeId),
        repository.observeIngredients(recipeId),
        _checkedIds,
    ) { recipe, instructions, ingredients, checked ->
        RecipeCookUiState(
            recipe = recipe,
            instructions = instructions.sortedBy { it.position },
            ingredients = ingredients.filter { it.deleted == 0 }.sortedBy { it.position },
            checkedIngredientIds = checked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeCookUiState())

    fun toggleIngredient(id: String) {
        _checkedIds.value = _checkedIds.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun toggleStep(index: Int) {
        _completedSteps.update { if (index in it) it - index else it + index }
    }
}
