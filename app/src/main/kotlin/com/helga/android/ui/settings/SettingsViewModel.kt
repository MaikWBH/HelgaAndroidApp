package com.helga.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.sync.SyncStatus
import com.helga.android.data.sync.SyncStatusHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val quickEmojiDao: QuickEmojiDao,
    private val weekplanSettingsDao: WeekplanSettingsDao,
    private val syncStatusHolder: SyncStatusHolder,
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
}

data class SettingsState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val weekplanDays: Int = 7,
    val shoppingDay: Int = 0,
    val defaultShoppingListId: String = "",
    val themeMode: String = "system",
    val accentColor: Int = 0,
    val checkMode: String = "keep",
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
