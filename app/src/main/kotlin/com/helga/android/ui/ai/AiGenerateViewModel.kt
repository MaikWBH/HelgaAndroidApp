package com.helga.android.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SseClient
import com.helga.android.data.remote.dto.AiGenerateRequest
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.ServerReachabilityMonitor
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.IngredientLineParser
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val dietType: String = "Egal",
    val cookTime: String = "Egal",
    val effort: String = "Egal",
    val cuisine: String = "Egal",
    val special: String = "",
    val feedbackVisible: Boolean = false,
    val feedback: String = "",
    val status: AiGenerateStatus = AiGenerateStatus.Idle,
    val isSaving: Boolean = false,
)

@HiltViewModel
class AiGenerateViewModel @Inject constructor(
    private val sseClient: SseClient,
    private val moshi: Moshi,
    private val repository: RecipeRepository,
    private val syncScheduler: SyncScheduler,
    private val preferences: AppPreferences,
    private val serverReachability: ServerReachabilityMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(AiGenerateState())
    val state: StateFlow<AiGenerateState> = _state.asStateFlow()

    /** ki A3 — Hinweis vor dem ersten Versuch statt Fehler erst nach einem gescheiterten Generieren. */
    val serverReachable: StateFlow<Boolean?> = serverReachability.reachable

    private val generateAdapter by lazy { moshi.adapter(AiGenerateRequest::class.java) }

    init {
        serverReachability.checkAsync()
    }

    fun setPrompt(p: String) = _state.update { it.copy(prompt = p, status = AiGenerateStatus.Idle) }
    fun setDietType(v: String) = _state.update { it.copy(dietType = v) }
    fun setCookTime(v: String) = _state.update { it.copy(cookTime = v) }
    fun setEffort(v: String) = _state.update { it.copy(effort = v) }
    fun setCuisine(v: String) = _state.update { it.copy(cuisine = v) }
    fun setSpecial(v: String) = _state.update { it.copy(special = v) }
    fun showFeedback() = _state.update { it.copy(feedbackVisible = true, feedback = "") }
    fun hideFeedback() = _state.update { it.copy(feedbackVisible = false) }
    fun setFeedback(v: String) = _state.update { it.copy(feedback = v) }

    fun discardPreview() {
        _state.update { it.copy(status = AiGenerateStatus.Idle, feedbackVisible = false, isSaving = false) }
    }

    fun generate() {
        val s = _state.value
        val prompt = s.prompt.trim()
        if (prompt.isBlank()) return
        val customInstructions = buildCustomInstructions(s)
        _state.update { it.copy(status = AiGenerateStatus.Generating, feedbackVisible = false) }
        viewModelScope.launch {
            try {
                val allergies = preferences.allergies.first()
                val bodyJson = generateAdapter.toJson(
                    AiGenerateRequest(
                        prompt = prompt,
                        customInstructions = customInstructions,
                        excludeAllergens = allergies,
                    )
                )
                val html = sseClient.collect("api/ai/generate", bodyJson)
                val parsed = RecipeJsonLdParser.parse(html)
                    ?: throw Exception("Rezept konnte nicht aus der KI-Antwort extrahiert werden")
                _state.update { it.copy(status = AiGenerateStatus.Preview(parsed), feedback = "") }
            } catch (e: Exception) {
                _state.update { it.copy(status = AiGenerateStatus.Error(e.message ?: "Unbekannter Fehler")) }
            }
        }
    }

    fun regenerate() {
        _state.update { it.copy(feedbackVisible = false) }
        generate()
    }

    private fun buildCustomInstructions(s: AiGenerateState): String = buildString {
        if (s.dietType != "Egal") appendLine("Ernährungsweise: ${s.dietType}")
        if (s.cookTime != "Egal") appendLine("Kochzeit: ${s.cookTime}")
        if (s.effort != "Egal") appendLine("Schwierigkeitsgrad: ${s.effort}")
        if (s.cuisine != "Egal") appendLine("Küche/Stil: ${s.cuisine}")
        if (s.special.isNotBlank()) appendLine("Besonderes: ${s.special}")
        if (s.feedback.isNotBlank()) appendLine("Feedback zum vorherigen Rezept: ${s.feedback}")
    }.trim()

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
            val ingredients = recipe.ingredients.filterNot(IngredientLineParser::isHeaderLine).mapIndexed { idx, line ->
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
}
