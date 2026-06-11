package com.helga.android.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.ImportedRecipeDto
import com.helga.android.data.remote.dto.UrlImportRequest
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ImportStatus {
    data object Idle : ImportStatus
    data object Loading : ImportStatus
    data class Success(val recipe: ImportedRecipeDto) : ImportStatus
    data class Error(val message: String) : ImportStatus
}

data class UrlImportState(
    val url: String = "",
    val status: ImportStatus = ImportStatus.Idle,
    val isSaving: Boolean = false,
)

@HiltViewModel
class UrlImportViewModel @Inject constructor(
    private val apiFactory: SyncApiFactory,
    private val repository: RecipeRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(UrlImportState())
    val state: StateFlow<UrlImportState> = _state.asStateFlow()

    fun setUrl(url: String) = _state.update { it.copy(url = url, status = ImportStatus.Idle) }

    fun prefillUrl(url: String) {
        if (_state.value.url.isBlank()) _state.update { it.copy(url = url) }
    }

    fun import() {
        val url = _state.value.url.trim()
        if (url.isBlank()) return
        _state.update { it.copy(status = ImportStatus.Loading) }
        viewModelScope.launch {
            try {
                val api = apiFactory.api()
                val result = api.importFromUrl(UrlImportRequest(url))
                _state.update { it.copy(status = ImportStatus.Success(result)) }
            } catch (e: Exception) {
                _state.update { it.copy(status = ImportStatus.Error(e.message ?: "Unbekannter Fehler")) }
            }
        }
    }

    fun save(recipe: ImportedRecipeDto, onSuccess: () -> Unit) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entity = RecipeEntity(
                id = id,
                slug = id,
                name = recipe.name,
                description = recipe.description,
                recipeYield = recipe.recipeYield,
                prepTime = recipe.prepTime,
                cookTime = recipe.cookTime,
                totalTime = recipe.totalTime,
                cuisine = recipe.cuisine,
                mealSlot = "other",
                effort = recipe.effort,
                proteinType = recipe.proteinType,
                seasonFit = recipe.seasonFit,
                sourceUrl = recipe.sourceUrl,
                localImageUri = "",
                createdAt = now,
            )
            val ingredients = recipe.ingredients.mapIndexed { idx, ing ->
                IngredientEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = id,
                    position = idx,
                    quantity = ing.quantity,
                    unit = ing.unit,
                    food = ing.food,
                    note = ing.note,
                )
            }
            val instructions = recipe.instructions.mapIndexed { idx, ins ->
                InstructionEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = id,
                    position = idx + 1,
                    text = ins.text,
                )
            }
            val tags = recipe.tags.map { name ->
                TagEntity(id = UUID.randomUUID().toString(), recipeId = id, name = name)
            }
            repository.saveRecipe(entity, ingredients, instructions, tags)
            syncScheduler.triggerOneShot()
            _state.update { it.copy(isSaving = false) }

            // Auto-Klassifizierung: Nach dem Speichern im Hintergrund klassifizieren
            classifyRecipeAsync(entity, ingredients, tags)

            onSuccess()
        }
    }

    private fun classifyRecipeAsync(recipe: RecipeEntity, ingredients: List<IngredientEntity>, tags: List<TagEntity>) {
        viewModelScope.launch {
            try {
                val api = apiFactory.api()
                val result = api.classifyRecipe(
                    com.helga.android.data.remote.dto.AiClassifyRequest(
                        name = recipe.name,
                        description = recipe.description,
                        tags = tags.map { it.name },
                        ingredients = ingredients.map { it.food },
                    )
                )
                val hasData = result.mealSlot.isNotBlank() || result.proteinType.isNotBlank() ||
                    result.effort.isNotBlank() || result.cuisine.isNotBlank() || result.seasonFit.isNotBlank()
                if (hasData) {
                    repository.upsertLocal(
                        recipe.copy(
                            mealSlot = result.mealSlot.ifBlank { recipe.mealSlot },
                            proteinType = result.proteinType.ifBlank { recipe.proteinType },
                            effort = result.effort.ifBlank { recipe.effort },
                            cuisine = result.cuisine.ifBlank { recipe.cuisine },
                            seasonFit = result.seasonFit.ifBlank { recipe.seasonFit },
                        )
                    )
                    syncScheduler.triggerOneShot()
                }
            } catch (_: Exception) {
                // Stillschweigend fehlschlagen — Rezept wurde bereits gespeichert
            }
        }
    }
}
