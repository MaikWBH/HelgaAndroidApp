package com.helga.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.AppDatabase
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.model.NUTRITION_BASELINE_PORTIONS
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.AiClassifyRequest
import com.helga.android.data.remote.dto.AiNutritionRequest
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.sync.SyncStatus
import com.helga.android.data.sync.SyncStatusHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val apiFactory: SyncApiFactory,
    private val syncScheduler: SyncScheduler,
    private val shoppingRepository: ShoppingRepository,
    private val recipeRepository: RecipeRepository,
    private val quickEmojiDao: QuickEmojiDao,
    private val weekplanSettingsDao: WeekplanSettingsDao,
    private val syncStatusHolder: SyncStatusHolder,
    private val database: AppDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val lastSyncTs: StateFlow<Long> = preferences.lastSyncTs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val syncError: StateFlow<String?> = syncStatusHolder.status
        .map { status -> if (status is SyncStatus.Error) status.message else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shoppingLists: StateFlow<List<ShoppingListEntity>> = shoppingRepository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickEmojis: StateFlow<List<QuickEmojiEntity>> = quickEmojiDao.observeEmojis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val conn = preferences.connection.first()
            val weekplanDays = preferences.weekplanDays.first()
            val shoppingDay = preferences.shoppingDay.first()
            val defaultListId = preferences.defaultShoppingListId.first()
            val themeMode = preferences.themeMode.first()
            val accentColor = preferences.accentColor.first()
            val checkMode = preferences.checkMode.first()
            val scanReminderThreshold = preferences.scanReminderThreshold.first()
            val reconcileEnabled = preferences.receiptReconciliationEnabled.first()
            val receiptRetentionMonths = preferences.receiptRetentionMonths.first()
            val notifyShopping = preferences.notifyShoppingDay.first()
            val notifyCook = preferences.notifyCookReminder.first()
            _state.update {
                it.copy(
                    serverUrl = conn.serverUrl,
                    apiKey = conn.apiKey,
                    weekplanDays = weekplanDays,
                    shoppingDay = shoppingDay,
                    defaultShoppingListId = defaultListId,
                    themeMode = themeMode,
                    accentColor = accentColor,
                    checkMode = checkMode,
                    scanReminderThreshold = scanReminderThreshold,
                    receiptReconciliationEnabled = reconcileEnabled,
                    receiptRetentionMonths = receiptRetentionMonths,
                    notifyShoppingDay = notifyShopping,
                    notifyCookReminder = notifyCook,
                    loaded = true,
                )
            }
        }
    }

    fun setServerUrl(url: String) = _state.update {
        it.copy(serverUrl = url, validation = SettingsValidation.Idle)
    }

    fun setApiKey(key: String) = _state.update {
        it.copy(apiKey = key, validation = SettingsValidation.Idle)
    }

    fun setWeekplanDays(days: Int) = _state.update { it.copy(weekplanDays = days) }

    fun setShoppingDay(day: Int) = _state.update { it.copy(shoppingDay = day.coerceIn(0, 6)) }

    fun setDefaultShoppingListId(listId: String) = _state.update { it.copy(defaultShoppingListId = listId) }

    fun setThemeMode(mode: String) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { preferences.saveThemeMode(mode) }
    }

    fun setAccentColor(index: Int) {
        _state.update { it.copy(accentColor = index) }
        viewModelScope.launch { preferences.saveAccentColor(index) }
    }

    fun setCheckMode(mode: String) {
        _state.update { it.copy(checkMode = mode) }
        viewModelScope.launch { preferences.saveCheckMode(mode) }
    }

    fun setScanReminderThreshold(threshold: Float) {
        _state.update { it.copy(scanReminderThreshold = threshold) }
        viewModelScope.launch { preferences.saveScanReminderThreshold(threshold) }
    }

    fun setReceiptReconciliationEnabled(enabled: Boolean) {
        _state.update { it.copy(receiptReconciliationEnabled = enabled) }
        viewModelScope.launch { preferences.saveReceiptReconciliationEnabled(enabled) }
    }

    fun setReceiptRetentionMonths(months: Int) {
        _state.update { it.copy(receiptRetentionMonths = months) }
        viewModelScope.launch { preferences.saveReceiptRetentionMonths(months) }
    }

    fun setNotifyShoppingDay(enabled: Boolean) {
        _state.update { it.copy(notifyShoppingDay = enabled) }
        viewModelScope.launch { preferences.saveNotifyShoppingDay(enabled) }
    }

    fun setNotifyCookReminder(enabled: Boolean) {
        _state.update { it.copy(notifyCookReminder = enabled) }
        viewModelScope.launch { preferences.saveNotifyCookReminder(enabled) }
    }

    fun testAndSave() {
        val url = _state.value.serverUrl.trim()
        val key = _state.value.apiKey.trim()
        if (url.isBlank() || !url.startsWith("http")) {
            _state.update { it.copy(validation = SettingsValidation.InvalidUrl) }
            return
        }
        _state.update { it.copy(validation = SettingsValidation.Testing) }
        viewModelScope.launch {
            try {
                val api = apiFactory.apiForOnboarding(url, key)
                api.health()
                preferences.saveConnection(url, key)
                syncScheduler.schedulePeriodic()
                syncScheduler.triggerOneShot()
                _state.update { it.copy(validation = SettingsValidation.Success) }
            } catch (e: HttpException) {
                Timber.w(e, "Settings-Healthcheck HTTP-Fehler")
                _state.update {
                    it.copy(
                        validation = if (e.code() == 401 || e.code() == 403)
                            SettingsValidation.Unauthorized else SettingsValidation.Unreachable
                    )
                }
            } catch (e: IOException) {
                Timber.w(e, "Settings-Healthcheck offline")
                _state.update { it.copy(validation = SettingsValidation.Unreachable) }
            } catch (e: Exception) {
                Timber.e(e, "Settings-Healthcheck unbekannter Fehler")
                _state.update { it.copy(validation = SettingsValidation.Unreachable) }
            }
        }
    }

    fun syncNow() {
        syncScheduler.triggerOneShot()
    }

    fun saveFeatureSettings() {
        val state = _state.value
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            preferences.saveWeekplanDays(state.weekplanDays)
            preferences.saveShoppingDay(state.shoppingDay)
            preferences.saveDefaultShoppingListId(state.defaultShoppingListId)
            weekplanSettingsDao.upsert(
                com.helga.android.data.local.entity.WeekplanSettingsEntity(
                    id = "global",
                    planDays = state.weekplanDays,
                    shoppingDay = state.shoppingDay,
                    updatedAt = now,
                    dirty = 1,
                )
            )
            if (state.defaultShoppingListId.isNotBlank()) {
                shoppingRepository.setDefaultList(state.defaultShoppingListId)
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteShoppingList(list: ShoppingListEntity) {
        viewModelScope.launch {
            shoppingRepository.deleteList(list)
            if (_state.value.defaultShoppingListId == list.id) {
                preferences.saveDefaultShoppingListId("")
                _state.update { it.copy(defaultShoppingListId = "") }
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun addQuickEmoji(emoji: String, food: String, quantity: Double, unit: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            quickEmojiDao.upsertEmoji(
                QuickEmojiEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    emoji = emoji,
                    food = food.trim(),
                    quantity = quantity,
                    unit = unit.trim(),
                    sortOrder = quickEmojis.value.size,
                    updatedAt = now,
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun updateQuickEmoji(item: QuickEmojiEntity, emoji: String, food: String, quantity: Double, unit: String) {
        viewModelScope.launch {
            quickEmojiDao.upsertEmoji(
                item.copy(
                    emoji = emoji,
                    food = food.trim(),
                    quantity = quantity,
                    unit = unit.trim(),
                    updatedAt = System.currentTimeMillis(),
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteQuickEmoji(item: QuickEmojiEntity) {
        viewModelScope.launch {
            quickEmojiDao.upsertEmoji(
                item.copy(
                    deleted = 1,
                    updatedAt = System.currentTimeMillis(),
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            syncScheduler.cancelAll()
            preferences.clearConnection()
            onLoggedOut()
        }
    }

    /**
     * Leert alle Room-Tabellen (einstellungen A4) — für den Fall, dass die App "klemmt" und ein
     * sauberer Neustart ohne Deinstallation reichen soll. Server-URL/API-Key bleiben erhalten,
     * damit direkt danach wieder alles vom Server-Backup zurückgeholt werden kann: Sync-Cursor
     * auf 0 zurücksetzen (sonst würde `GET /api/sync?since=<alter Stand>` nichts mehr liefern,
     * die App bliebe leer) und sofort einen Voll-Sync anstoßen.
     */
    fun resetLocalData() {
        viewModelScope.launch {
            syncScheduler.cancelAll()
            withContext(Dispatchers.IO) { database.clearAllTables() }
            preferences.saveLastSyncTs(0L)
            syncScheduler.schedulePeriodic()
            syncScheduler.triggerOneShot()
        }
    }

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    fun exportAllData() {
        viewModelScope.launch {
            try {
                val recipes = recipeRepository.allRecipes()
                val json = JSONObject()
                val recipesArray = JSONArray()
                recipes.forEach { recipe ->
                    val obj = JSONObject().apply {
                        put("id", recipe.id)
                        put("name", recipe.name)
                        put("description", recipe.description)
                        put("recipeYield", recipe.recipeYield)
                        put("prepTime", recipe.prepTime)
                        put("cookTime", recipe.cookTime)
                        put("totalTime", recipe.totalTime)
                        put("sourceUrl", recipe.sourceUrl)
                        put("rating", recipe.rating)
                        put("proteinType", recipe.proteinType)
                        put("effort", recipe.effort)
                        put("cuisine", recipe.cuisine)
                        put("mealSlot", recipe.mealSlot)
                        put("personalNotes", recipe.personalNotes)
                    }
                    val ingredients = recipeRepository.ingredientsForRecipe(recipe.id)
                    val ingArray = JSONArray()
                    ingredients.forEach { ing ->
                        ingArray.put(JSONObject().apply {
                            put("quantity", ing.quantity)
                            put("unit", ing.unit)
                            put("food", ing.food)
                            put("note", ing.note)
                        })
                    }
                    obj.put("ingredients", ingArray)

                    val instructions = recipeRepository.instructionsForRecipe(recipe.id)
                    val stepsArray = JSONArray()
                    instructions.sortedBy { it.position }.forEach { step ->
                        stepsArray.put(step.text)
                    }
                    obj.put("instructions", stepsArray)
                    recipesArray.put(obj)
                }
                json.put("recipes", recipesArray)
                json.put("exportDate", System.currentTimeMillis())
                json.put("recipeCount", recipes.size)
                _exportJson.value = json.toString(2)
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
            }
        }
    }

    fun clearExport() { _exportJson.value = null }

    private val _bulkAiState = MutableStateFlow(BulkAiState())
    val bulkAiState: StateFlow<BulkAiState> = _bulkAiState.asStateFlow()

    fun runBulkAi(mode: BulkAiMode) {
        if (_bulkAiState.value.isRunning) return
        viewModelScope.launch {
            val recipes = recipeRepository.allRecipes()
            _bulkAiState.value = BulkAiState(mode = mode, isRunning = true, total = recipes.size)
            val api = apiFactory.api()
            var updated = 0
            var failed = 0
            recipes.forEachIndexed { index, recipe ->
                try {
                    if (mode == BulkAiMode.CLASSIFY || mode == BulkAiMode.BOTH) {
                        val tags = recipeRepository.tagsByRecipeId(recipe.id).map { it.name }
                        val ingredientNames = recipeRepository.ingredientsForRecipe(recipe.id).map { it.food }
                        val classifyResult = api.classifyRecipe(
                            AiClassifyRequest(
                                name = recipe.name,
                                description = recipe.description,
                                tags = tags,
                                ingredients = ingredientNames,
                            )
                        )
                        recipeRepository.upsertLocal(
                            recipe.copy(
                                proteinType = classifyResult.proteinType.ifBlank { recipe.proteinType },
                                effort = classifyResult.effort.ifBlank { recipe.effort },
                                cuisine = classifyResult.cuisine.ifBlank { recipe.cuisine },
                                mealSlot = classifyResult.mealSlot.ifBlank { recipe.mealSlot },
                                seasonFit = classifyResult.seasonFit.ifBlank { recipe.seasonFit },
                            )
                        )
                    }
                    if (mode == BulkAiMode.NUTRITION || mode == BulkAiMode.BOTH) {
                        val ingredients = recipeRepository.ingredientsForRecipe(recipe.id).filter { it.deleted == 0 }
                        val baseServings = Regex("""\d+""").find(recipe.recipeYield)?.value?.toIntOrNull()
                            ?.takeIf { it > 0 } ?: NUTRITION_BASELINE_PORTIONS
                        val scale = NUTRITION_BASELINE_PORTIONS.toDouble() / baseServings.toDouble()
                        val ingredientLines = ingredients.map { ing ->
                            val scaledQty = ing.quantity * scale
                            val qtyStr = when {
                                scaledQty <= 0.0 -> ""
                                scaledQty % 1.0 < 0.01 -> scaledQty.toInt().toString()
                                else -> String.format(java.util.Locale.US, "%.1f", scaledQty)
                            }
                            listOf(qtyStr, ing.unit, ing.food).filter { it.isNotBlank() }.joinToString(" ")
                        }
                        val nutritionResult = api.estimateNutrition(
                            AiNutritionRequest(
                                name = recipe.name,
                                description = recipe.description,
                                ingredients = ingredientLines,
                                portions = NUTRITION_BASELINE_PORTIONS,
                            )
                        )
                        recipeRepository.saveNutrition(
                            recipeId = recipe.id,
                            kcal = nutritionResult.kcal,
                            protein = nutritionResult.protein,
                            fat = nutritionResult.fat,
                            carbs = nutritionResult.carbs,
                            source = "ai",
                        )
                    }
                    updated++
                } catch (e: Exception) {
                    Timber.w(e, "Bulk-KI fehlgeschlagen für Rezept ${recipe.id}")
                    failed++
                }
                _bulkAiState.update { it.copy(processed = index + 1, updated = updated, failed = failed) }
            }
            syncScheduler.triggerOneShot()
            _bulkAiState.update { it.copy(isRunning = false) }
        }
    }

    fun dismissBulkAiResult() {
        _bulkAiState.value = BulkAiState()
    }
}

enum class BulkAiMode { NUTRITION, CLASSIFY, BOTH }

data class BulkAiState(
    val mode: BulkAiMode? = null,
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val updated: Int = 0,
    val failed: Int = 0,
)

data class SettingsState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val weekplanDays: Int = 7,
    val shoppingDay: Int = 0,
    val defaultShoppingListId: String = "",
    val themeMode: String = "system",
    val accentColor: Int = 0,
    val checkMode: String = "keep",
    val scanReminderThreshold: Float = 0.6f,
    val receiptReconciliationEnabled: Boolean = true,
    val receiptRetentionMonths: Int = 3,
    val notifyShoppingDay: Boolean = false,
    val notifyCookReminder: Boolean = false,
    val loaded: Boolean = false,
    val validation: SettingsValidation = SettingsValidation.Idle,
)

sealed interface SettingsValidation {
    data object Idle : SettingsValidation
    data object Testing : SettingsValidation
    data object Success : SettingsValidation
    data object InvalidUrl : SettingsValidation
    data object Unreachable : SettingsValidation
    data object Unauthorized : SettingsValidation
}
