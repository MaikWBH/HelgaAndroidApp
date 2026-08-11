package com.helga.android.ui.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SseClient
import com.helga.android.data.remote.dto.AiRemixRequest
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.IngredientLineParser
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface AiRemixStatus {
    data object Idle : AiRemixStatus
    data object Generating : AiRemixStatus
    data class Preview(val recipe: ParsedAiRecipe) : AiRemixStatus
    data class Error(val message: String) : AiRemixStatus
}

data class AiRemixState(
    val remixPrompt: String = "",
    val status: AiRemixStatus = AiRemixStatus.Idle,
    val isSaving: Boolean = false,
)

data class AiRemixSourceState(
    val recipe: RecipeEntity? = null,
    val ingredientStrings: List<String> = emptyList(),
    val instructionStrings: List<String> = emptyList(),
)

@HiltViewModel
class AiRemixViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sseClient: SseClient,
    private val moshi: Moshi,
    private val repository: RecipeRepository,
    private val syncScheduler: SyncScheduler,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedStateHandle["recipeId"])

    val source: StateFlow<AiRemixSourceState> = combine(
        repository.observeById(recipeId),
        repository.observeIngredients(recipeId),
        repository.observeInstructions(recipeId),
    ) { recipe, ingredients, instructions ->
        AiRemixSourceState(
            recipe = recipe,
            ingredientStrings = ingredients.map { it.toDisplayString() },
            instructionStrings = instructions.sortedBy { it.position }.map { it.text },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiRemixSourceState())

    private val _state = MutableStateFlow(AiRemixState())
    val state: StateFlow<AiRemixState> = _state.asStateFlow()

    private val remixAdapter by lazy { moshi.adapter(AiRemixRequest::class.java) }

    fun setRemixPrompt(p: String) = _state.update { it.copy(remixPrompt = p, status = AiRemixStatus.Idle) }

    fun discardPreview() {
        _state.update { it.copy(status = AiRemixStatus.Idle, isSaving = false) }
    }

    fun remix() {
        val remixPrompt = _state.value.remixPrompt.trim()
        if (remixPrompt.isBlank()) return
        val src = source.value
        val recipe = src.recipe ?: return
        _state.update { it.copy(status = AiRemixStatus.Generating) }
        viewModelScope.launch {
            try {
                val req = AiRemixRequest(
                    recipeName = recipe.name,
                    recipeDescription = recipe.description,
                    recipeIngredients = src.ingredientStrings,
                    recipeInstructions = src.instructionStrings,
                    remixPrompt = remixPrompt,
                    excludeAllergens = preferences.allergies.first(),
                )
                val bodyJson = remixAdapter.toJson(req)
                val html = sseClient.collect("api/ai/remix", bodyJson)
                val parsed = RecipeJsonLdParser.parse(html)
                    ?: throw Exception("Rezept konnte nicht aus der KI-Antwort extrahiert werden")
                _state.update { it.copy(status = AiRemixStatus.Preview(parsed)) }
            } catch (e: Exception) {
                _state.update { it.copy(status = AiRemixStatus.Error(e.message ?: "Unbekannter Fehler")) }
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
                mealSlot = recipe.mealSlot,
                effort = recipe.effort,
                proteinType = recipe.proteinType,
                seasonFit = recipe.seasonFit,
                sourceUrl = "",
                localImageUri = "",
                createdAt = now,
            )
            val ingredients = recipe.ingredients.mapIndexed { idx, line ->
                val parsed = IngredientLineParser.parse(line)
                IngredientEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = id,
                    position = idx,
                    quantity = parsed.quantity,
                    unit = parsed.unit,
                    food = parsed.food.ifBlank { line },
                    note = parsed.note,
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

    private fun IngredientEntity.toDisplayString(): String = buildString {
        if (quantity > 0.0) {
            val q = if (quantity % 1.0 == 0.0) "${quantity.toInt()}" else "$quantity"
            append(q)
            if (unit.isNotBlank()) append(" $unit")
            append(" ")
        }
        append(food)
        if (note.isNotBlank()) append(" ($note)")
    }
}
