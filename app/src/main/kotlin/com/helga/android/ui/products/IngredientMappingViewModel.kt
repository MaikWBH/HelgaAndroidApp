package com.helga.android.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.IngredientMappingDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.entity.IngredientProductMappingEntity
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.IngredientNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Eine Zutat aus den Rezepten mit ihrem aktuellen Mapping-Status. */
data class IngredientMappingRow(
    val displayFood: String, // erster gefundener Originalname (z.B. "Butter")
    val normalized: String,
    val mapping: IngredientProductMappingEntity?,
)

@HiltViewModel
class IngredientMappingViewModel @Inject constructor(
    recipeDao: RecipeDao,
    private val mappingDao: IngredientMappingDao,
    private val offProductDao: OffProductDao,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val rows: StateFlow<List<IngredientMappingRow>> = combine(
        recipeDao.observeDistinctIngredientNames(),
        mappingDao.observeAll(),
    ) { foods, mappings ->
        val byName = mappings.associateBy { it.ingredientName }
        foods
            .map { food -> food to IngredientNormalizer.normalize(food) }
            .filter { it.second.isNotBlank() }
            .groupBy { it.second }
            .map { (norm, pairs) ->
                IngredientMappingRow(
                    displayFood = pairs.first().first,
                    normalized = norm,
                    mapping = byName[norm],
                )
            }
            .sortedWith(compareBy({ it.mapping != null }, { it.displayFood.lowercase() }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _suggestions = MutableStateFlow<List<OffProductEntity>>(emptyList())
    val suggestions: StateFlow<List<OffProductEntity>> = _suggestions.asStateFlow()

    /** Lädt Vorschläge aus "Meine Produkte" für den Picker zu einer Zutat. */
    fun loadSuggestions(food: String) {
        viewModelScope.launch {
            val terms = food.split(Regex("[\\s\\-]+")).filter { it.length > 2 }
            val results = LinkedHashMap<String, OffProductEntity>()
            // Zuerst nach dem ganzen Namen, dann nach einzelnen Wörtern
            (listOf(food) + terms).forEach { term ->
                offProductDao.searchFavorites(term.trim(), limit = 10).forEach { results[it.id] = it }
            }
            _suggestions.value = results.values.toList()
        }
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun assignProduct(normalizedFood: String, product: OffProductEntity) {
        viewModelScope.launch {
            val existing = mappingDao.getByIngredientName(normalizedFood)
            mappingDao.upsert(
                IngredientProductMappingEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    ingredientName = normalizedFood,
                    offProductId = product.id,
                    offBarcode = product.barcode,
                    displayName = product.name.ifBlank { product.barcode },
                    updatedAt = System.currentTimeMillis(),
                    deleted = 0,
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun removeMapping(mapping: IngredientProductMappingEntity) {
        viewModelScope.launch {
            mappingDao.upsert(
                mapping.copy(deleted = 1, updatedAt = System.currentTimeMillis(), dirty = 1)
            )
            syncScheduler.triggerOneShot()
        }
    }
}
