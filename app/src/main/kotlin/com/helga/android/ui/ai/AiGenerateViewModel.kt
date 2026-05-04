package com.helga.android.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.remote.SseClient
import com.helga.android.data.remote.dto.AiGenerateRequest
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface AiGenerateStatus {
    data object Idle : AiGenerateStatus
    data object Generating : AiGenerateStatus
    data class Preview(val recipe: ParsedAiRecipe) : AiGenerateStatus
    data class Error(val message: String) : AiGenerateStatus
}

data class AiGenerateState(
    val prompt: String = "",
    val status: AiGenerateStatus = AiGenerateStatus.Idle,
    val isSaving: Boolean = false,
)

@HiltViewModel
class AiGenerateViewModel @Inject constructor(
    private val sseClient: SseClient,
    private val moshi: Moshi,
    private val repository: RecipeRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(AiGenerateState())
    val state: StateFlow<AiGenerateState> = _state.asStateFlow()

    private val generateAdapter by lazy { moshi.adapter(AiGenerateRequest::class.java) }

    fun setPrompt(p: String) = _state.update { it.copy(prompt = p, status = AiGenerateStatus.Idle) }

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isBlank()) return
        _state.update { it.copy(status = AiGenerateStatus.Generating) }
        viewModelScope.launch {
            try {
                val bodyJson = generateAdapter.toJson(AiGenerateRequest(prompt = prompt))
                val html = sseClient.collect("api/ai/generate", bodyJson)
                val parsed = RecipeJsonLdParser.parse(html)
                    ?: throw Exception("Rezept konnte nicht aus der KI-Antwort extrahiert werden")
                _state.update { it.copy(status = AiGenerateStatus.Preview(parsed)) }
            } catch (e: Exception) {
                _state.update { it.copy(status = AiGenerateStatus.Error(e.message ?: "Unbekannter Fehler")) }
            }
        }
    }

    fun save(recipe: ParsedAiRecipe, onSaved: (id: String) -> Unit) {
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
                mealType = recipe.mealType,
                effort = recipe.effort,
                proteinType = recipe.proteinType,
                seasonFit = recipe.seasonFit,
                sourceUrl = "",
                localImageUri = "",
                createdAt = now,
            )
            val ingredients = recipe.ingredients.mapIndexed { idx, food ->
                IngredientEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = id,
                    position = idx,
                    food = food,
                )
            }
            val instructions = recipe.instructions.mapIndexed { idx, text ->
                InstructionEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = id,
                    position = idx + 1,
                    text = text,
                )
            }
            val tags = recipe.tags.map { name ->
                TagEntity(id = UUID.randomUUID().toString(), recipeId = id, name = name)
            }
            repository.saveRecipe(entity, ingredients, instructions, tags)
            syncScheduler.triggerOneShot()
            _state.update { it.copy(isSaving = false) }
            onSaved(id)
        }
    }
}
