package com.helga.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val apiFactory: SyncApiFactory,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        // Bereits gespeicherte Verbindung vorbelegen. Normalerweise wird dieser Screen bei
        // konfigurierter App gar nicht erst angezeigt — landet man doch hier (z. B. weil der
        // Server gerade nicht erreichbar war), muss niemand den 64-Zeichen-API-Key abtippen.
        viewModelScope.launch {
            val conn = preferences.currentConnection()
            if (conn.isConfigured) {
                _state.update { it.copy(serverUrl = conn.serverUrl, apiKey = conn.apiKey) }
            }
        }
    }

    fun setServerUrl(url: String) = _state.update { it.copy(serverUrl = url, validation = Validation.Idle) }
    fun setApiKey(key: String) = _state.update { it.copy(apiKey = key, validation = Validation.Idle) }

    fun testConnection(onSuccess: () -> Unit) {
        val url = _state.value.serverUrl.trim()
        val key = _state.value.apiKey.trim()
        if (url.isBlank() || !url.startsWith("http")) {
            _state.update { it.copy(validation = Validation.InvalidUrl) }
            return
        }
        _state.update { it.copy(validation = Validation.Testing) }
        viewModelScope.launch {
            try {
                val api = apiFactory.apiForOnboarding(url, key)
                api.health()
                preferences.saveConnection(url, key)
                syncScheduler.schedulePeriodic()
                syncScheduler.triggerOneShot()
                _state.update { it.copy(validation = Validation.Success) }
                onSuccess()
            } catch (e: HttpException) {
                Timber.w(e, "Healthcheck HTTP-Fehler")
                _state.update {
                    it.copy(
                        validation = if (e.code() == 401 || e.code() == 403)
                            Validation.Unauthorized else Validation.Unreachable
                    )
                }
            } catch (e: IOException) {
                Timber.w(e, "Healthcheck offline")
                _state.update { it.copy(validation = Validation.Unreachable) }
            } catch (e: Exception) {
                Timber.e(e, "Healthcheck unbekannter Fehler")
                _state.update { it.copy(validation = Validation.Unreachable) }
            }
        }
    }
}

data class OnboardingState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val validation: Validation = Validation.Idle,
)

sealed interface Validation {
    data object Idle : Validation
    data object Testing : Validation
    data object Success : Validation
    data object InvalidUrl : Validation
    data object Unreachable : Validation
    data object Unauthorized : Validation
}
