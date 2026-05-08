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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _baseServings = MutableStateFlow(0)
    private val _servings = MutableStateFlow(0)
    val servings: StateFlow<Int> = _servings.asStateFlow()
    val baseServings: StateFlow<Int> = _baseServings.asStateFlow()
    val scaleFactor: StateFlow<Float> = combine(_servings, _baseServings) { s, base ->
        if (base > 0 && s > 0) s.toFloat() / base.toFloat() else 1f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

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

    init {
        viewModelScope.launch {
            uiState.filter { it.recipe != null }.first().recipe?.let { recipe ->
                val base = Regex("""\d+""").find(recipe.recipeYield)?.value?.toIntOrNull() ?: 0
                if (base > 0) {
                    _baseServings.value = base
                    _servings.value = base
                }
            }
        }
    }

    fun setServings(n: Int) { _servings.value = n.coerceIn(1, 99) }

    fun toggleIngredient(id: String) {
        _checkedIds.value = _checkedIds.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun toggleStep(index: Int) {
        _completedSteps.update { if (index in it) it - index else it + index }
    }
}
