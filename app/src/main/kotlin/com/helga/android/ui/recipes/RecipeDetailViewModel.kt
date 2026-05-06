package com.helga.android.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeDetailUiState(
    val recipe: RecipeEntity? = null,
    val ingredients: List<IngredientEntity> = emptyList(),
    val instructions: List<InstructionEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val isClassifying: Boolean = false,
    val classifyError: String? = null,
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
    private val apiFactory: SyncApiFactory,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val recipeId: String = checkNotNull(savedStateHandle["recipeId"])

    private val _classifyState = MutableStateFlow(false to (null as String?))

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        repository.observeById(recipeId),
        repository.observeIngredients(recipeId),
        repository.observeInstructions(recipeId),
        repository.observeTags(recipeId),
        _classifyState,
    ) { recipe, ingredients, instructions, tags, (isClassifying, classifyError) ->
        RecipeDetailUiState(recipe, ingredients, instructions, tags, isClassifying, classifyError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val shoppingLists: StateFlow<List<ShoppingListEntity>> = shoppingRepository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRating(rating: Int) {
        viewModelScope.launch {
            repository.updateRating(recipeId, rating)
            syncScheduler.triggerOneShot()
        }
    }

    fun toggleFavorite() {
        val recipe = uiState.value.recipe ?: return
        viewModelScope.launch {
            repository.toggleFavorite(recipe)
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

    fun classify() {
        val state = uiState.value
        val recipe = state.recipe ?: return
        _classifyState.update { true to null }
        viewModelScope.launch {
            try {
                val api = apiFactory.api()
                val req = AiClassifyRequest(
                    name = recipe.name,
                    description = recipe.description,
                    tags = state.tags.map { it.name },
                    ingredients = state.ingredients.map { it.food },
                )
                val result = api.classifyRecipe(req)
                repository.upsertLocal(
                    recipe.copy(
                        proteinType = result.proteinType.ifBlank { recipe.proteinType },
                        effort = result.effort.ifBlank { recipe.effort },
                        cuisine = result.cuisine.ifBlank { recipe.cuisine },
                        mealType = result.mealType.ifBlank { recipe.mealType },
                        seasonFit = result.seasonFit.ifBlank { recipe.seasonFit },
                    )
                )
                syncScheduler.triggerOneShot()
                _classifyState.update { false to null }
            } catch (e: Exception) {
                _classifyState.update { false to (e.message ?: "Klassifikation fehlgeschlagen") }
            }
        }
    }

    fun exportToShoppingList(listId: String) {
        viewModelScope.launch {
            repository.exportToShoppingList(recipeId, listId)
            syncScheduler.triggerOneShot()
        }
    }

    suspend fun defaultShoppingListId(): String? {
        val preferred: String = preferences.defaultShoppingListId.first()
        if (preferred.isNotBlank()) return preferred
        return shoppingLists.value.firstOrNull()?.id
    }
}
