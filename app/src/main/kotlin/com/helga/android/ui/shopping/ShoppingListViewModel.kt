package com.helga.android.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.model.ListCostEstimate
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.sync.SyncEngine
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.helga.android.data.local.entity.OffProductEntity
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.json.JSONArray

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val storeRepository: StoreRepository,
    private val quickEmojiDao: QuickEmojiDao,
    private val weekplanDao: WeekplanDao,
    private val recipeDao: RecipeDao,
    private val offProductDao: OffProductDao,
    private val apiFactory: SyncApiFactory,
    private val syncScheduler: SyncScheduler,
    private val syncEngine: SyncEngine,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _activeListId = MutableStateFlow<String?>(null)

    private val _scannedProduct = MutableStateFlow<OffProductEntity?>(null)
    val scannedProduct: StateFlow<OffProductEntity?> = _scannedProduct.asStateFlow()

    private val _alternativeProducts = MutableStateFlow<List<OffProductEntity>>(emptyList())
    val alternativeProducts: StateFlow<List<OffProductEntity>> = _alternativeProducts.asStateFlow()

    val lists: StateFlow<List<ShoppingListEntity>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeListId: StateFlow<String?> = combine(
        lists,
        _activeListId,
        preferences.defaultShoppingListId,
    ) { lists, selected, defaultId ->
        selected ?: lists.firstOrNull { it.id == defaultId }?.id ?: lists.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val itemsByAisle: StateFlow<Map<String, List<ShoppingItemEntity>>> = activeListId
        .flatMapLatest { listId ->
            if (listId == null) flowOf(emptyMap())
            else repository.observeItemsByList(listId).map { items ->
                items.groupBy { it.aisle }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val activeStore: StateFlow<StoreEntity?> = storeRepository.observeActiveStore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allStores: StateFlow<List<StoreEntity>> = storeRepository.observeStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val storeAisles: StateFlow<List<StoreAisleEntity>> = activeStore
        .flatMapLatest { store ->
            if (store == null) flowOf(emptyList())
            else storeRepository.observeAisles(store.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val aisleSortMap: StateFlow<Map<String, Int>> = storeAisles
        .map { aisles -> aisles.associate { it.aisleName to it.sortOrder } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val quickEmojis: StateFlow<List<QuickEmojiEntity>> = quickEmojiDao.observeEmojis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val staples: StateFlow<List<ShoppingListStapleEntity>> = activeListId
        .flatMapLatest { listId ->
            if (listId == null) flowOf(emptyList())
            else storeRepository.observeStaples(listId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checkMode: StateFlow<String> = preferences.checkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "keep")

    // Banner: Wochenplan bereit zum Export?
    private val monday = LocalDate.now().with(DayOfWeek.MONDAY)
    private val sunday = monday.plusDays(6)
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    val weekplanHasRecipes: StateFlow<Boolean> = weekplanDao.observeDaysBetween(
        monday.format(fmt), sunday.format(fmt),
    ).flatMapLatest { days ->
        if (days.isEmpty()) flowOf(false)
        else {
            val countFlows = days.map { day -> weekplanDao.observeRecipeCount(day.id) }
            combine(countFlows) { counts -> counts.any { it > 0 } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentListEmpty: StateFlow<Boolean> = itemsByAisle
        .map { it.values.flatten().isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _costEstimate = MutableStateFlow<ListCostEstimate?>(null)
    val costEstimate: StateFlow<ListCostEstimate?> = _costEstimate

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        viewModelScope.launch {
            activeListId.collect { listId ->
                if (listId != null) {
                    _costEstimate.value = repository.estimateListCosts(listId)
                }
            }
        }
    }

    fun selectList(id: String) {
        _activeListId.value = id
    }

    /**
     * Manueller Pull-to-Refresh: zieht sofort die aktuellen Server-Daten und merged
     * sie in Room. So sieht Handy B direkt einen Eintrag, den Handy A erstellt hat.
     * Netzwerkfehler sind nicht-fatal – die lokale Liste bleibt erhalten.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                withContext(Dispatchers.IO) {
                    syncEngine.runFullSync()
                }
                activeListId.value?.let { listId ->
                    _costEstimate.value = repository.estimateListCosts(listId)
                }
            } catch (e: Exception) {
                // Offline oder Server nicht erreichbar – lokale Liste unverändert lassen.
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun exportWeekToShoppingList(listId: String) {
        viewModelScope.launch {
            val days = weekplanDao.getDaysBetween(monday.format(fmt), sunday.format(fmt))
            days.forEach { day ->
                weekplanDao.recipesForDay(day.id).forEach { entry ->
                    val recipeName = recipeDao.findById(entry.recipeId)?.name ?: ""
                    val ingredients = recipeDao.ingredientsByRecipeId(entry.recipeId)
                    ingredients.filter { it.deleted == 0 }.forEach { ingredient ->
                        val storeId = activeStore.value?.id
                        val aisle = if (storeId != null)
                            storeRepository.findAisleForProduct(ingredient.food, storeId) ?: ""
                        else ""
                        repository.addOrMergeItem(
                            listId = listId,
                            name = ingredient.food,
                            quantity = ingredient.quantity,
                            unit = ingredient.unit,
                            source = "weekplan",
                            aisle = aisle,
                            recipeName = recipeName,
                        )
                    }
                }
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun selectStore(storeId: String?) {
        viewModelScope.launch {
            if (storeId == null) {
                storeRepository.deactivateAll()
            } else {
                storeRepository.setActiveStore(storeId)
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val id = repository.createList(name)
            _activeListId.value = id
            syncScheduler.triggerOneShot()
        }
    }

    fun addItem(name: String) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val storeId = activeStore.value?.id
            val aisle = if (storeId != null)
                storeRepository.findAisleForProduct(trimmed, storeId) ?: ""
            else ""
            repository.addItem(listId = listId, name = trimmed, aisle = aisle)
            syncScheduler.triggerOneShot()
        }
    }

    fun addEmojiItem(emoji: QuickEmojiEntity) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            val storeId = activeStore.value?.id
            val aisle = if (storeId != null)
                storeRepository.findAisleForProduct(emoji.food, storeId) ?: ""
            else ""
            repository.addItem(
                listId = listId,
                name = emoji.food,
                quantity = emoji.quantity,
                unit = emoji.unit,
                aisle = aisle,
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun assignAisle(item: ShoppingItemEntity, aisleName: String) {
        viewModelScope.launch {
            repository.updateItemAisle(item, aisleName)
            val storeId = activeStore.value?.id
            if (storeId != null) {
                storeRepository.saveAisleProduct(item.name, aisleName, storeId)
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun addStaplesToList() {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            val stapleList = storeRepository.staplesForList(listId)
            if (stapleList.isEmpty()) return@launch
            val storeId = activeStore.value?.id
            stapleList.forEach { staple ->
                val aisle = if (storeId != null)
                    storeRepository.findAisleForProduct(staple.name, storeId) ?: ""
                else ""
                repository.addItem(
                    listId = listId,
                    name = staple.name,
                    quantity = staple.quantity,
                    aisle = aisle,
                    source = "staple",
                )
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun addStaple(name: String) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            storeRepository.addStaple(listId, name)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteStaple(staple: ShoppingListStapleEntity) {
        viewModelScope.launch {
            storeRepository.deleteStaple(staple)
            syncScheduler.triggerOneShot()
        }
    }

    fun toggleChecked(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.toggleChecked(item)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteItem(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.softDeleteItem(item)
            syncScheduler.triggerOneShot()
        }
    }

    fun updateItem(id: String, quantity: Double, unit: String, name: String) {
        viewModelScope.launch {
            repository.updateItem(id = id, quantity = quantity, unit = unit, name = name)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteCheckedItems() {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            repository.deleteCheckedItems(listId)
            syncScheduler.triggerOneShot()
        }
    }

    suspend fun suggestItems(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return try {
            apiFactory.api().suggestItems(query).suggestions
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addItemFromBarcode(barcode: String) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            try {
                val dto = apiFactory.api().lookupBarcode(
                    com.helga.android.data.remote.dto.OffLookupBarcodeRequest(barcode)
                )
                // Zeige Produkt-Detail-Dialog statt direkt hinzuzufügen
                _scannedProduct.value = OffProductEntity(
                    id = dto.id.ifBlank { barcode },
                    barcode = dto.barcode.ifBlank { barcode },
                    name = dto.name,
                    brand = dto.brand,
                    categories = dto.categories,
                    kcalPerUnit = dto.kcalPerUnit,
                    proteins = dto.proteins,
                    fats = dto.fats,
                    carbs = dto.carbs,
                    nutriScore = dto.nutriScore,
                    nova = dto.nova,
                    ecoScore = dto.ecoScore,
                    allergenes = dto.allergenes,
                    additives = dto.additives,
                    isOrganic = dto.isOrganic,
                    vegan = dto.vegan,
                    vegetarian = dto.vegetarian,
                    imagePath = dto.imagePath,
                    updatedAt = dto.updatedAt,
                    deleted = dto.deleted,
                )
            } catch (e: Exception) {
                Timber.w(e, "Barcode lookup failed: $barcode")
                // Bei Fehler: Generisches Produkt-Dummy anzeigen
                _scannedProduct.value = OffProductEntity(
                    id = barcode,
                    barcode = barcode,
                    name = "Barcode: $barcode (nicht gefunden)",
                )
            }
        }
    }

    fun confirmScannedProduct(product: OffProductEntity? = null, quantity: Double = 1.0, unit: String = "Stück") {
        val listId = activeListId.value ?: return
        val itemProduct = product ?: scannedProduct.value ?: return
        viewModelScope.launch {
            try {
                val storeId = activeStore.value?.id
                val aisle = if (storeId != null)
                    storeRepository.findAisleForProduct(itemProduct.name, storeId) ?: ""
                else ""
                repository.addItem(
                    listId = listId,
                    name = itemProduct.name,
                    quantity = quantity,
                    unit = unit,
                    aisle = aisle,
                    offBarcode = itemProduct.barcode,
                    offProductId = itemProduct.id,
                )
                syncScheduler.triggerOneShot()
                _scannedProduct.value = null
            } catch (e: Exception) {
                Timber.e(e, "Failed to add item from barcode")
            }
        }
    }

    fun clearScannedProduct() {
        _scannedProduct.value = null
    }

    fun searchAlternatives(product: OffProductEntity) {
        viewModelScope.launch {
            try {
                val searchTerms = product.name
                    .split(Regex("[\\s\\-]+"))
                    .take(2)
                    .filter { it.isNotBlank() && it.length > 2 }

                if (searchTerms.isEmpty()) {
                    _alternativeProducts.value = emptyList()
                    return@launch
                }

                val allCandidates = mutableListOf<OffProductEntity>()
                for (term in searchTerms) {
                    allCandidates.addAll(offProductDao.search(term, limit = 20))
                }

                val currentAllergens = parseAllergens(product.allergenes)
                val currentNutriScoreRank = nutriScoreRank(product.nutriScore)

                val alternatives = allCandidates
                    .filter { it.id != product.id }
                    .filter { candidate ->
                        val candidateNutriRank = nutriScoreRank(candidate.nutriScore)
                        val candidateAllergens = parseAllergens(candidate.allergenes)

                        val hasLowerKcal = candidate.kcalPerUnit < product.kcalPerUnit * 0.95
                        val hasBetterNutriScore = candidateNutriRank < currentNutriScoreRank
                        val noNewAllergens = candidateAllergens.none { allergen ->
                            allergen !in currentAllergens
                        }

                        (hasLowerKcal || hasBetterNutriScore) && noNewAllergens
                    }
                    .distinctBy { it.id }
                    .sortedWith(compareBy(
                        { -nutriScoreRank(it.nutriScore) },
                        { it.kcalPerUnit }
                    ))
                    .take(3)

                _alternativeProducts.value = alternatives
                Timber.i("Found ${alternatives.size} alternatives for: ${product.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error searching alternatives")
                _alternativeProducts.value = emptyList()
            }
        }
    }

    private fun nutriScoreRank(score: String): Int = when (score.uppercase()) {
        "A" -> 4
        "B" -> 3
        "C" -> 2
        "D" -> 1
        "E" -> 0
        else -> -1
    }

    private fun parseAllergens(jsonString: String): Set<String> = try {
        if (jsonString.isBlank() || jsonString == "[]") {
            emptySet()
        } else {
            val array = JSONArray(jsonString)
            (0 until array.length()).map { array.getString(it) }.toSet()
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to parse allergens")
        emptySet()
    }
}
