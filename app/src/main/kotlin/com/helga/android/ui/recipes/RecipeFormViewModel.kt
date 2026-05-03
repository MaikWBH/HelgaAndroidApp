package com.helga.android.ui.recipes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.helga.android.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class IngredientFormItem(
    val id: String = UUID.randomUUID().toString(),
    val quantity: String = "",
    val unit: String = "",
    val food: String = "",
    val note: String = "",
)

data class InstructionFormItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
)

data class RecipeFormState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val recipeYield: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val cuisine: String = "",
    val mealType: String = "",
    val effort: String = "",
    val proteinType: String = "",
    val seasonFit: String = "",
    val sourceUrl: String = "",
    val localImageUri: String = "",
    val ingredients: List<IngredientFormItem> = emptyList(),
    val instructions: List<InstructionFormItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
class RecipeFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val recipeId: String? = savedStateHandle["recipeId"]
    val isEditing: Boolean get() = recipeId != null

    private val _state = MutableStateFlow(RecipeFormState())
    val state: StateFlow<RecipeFormState> = _state.asStateFlow()

    init {
        if (recipeId != null) loadRecipe(recipeId)
    }

    private fun loadRecipe(id: String) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val recipe = repository.findById(id) ?: return@launch
            val ingredients = repository.observeIngredients(id).first()
            val instructions = repository.observeInstructions(id).first()
            val tags = repository.observeTags(id).first()
            _state.update {
                it.copy(
                    id = recipe.id,
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
                    sourceUrl = recipe.sourceUrl,
                    localImageUri = recipe.localImageUri,
                    ingredients = ingredients.map { ing ->
                        IngredientFormItem(
                            id = ing.id,
                            quantity = if (ing.quantity > 0.0) {
                                if (ing.quantity % 1.0 == 0.0) "${ing.quantity.toInt()}" else "${ing.quantity}"
                            } else "",
                            unit = ing.unit,
                            food = ing.food,
                            note = ing.note,
                        )
                    },
                    instructions = instructions.map { ins ->
                        InstructionFormItem(id = ins.id, text = ins.text)
                    },
                    tags = tags.map { t -> t.name },
                    isLoading = false,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setRecipeYield(v: String) = _state.update { it.copy(recipeYield = v) }
    fun setPrepTime(v: String) = _state.update { it.copy(prepTime = v) }
    fun setCookTime(v: String) = _state.update { it.copy(cookTime = v) }
    fun setTotalTime(v: String) = _state.update { it.copy(totalTime = v) }
    fun setCuisine(v: String) = _state.update { it.copy(cuisine = v) }
    fun setMealType(v: String) = _state.update { it.copy(mealType = v) }
    fun setEffort(v: String) = _state.update { it.copy(effort = v) }
    fun setProteinType(v: String) = _state.update { it.copy(proteinType = v) }
    fun setSeasonFit(v: String) = _state.update { it.copy(seasonFit = v) }
    fun setSourceUrl(v: String) = _state.update { it.copy(sourceUrl = v) }

    // ── Ingredients ──────────────────────────────────────────────────────────

    fun addIngredient() = _state.update {
        it.copy(ingredients = it.ingredients + IngredientFormItem())
    }

    fun updateIngredient(index: Int, item: IngredientFormItem) = _state.update {
        it.copy(ingredients = it.ingredients.toMutableList().also { list -> list[index] = item })
    }

    fun removeIngredient(index: Int) = _state.update {
        it.copy(ingredients = it.ingredients.toMutableList().also { list -> list.removeAt(index) })
    }

    // ── Instructions ─────────────────────────────────────────────────────────

    fun addInstruction() = _state.update {
        it.copy(instructions = it.instructions + InstructionFormItem())
    }

    fun updateInstruction(index: Int, item: InstructionFormItem) = _state.update {
        it.copy(instructions = it.instructions.toMutableList().also { list -> list[index] = item })
    }

    fun removeInstruction(index: Int) = _state.update {
        it.copy(instructions = it.instructions.toMutableList().also { list -> list.removeAt(index) })
    }

    // ── Tags ─────────────────────────────────────────────────────────────────

    fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed in _state.value.tags) return
        _state.update { it.copy(tags = it.tags + trimmed) }
    }

    fun removeTag(name: String) = _state.update {
        it.copy(tags = it.tags.filter { t -> t != name })
    }

    // ── Bild ─────────────────────────────────────────────────────────────────

    fun setImage(uri: Uri) {
        viewModelScope.launch {
            val dest = withContext(Dispatchers.IO) { copyImageToAppStorage(uri) }
            _state.update { it.copy(localImageUri = dest) }
        }
    }

    private fun copyImageToAppStorage(uri: Uri): String {
        val dir = File(context.filesDir, "images").also { it.mkdirs() }
        val dest = File(dir, "${_state.value.id}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest.absolutePath
    }

    // ── Speichern ────────────────────────────────────────────────────────────

    fun save(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val recipeId = s.id
            val recipe = RecipeEntity(
                id = recipeId,
                slug = recipeId,
                name = s.name.trim(),
                description = s.description.trim(),
                recipeYield = s.recipeYield.trim(),
                prepTime = s.prepTime.trim(),
                cookTime = s.cookTime.trim(),
                totalTime = s.totalTime.trim(),
                cuisine = s.cuisine.trim(),
                mealType = s.mealType.trim(),
                effort = s.effort.trim(),
                proteinType = s.proteinType.trim(),
                seasonFit = s.seasonFit.trim(),
                sourceUrl = s.sourceUrl.trim(),
                localImageUri = s.localImageUri,
                createdAt = System.currentTimeMillis(),
            )
            val ingredients = s.ingredients.mapIndexed { idx, item ->
                IngredientEntity(
                    id = item.id,
                    recipeId = recipeId,
                    position = idx,
                    quantity = item.quantity.toDoubleOrNull() ?: 0.0,
                    unit = item.unit.trim(),
                    food = item.food.trim(),
                    note = item.note.trim(),
                )
            }
            val instructions = s.instructions.mapIndexed { idx, item ->
                InstructionEntity(
                    id = item.id,
                    recipeId = recipeId,
                    position = idx + 1,
                    text = item.text.trim(),
                )
            }
            val tags = s.tags.map { name ->
                TagEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = recipeId,
                    name = name,
                )
            }
            repository.saveRecipe(recipe, ingredients, instructions, tags)
            _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
            onSuccess()
        }
    }
}
