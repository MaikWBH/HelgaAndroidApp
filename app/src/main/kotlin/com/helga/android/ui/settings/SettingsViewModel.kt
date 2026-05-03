package com.helga.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val lastSyncTs: StateFlow<Long> = preferences.lastSyncTs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        viewModelScope.launch {
            val conn = preferences.connection.first()
            _state.update {
                it.copy(serverUrl = conn.serverUrl, apiKey = conn.apiKey, loaded = true)
            }
        }
    }

    fun setServerUrl(url: String) = _state.update {
        it.copy(serverUrl = url, validation = SettingsValidation.Idle)
    }

    fun setApiKey(key: String) = _state.update {
        it.copy(apiKey = key, validation = SettingsValidation.Idle)
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
