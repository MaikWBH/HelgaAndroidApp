package com.helga.android.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.model.MealSlots
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.repository.AiRepository
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.ImportedRecipeBundle
import com.helga.android.data.util.MarkdownBlockError
import com.helga.android.data.util.MarkdownFatal
import com.helga.android.data.util.ParsedRecipe
import com.helga.android.data.util.RecipeMarkdownMapper
import com.helga.android.data.util.RecipeMarkdownParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MarkdownPreviewItem(val recipe: ParsedRecipe, val selected: Boolean, val duplicate: Boolean)

data class MarkdownImportState(
    val input: String = "",
    /** `null` = noch nicht ausgewertet. */
    val preview: List<MarkdownPreviewItem>? = null,
    val errors: List<MarkdownBlockError> = emptyList(),
    val fatal: MarkdownFatal? = null,
    val isSaving: Boolean = false,
)

sealed interface MarkdownSaveTarget {
    data class Detail(val recipeId: String) : MarkdownSaveTarget
    data object RecipeList : MarkdownSaveTarget
}

/** Markdown-Rezepte aus der Zwischenablage auswerten, in der Vorschau auswählen und speichern (Phase 7). */
@HiltViewModel
class MarkdownImportViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val aiRepository: AiRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(MarkdownImportState())
    val state: StateFlow<MarkdownImportState> = _state.asStateFlow()

    fun setInput(text: String) = _state.update {
        it.copy(input = text, preview = null, errors = emptyList(), fatal = null)
    }

    fun evaluate() {
        val text = _state.value.input
        viewModelScope.launch {
            val result = RecipeMarkdownParser.parse(text)
            val seen = repository.allRecipes().map { it.name.trim().lowercase() }.toMutableSet()
            val items = result.recipes.map { recipe ->
                val duplicate = !seen.add(recipe.name.trim().lowercase())
                MarkdownPreviewItem(recipe, selected = !duplicate, duplicate = duplicate)
            }
            _state.update {
                it.copy(
                    preview = if (result.fatal == null) items else null,
                    errors = result.errors,
                    fatal = result.fatal,
                )
            }
        }
    }

    fun toggle(index: Int) = _state.update { current ->
        val preview = current.preview ?: return@update current
        current.copy(
            preview = preview.mapIndexed { i, item -> if (i == index) item.copy(selected = !item.selected) else item },
        )
    }

    fun save(onDone: (MarkdownSaveTarget) -> Unit) {
        val current = _state.value
        val selected = current.preview.orEmpty().filter { it.selected }.map { it.recipe }
        if (selected.isEmpty() || current.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val bundles = selected.map { RecipeMarkdownMapper.toBundle(it, System.currentTimeMillis()) }
            bundles.forEach { b ->
                repository.saveRecipe(b.recipe, b.ingredients, b.instructions, b.tags)
            }
            syncScheduler.triggerOneShot()
            if (classifyMissing(bundles)) syncScheduler.triggerOneShot()
            _state.update { it.copy(isSaving = false) }
            onDone(
                if (bundles.size == 1) MarkdownSaveTarget.Detail(bundles.first().recipe.id) else MarkdownSaveTarget.RecipeList,
            )
        }
    }

    /** Klassifiziert Rezepte mit leeren Feldern (höchstens 5, nacheinander); `true`, wenn etwas übernommen wurde. */
    private suspend fun classifyMissing(bundles: List<ImportedRecipeBundle>): Boolean {
        var changed = false
        for (bundle in bundles.filter { needsClassification(it.recipe) }.take(MAX_CLASSIFY)) {
            try {
                val recipe = bundle.recipe
                val result = withTimeoutOrNull(CLASSIFY_TIMEOUT_MS) {
                    aiRepository.classifyRecipe(
                        AiClassifyRequest(
                            name = recipe.name,
                            description = recipe.description,
                            tags = bundle.tags.map { it.name },
                            ingredients = bundle.ingredients.map { it.food },
                        ),
                    )
                } ?: continue
                repository.upsertLocal(
                    recipe.copy(
                        mealSlot = if (recipe.mealSlot == MealSlots.OTHER) result.mealSlot.ifBlank { recipe.mealSlot } else recipe.mealSlot,
                        proteinType = recipe.proteinType.ifBlank { result.proteinType },
                        effort = recipe.effort.ifBlank { result.effort },
                        cuisine = recipe.cuisine.ifBlank { result.cuisine },
                        seasonFit = recipe.seasonFit.ifBlank { result.seasonFit },
                    ),
                )
                changed = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Klassifizierung ist optional – das Rezept ist bereits gespeichert.
            }
        }
        return changed
    }

    private fun needsClassification(recipe: RecipeEntity): Boolean =
        recipe.cuisine.isBlank() || recipe.effort.isBlank() || recipe.proteinType.isBlank() ||
            recipe.seasonFit.isBlank() || recipe.mealSlot == MealSlots.OTHER

    private companion object {
        const val MAX_CLASSIFY = 5
        const val CLASSIFY_TIMEOUT_MS = 15_000L
    }
}
