package com.helga.android.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.WeekplanRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    private val weekplanRepository: WeekplanRepository,
    private val weekplanDao: WeekplanDao,
    private val recipeDao: RecipeDao,
    private val apiFactory: SyncApiFactory,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val recipeId: String = checkNotNull(savedStateHandle["recipeId"])

    private val _classifyState = MutableStateFlow(false to (null as String?))
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _baseServings = MutableStateFlow(0)
    private val _servings = MutableStateFlow(0)
    val servings: StateFlow<Int> = _servings.asStateFlow()
    val baseServings: StateFlow<Int> = _baseServings.asStateFlow()
    val scaleFactor: StateFlow<Float> = combine(_servings, _baseServings) { s, base ->
        if (base > 0 && s > 0) s.toFloat() / base.toFloat() else 1f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

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

    init {
        viewModelScope.launch {
            uiState.filter { it.recipe != null }.first().recipe?.let { recipe ->
                val base = parseServings(recipe.recipeYield)
                if (base > 0) {
                    _baseServings.value = base
                    _servings.value = base
                }
            }
        }
    }

    private fun parseServings(yieldStr: String): Int =
        Regex("""\d+""").find(yieldStr)?.value?.toIntOrNull() ?: 0

    fun setServings(n: Int) { _servings.value = n.coerceIn(1, 99) }

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

    fun addToDefaultShoppingList(listName: String) {
        viewModelScope.launch {
            val listId = resolveDefaultListId() ?: return@launch
            repository.exportToShoppingList(recipeId, listId)
            syncScheduler.triggerOneShot()
            _snackbarMessage.tryEmit("Zur „$listName” hinzugefügt")
        }
    }

    fun resolveDefaultListId(): String? {
        val preferred = shoppingLists.value.firstOrNull { it.isDefaultRecipe == 1 }
        return preferred?.id ?: shoppingLists.value.firstOrNull()?.id
    }

    // ── Wochenplan-Integration ────────────────────────────────────────────

    data class WeekplanDayWithRecipes(
        val day: WeekplanDayEntity,
        val recipeNames: List<String>,
    )

    private val _weekplanDays = MutableStateFlow<List<WeekplanDayWithRecipes>>(emptyList())
    val weekplanDays: StateFlow<List<WeekplanDayWithRecipes>> = _weekplanDays.asStateFlow()

    fun loadWeekplanDays() {
        viewModelScope.launch {
            val monday = LocalDate.now().with(DayOfWeek.MONDAY)
            val sunday = monday.plusDays(6)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val days = weekplanRepository.observeDaysBetween(
                monday.format(fmt), sunday.format(fmt)
            ).first()
            val result = days.map { day ->
                val recipes = weekplanDao.recipesForDay(day.id)
                val names = recipes.map { wr ->
                    recipeDao.findById(wr.recipeId)?.name ?: wr.recipeId
                }
                WeekplanDayWithRecipes(day, names)
            }
            _weekplanDays.value = result
        }
    }

    fun addToWeekplanDay(dayId: String) {
        viewModelScope.launch {
            weekplanRepository.addRecipe(dayId, recipeId)
            syncScheduler.triggerOneShot()
            val recipe = uiState.value.recipe
            _snackbarMessage.tryEmit("Zum Wochenplan hinzugefügt")
        }
    }
}
